/*

This file is part of the Feeeze scheduling analysis tool.

This code is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License, version 3,
as published by the Free Software Foundation.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License, version 3,
along with this program.  If not, see <http://www.gnu.org/licenses/>

*/

/*-----------------------------------------------------------------------
 *
 * Copyright (c) 2025, Tokiwa Software GmbH, Germany
 *
 * Source of feeze_recorder.bpf.c
 *
 * This is the bpf code of the feeze recorder.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 *
 *---------------------------------------------------------------------*/


#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif
#include <unistd.h>
#include <string.h>
#include <stdio.h>
#include <errno.h>
#include <signal.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <pwd.h>
#include <sched.h>
#include <pthread.h>
#include <sys/types.h>
#include <assert.h>
#include <sys/sdt.h>

#include "feeze_recorder_common.h"
#include "feeze_recorder.skel.h"


#define SHARED_MEM_NAME "/tmp/feeze_events_recorder_data"
#define SHARED_MEM_SIZE ((off_t) 64*1024*1024)


/**
 * Flag used to indicate that we should stop, clean up and exit.
 */
static volatile bool exiting = false;

/**
 * Flag used to indicate that we should recording andclean up, but wait for further instructions
 */
static volatile bool finishing = false;


typedef struct shared_buffer shared_buffer;
typedef struct entry entry;

struct  shared_buffer
{
  volatile uint64_t size;
  volatile uint64_t num_entries;
  volatile int      entry_start_offset;
  volatile int      entry_size;
  volatile char     done;
  volatile char     unused2;
  volatile char     unused3;
  volatile char     unused4;
};

#define ENTRY_KIND_UNUSED        0
#define ENTRY_KIND_SCHED_SWITCH  1
#define ENTRY_KIND_SCHED_WAKING  5
#define ENTRY_KIND_SCHED_WAKEUP  6
#define ENTRY_KIND_USER          2
#define ENTRY_KIND_PROCESS       3
#define ENTRY_KIND_THREAD        4
#define ENTRY_KIND_USER_EVENT    7
#define ENTRY_KIND_THREAD_NAME   8
#define ENTRY_KIND_GAP           9  // A gap in the data due to ringbuffer overflow
#define ENTRY_KIND_MORE_CHARS   10  // continuation of a previous char[]


// untimed payloads:

// a user id followed by the user name as more_chars_payload
struct user_payload
{
  uid_t uid;                         //  4  4     -- element and total size in bytes
};

// a process id that belongs to given user id
struct process_payload
{
  pid_t pid;                         //  4  4     -- element and total size in bytes
  uid_t uid;                         //  4  8     -- element and total size in bytes
};

// a thread that belongs to given process
struct thread_payload
{
  pid_t tid;                         //  4  4     -- element and total size in bytes
  pid_t pid;                         //  4  8     -- element and total size in bytes
};

// a thread name, might change repeatedly
struct thread_name_payload
{
  uint16_t t_num;                    //  2  2     -- element and total size in bytes
  char name[6];                      //  6  8     -- element and total size in bytes
};

// additional chars following an event that carries a string
struct more_chars_payload
{
  char str[8];                       //  8  8     -- element and total size in bytes
};

// timed payloads:

// a SCHED_SWITCH, some thread starts or stops running:
//
// note that either the old or the new thread always seems to be the swapper
// with tid==0.
//
// to safe space, we do not use pid_t here, but we number the threads in
// the order of the ENTRY_KIND_THREAD events starting at 0, 1, 2, ... and
// use this as a 16-bit unsigned value
struct sched_switch_payload
{
  uint16_t old_t_num;                //  2  2     -- element and total size in bytes
  uint16_t new_t_num;                //  2  4     -- element and total size in bytes
  uint16_t cpu_id;                   //  2  6     -- element and total size in bytes
};

// a SCHED_WAKING (thread stops being blocked) or SCHED_WAKEUP (after WAKING,
// thread gets added to a CPUs run queue) event
//
struct sched_wakeup_payload
{
  uint16_t causing_t_num;            //  2  2     -- element and total size in bytes
  uint16_t new_t_num;                //  2  4     -- element and total size in bytes
  uint16_t cpu_id;                   //  2  6     -- element and total size in bytes
};

// A user event on thread with given number,  using given color and given message,
// msg, might be continued by following MORE_CHARS event.
//
struct user_event
{
  uint16_t t_num;                    //  2  2     -- element and total size in bytes
  uint8_t col;                       //  1  3     -- element and total size in bytes
  uint8_t pad;                       //  1  4     -- element and total size in bytes
  char msg[4];                       //  4  8     -- element and total size in bytes
};

// A ring buffer overflow when communicating with eBPF. This indicates we
// lost some data since the previous event.
//
struct gap_payload                   //  0  0     -- element and total size in bytes
{
};


// event payload carrying a timestamp
//
struct timed_payload
{
  // tricky overlap with untimed_payload.kind:
  //
  //  - kind in bits 0..3 of ns_and_kind[0]
  //
  //  - ns in bits 4..7 of ns_and_kind[0] and all bits of ns_and_kind[1..7].
  //    stored using little endian order
  //
  uint8_t ns_and_kind[8];            //  8  8     -- element and total size in bytes
  union
  {
    struct sched_switch_payload ss;  //  6 14     -- element and total size in bytes
    struct sched_wakeup_payload sw;  //  6 14     -- element and total size in bytes
    struct user_event           ue;  //  8 16     -- element and total size in bytes
  } payload;                         //  8 16     -- element and total size in bytes
};


// event payload carrying no timestamp
struct untimed_payload
{
  uint8_t kind;                      //  1  1     -- element and total size in bytes
  uint8_t pad1;                      //  1  2     -- element and total size in bytes
  uint16_t pad2;                     //  2  4     -- element and total size in bytes
  uint32_t pad4;                     //  4  8     -- element and total size in bytes
  union
  {
    struct user_payload         u;   //  4 12     -- element and total size in bytes
    struct process_payload      p;   //  8 16     -- element and total size in bytes
    struct thread_payload       t;   //  8 16     -- element and total size in bytes
    struct thread_name_payload  tn;  //  8 16     -- element and total size in bytes
    struct gap_payload          gp;  //  0  8     -- element and total size in bytes
    struct more_chars_payload   mc;  //  8 16     -- element and total size in bytes
  } payload;                         //  8 16
};


// event entry
struct entry
{
  union
  {
    struct timed_payload timed;      // 16 16     -- element and total size in bytes
    struct untimed_payload untimed;  // 16 16     -- element and total size in bytes
  };
};

// sizeof(struct entry):
//
#define ENTRY_SIZE 0x10


volatile uint64_t counter = 0;
shared_buffer *shmem = MAP_FAILED;


uint64_t eventcount = 0;

#define MAX_USER_NAME_LENGTH    256
#define MAX_PROCESS_NAME_LENGTH 256


#define MAX_NUM_THREADS (4096)
#define MAX_THREAD_NAME_LENGTH (32)

/* all the thread ids found so far */
pid_t thread_tids[MAX_NUM_THREADS];

/* all the thread pids corresponding to the thread ids found so far, i.e.,
   tids[i] is a thread of pids[i].  */
pid_t thread_pids[MAX_NUM_THREADS];

char thread_names[MAX_NUM_THREADS*MAX_THREAD_NAME_LENGTH];

/**
 * Number of threads in thread_tids[]/thread_pids[] arrays.
 */
int num_threads = 0;


#define MAX_NUM_PROCESSES (4096)

pid_t process_pids[MAX_NUM_PROCESSES];


/**
 * Number of processes in process_pids[] array.
 */
int num_processes = 0;


#define MAX_NUM_USERS (4096)

uid_t user_uids[MAX_NUM_USERS];


/**
 * Number of users in user_uids[] array.
 */
int num_users = 0;


struct record_config
{
  const char* lib_fuzion;
  size_t shmem_size;
  const char* shmem_file_name;
  char *shared_lib_name;
};

struct record_config *config = NULL;


/**
 * Callback installed using libbpf_set_print() used for debug output sent to
 * bpf_printk().
 */
static int libbpf_print_fn(enum libbpf_print_level level,
                           const char *format,
                           va_list args)
{
  return vfprintf(stderr, format, args);
}


/**
 * Signal handler used to set `exiting` in case of ^C and ^\.
 */
static void sig_handler(int sig)
{
  exiting = true;
  finishing = true;
}


/**
 * Push the given entry to the shared memory buffer
 */
void post_entry(struct entry *e)
{
  uint64_t ec = eventcount;
  entry* entries = (entry*) &(shmem[1]);
  if ((void*) &entries[ec+1] > (void*) &((char*)shmem)[SHARED_MEM_SIZE])
    {
      printf("shared mem buffer full\n");
      finishing = true;
    }
  else
    {
      entries[ec] = *e;
      __sync_synchronize();
      shmem->num_entries = ec+1;
      __sync_synchronize();
      eventcount = ec+1;
      uint64_t n = eventcount; //  & ~((uint64_t) 0x3f);
    }
}


/**
 * Find thread tid in thread_tids[] array at indices 0..num_threads-1.
 *
 * @return the index of tid in thread_tids[] or -1 if not found.
 */
int thread_index(pid_t tid)
{
  int i =  0;

  while (i < num_threads && thread_tids[i] != tid)
    {
      i++;
    }
  return i < num_threads ? i : -1;
}


/**
 * Find process pid in process_pids[] array at indices 0..num_processes-1.
 *
 * @return the index of pid in process_pids[] or -1 if not found.
 */
int process_index(pid_t pid)
{
  int i =  0;

  while (i < num_processes && process_pids[i] != pid)
    {
      i++;
    }
  return i < num_processes ? i : -1;
}


/**
 * Find user uid in user_uids[] array at indices 0..num_users-1.
 *
 * @return the index of pid in process_pids[] or -1 if not found.
 */
int user_index(uid_t uid)
{
  int i =  0;

  while (i < num_users && user_uids[i] != uid)
    {
      i++;
    }
  return i < num_users ? i : -1;
}


/**
 * Get parent process id for thread tid using /proc/<tid>/stat.
 *
 * @return the corresponding pid or -1 if not found
 */
pid_t get_parent_pid(pid_t tid)
{
  pid_t pid = -1;
  char path[256];
  FILE *stat_file;

  snprintf(path, sizeof(path), "/proc/%d/stat", tid);
  stat_file = fopen(path, "r");
  if (stat_file)
    {
      unsigned int ppid;
      // See `https://github.com/torvalds/linux/blob/master/Documentation/filesystems/proc.rst`  for details or `man 5 proc` for short info:
      fscanf(stat_file, "%*d (%*[^)]) %*c %*d %d", &pid);
      fclose(stat_file);
    }
  return pid;
}


/**
 * Get thread group (process) id for thread tid using /proc/<tid>/stat.
 *
 * @return the corresponding pid or -1 if not found
 */
pid_t get_tgid(pid_t tid)
{
  pid_t tgid = -1;
  char path[256];
  snprintf(path, sizeof(path), "/proc/%d/status", tid);

  FILE *fp = fopen(path, "r");
  if (fp)
    {
      char line[256];
      bool done = false;
      while (!done && fgets(line, sizeof(line), fp))
        {
          done = sscanf(line, "Tgid:\t%d", &tgid) == 1;
        }
      fclose(fp);
    }

  // verify that tgid of tgid is still tgid:
  assert(tid == tgid || tgid == get_tgid(tgid));

  return tgid;
}


/**
 * Get name of given process from "/proc/%d/state"
 *
 * @param pid the process id
 *
 * @param buffer buffer to place the name
 *
 * @param n number of chars available in buffer
 *
 * @return buffer or NULL in case of an error.
 */
char *get_process_name(pid_t pid, char *buffer, int n)
{
  char *result = NULL;
  char path[256];
  FILE *fp;
  char name[256];  // NYI: Make this an argument!

  snprintf(path, sizeof(path), "/proc/%d/stat", pid);
  fp = fopen(path, "r");
  if (fp == NULL)
    {
      snprintf(buffer, n, "process %d (died)", pid);
    }
  else
    {
      if (fscanf(fp, "%*d %255s", name) != 1 || strlen(name) < 2)
        {
          snprintf(buffer, n, "*** failed to scan file '%s'", path);
        }
      else
        {
          int l = strlen(name);
          // Remove '('/')'
          name[l - 1] = '\0';
          strncpy(buffer, name+1, n);
          result = buffer;
        }
      fclose(fp);
    }
  return result;
}


/**
 * For a given process, determine the real user id of the owner of this process.
 */
int get_process_uid(pid_t pid)
{
  char path[256];
  FILE *fp;
  int uid = -1;

  snprintf(path, sizeof(path), "/proc/%d/status", pid);
  fp = fopen(path, "r");
  if (fp != NULL)
    {
      char line[256];
      while (fgets(line, sizeof(line), fp) &&
             sscanf(line, "Uid: %d", &uid) != 1)
        {
        }
      fclose(fp);
    }
  return uid;
}


/**
 * Get name of given user via getpwuid.
 *
 * @param uid the user id
 *
 * @param buffer buffer to place the name
 *
 * @param n number of chars available in buffer
 *
 * @return buffer or NULL in case of an error.
 */
char *get_user_name(uid_t uid, char *buffer, int n)
{
  char *result = NULL;

  struct passwd *pw = getpwuid(uid);
  if (pw == NULL)
    {
      snprintf(buffer, n, "unknown user %d", uid);
    }
  else
    {
      strncpy(buffer, pw->pw_name, n);
      result = buffer;
    }
  return result;
}


/**
 * Add event of type ENTRY_KIND_MORE_CHARS to add additional chars to a
 * string that is part of the previous event.
 *
 * @param more_chars the additional chars, must be '\0'-terminated. May be NULL
 * to do nothing.
 */
void post_more_chars(const char *more_chars)
{
  struct entry en;
  while (more_chars != NULL)
    {
      memset(&en, 0, sizeof(en));
      en.untimed.kind = ENTRY_KIND_MORE_CHARS;
      int l = strlen(more_chars);
      int lmax = sizeof(en.untimed.payload.mc.str);
      int l1 = l > lmax ? lmax : l;
      memcpy(en.untimed.payload.mc.str, more_chars, l1);
      post_entry(&en);
      more_chars = l == l1 ? NULL : &more_chars[l1];
    }
}



/**
 * Check if user uid was already encountered. If not, create and post an
 * entry of ENTRY_KIND_USER for this user.
 */
void add_user(uid_t uid)
{
  if (user_index(uid) < 0 && num_users < MAX_NUM_USERS)
    {
      user_uids[num_users] = uid;
      num_users++;

      struct entry en;
      en.untimed.kind = ENTRY_KIND_USER;
      en.untimed.payload.u.uid = uid;
      post_entry(&en);

      char name[MAX_USER_NAME_LENGTH+1];
      memset(&name, 0, sizeof(name));
      get_user_name(uid, &name[0], sizeof(name));
      post_more_chars(&name[0]);
    }
}


/**
 * Check if process pid was already encountered. If not, create and post an
 * entry of ENTRY_KIND_PROCESS for this process.
 */
void add_process(pid_t pid)
{
  if (process_index(pid) < 0 && num_processes < MAX_NUM_PROCESSES)
    {
      uid_t uid = get_process_uid(pid);
      add_user(uid);

      process_pids[num_processes] = pid;
      num_processes++;
      struct entry en;
      en.untimed.kind = ENTRY_KIND_PROCESS;
      en.untimed.payload.p.pid = pid;
      en.untimed.payload.p.uid = uid;
      post_entry(&en);

      char name[MAX_USER_NAME_LENGTH+1];
      memset(&name, 0, sizeof(name));
      get_process_name(pid, &name[0], sizeof(name));
      post_more_chars(&name[0]);
    }
}


void add_thread_name(int num,
                     char tname[16])
{
  struct entry en;
  memset(&en, 0, sizeof(en));
  en.untimed.kind = ENTRY_KIND_THREAD_NAME;
  en.untimed.payload.tn.t_num = (uint16_t) num;

  char name[MAX_THREAD_NAME_LENGTH+1];
  memset(&name, 0, sizeof(name));
  strncpy(&name[0], &tname[0], MAX_THREAD_NAME_LENGTH);

  memset( &thread_names[num*MAX_THREAD_NAME_LENGTH], 0,    MAX_THREAD_NAME_LENGTH);
  strncpy(&thread_names[num*MAX_THREAD_NAME_LENGTH], name, MAX_THREAD_NAME_LENGTH);

  strncpy(en.untimed.payload.tn.name, name, sizeof(en.untimed.payload.tn.name));

  post_entry(&en);

  if (strlen(name) > sizeof(en.untimed.payload.tn.name))
    {
      post_more_chars(&name[sizeof(en.untimed.payload.tn.name)]);
    }
}


/**
 * Check if thread tid was already encountered. If not, create and post an
 * entry of ENTRY_KIND_THREAD for this process.
 */
int add_thread(pid_t tid, char name[16])
{
  int num = thread_index(tid);
  if (num < 0 && num_threads < MAX_NUM_THREADS)
    {
      pid_t pid = get_tgid(tid);
      add_process(pid);

      num = num_threads;
      thread_tids[num_threads] = tid;
      thread_pids[num_threads] = pid;
      num_threads++;
      struct entry en;
      en.untimed.kind = ENTRY_KIND_THREAD;
      en.untimed.payload.t.tid = tid;
      en.untimed.payload.t.pid = pid;
      post_entry(&en);
    }
  if (num >= 0 && name[0]!=0 && strncmp(name, &thread_names[num*MAX_THREAD_NAME_LENGTH], sizeof(*name))!=0)
    {
      add_thread_name(num, name);
    }
  return num;
}


/**
 * handle event arriving in the ring buffer form BPF code
 */
int handle_event(void *ctx, void *data, size_t data_sz)
{
  if (!finishing && data != NULL && data_sz == sizeof(struct event))
    {
      char str[2*16+1];  // NYI: Support for user events with longer strings.
      const char*more_chars = NULL;
      const struct event *e = data;
      uint64_t ec = eventcount;
      entry* entries = (entry*) &(shmem[1]);
      if ((void*) &entries[ec+1] > (void*) &((char*)shmem)[SHARED_MEM_SIZE])
        {
          printf("shared mem buffer full\n"); // fflush(stdout);
          finishing = true;
        }
      else if (e->event_kind == RB_EVENT_GAP          ||
               e->event_kind == RB_EVENT_SCHED_SWITCH ||
               e->event_kind == RB_EVENT_SCHED_WAKEUP ||
               e->event_kind == RB_EVENT_SCHED_WAKING ||
               e->event_kind == RB_EVENT_FUZION_USER     )
        {
          struct entry en;
          int kind = -1;
          if (e->event_kind == RB_EVENT_GAP)
            {
              kind = ENTRY_KIND_GAP;
            }
          else if (e->event_kind == RB_EVENT_SCHED_SWITCH)
            {
              kind = ENTRY_KIND_SCHED_SWITCH;
              en.timed.payload.ss.old_t_num = add_thread(e->old_pid, (char*) &e->old_name);
              en.timed.payload.ss.new_t_num = add_thread(e->new_pid, (char*) &e->comm    );
              en.timed.payload.ss.cpu_id    = e->cpu_id;
            }
          else if (e->event_kind == RB_EVENT_SCHED_WAKEUP ||
                   e->event_kind == RB_EVENT_SCHED_WAKING    )
            {
              kind = e->event_kind == RB_EVENT_SCHED_WAKEUP ? ENTRY_KIND_SCHED_WAKEUP :
                     e->event_kind == RB_EVENT_SCHED_WAKING ? ENTRY_KIND_SCHED_WAKING : -1;
              en.timed.payload.sw.causing_t_num = add_thread(e->old_pid, (char*) &e->old_name);
              en.timed.payload.sw.new_t_num     = add_thread(e->new_pid, (char*) &e->comm    );
              en.timed.payload.sw.cpu_id        = e->cpu_id;
            }
          else if (e->event_kind == RB_EVENT_FUZION_USER)
            {
              memcpy(&(str[0*16]), &e->comm    [0], 16);
              memcpy(&(str[1*16]), &e->old_name[0], 16);
              str[2*16] = 0;
              add_thread(e->new_pid, "unknown");
              kind = ENTRY_KIND_USER_EVENT;
              int num = thread_index(e->new_pid);
              if (num >= 0 && num <= 0xffff)
                {
                  struct entry *n = NULL;
                  en.timed.payload.ue.t_num = (uint16_t) num;
                  en.timed.payload.ue.col = (uint8_t) e->new_pri;
                  memcpy(&en.timed.payload.ue.msg, &str, sizeof(en.timed.payload.ue.msg));
                  if (strlen(str) > sizeof(en.timed.payload.ue.msg))
                    {
                      more_chars = &str[sizeof(en.timed.payload.ue.msg)];
                    }
                }
            }
          if (kind != -1)
            {
              uint64_t nk = (e->ns << 4) | kind;
              en.timed.ns_and_kind[0] = (uint8_t) ( nk        & 0xff);
              en.timed.ns_and_kind[1] = (uint8_t) ((nk >>  8) & 0xff);
              en.timed.ns_and_kind[2] = (uint8_t) ((nk >> 16) & 0xff);
              en.timed.ns_and_kind[3] = (uint8_t) ((nk >> 24) & 0xff);
              en.timed.ns_and_kind[4] = (uint8_t) ((nk >> 32) & 0xff);
              en.timed.ns_and_kind[5] = (uint8_t) ((nk >> 40) & 0xff);
              en.timed.ns_and_kind[6] = (uint8_t) ((nk >> 48) & 0xff);
              en.timed.ns_and_kind[7] = (uint8_t) ((nk >> 54) & 0xff);
              post_entry(&en);
              post_more_chars(more_chars);
            }
        }
    }
  return 0;
}


/**
 * Mutex and conditional used by threads running t12start to play ping-pong:
 */
pthread_mutex_t mutex;
pthread_cond_t  cond;


/**
 * Busy threads just to see how fast mutex and cond could work:
 */
void *t12start(void *arg)
{
  int thrid = arg==NULL ? 0 : 1;
  int threadId = pthread_self();
  while (!finishing)
    {
      pthread_mutex_lock(&mutex);
      uint64_t c = ++counter;
      uint64_t n = c & -2;
      if ((n & 65535) == 0) // after 131k thread switches, sleep for 20ms to give tbe system some time to breathe...
        {
          struct timespec duration = { 0, 200000000 }; // 200ms
          nanosleep(&duration, 0);
        }
      pthread_cond_signal(&cond);
      while (counter == c)
        {
          pthread_cond_wait(&cond, &mutex);
        }
      pthread_mutex_unlock(&mutex);
    }
  return NULL;
}

struct s
{
  union
  {
    char str[16];
    uint64_t l[2];
  } u;
};

void *thread_dtrace_test(void *arg)
{
  int thrid = arg==NULL ? 0 : 1;
  int threadId = pthread_self();
  while (!finishing)
    {
      struct timespec duration = { 3, 200000000 }; // 3.200ms
      nanosleep(&duration, 0);
      DTRACE_PROBE(a,b);
      struct s u;
      strcpy(u.u.str, "drei-san-three");
      DTRACE_PROBE4(a,d,42,"2.0",u.u.l[0],u.u.l[1]);
      DTRACE_PROBE3(a,c,42,"2.0","u.u.l[0],u.u.l[1]");
    }
  return NULL;
}


/**
 * main function
 */
void *record(void *arg)
{
  config = arg;
  const char *shmem_file_name = config->shmem_file_name;
  fprintf(stderr, "--%s--\n",shmem_file_name);
  int entry_start_offset = (char*) &(shmem[1]) -
                           (char*) &(shmem[0]);
  int entry_size = (char *) &(((entry*) &(shmem[1]))[1]) -
                   (char *) &(((entry*) &(shmem[1]))[0]);

  if (entry_size != ENTRY_SIZE)
    {
      fprintf(stderr, "sizeof(entry) should be %d, but is %lu\n",ENTRY_SIZE, sizeof(entry));
      exit(1);
    }
  entry* en = NULL;

  counter = 0;
  eventcount = 0;
  num_threads = 0;
  num_processes = 0;
  num_users = 0;
  finishing = false;

  struct ring_buffer *rb = NULL;
  int err;
  //  int shared = shm_open(SHARED_MEM_NAME, O_RDWR | O_CREAT | O_EXCL, 0644);
  int shared = open(shmem_file_name,
                    0
                    | O_RDWR
                    | O_CREAT
                    /* | O_EXCL  -- cause error in case file exists */,
                    0644);
  if (shared < 0)
    {
      fprintf(stderr,"shm_open(%s) failed: %d %s\n",SHARED_MEM_NAME, errno, strerror(errno));
      err = 1;
      goto cleanup;
    }
  err = ftruncate(shared, config->shmem_size);
  if (err)
    {
      fprintf(stderr,"ftruncate(%d,%ld) failed: %d %s\n",shared, config->shmem_size, errno, strerror(errno));
      goto cleanup;
    }

  shmem = mmap(NULL, config->shmem_size, PROT_READ|PROT_WRITE, MAP_SHARED_VALIDATE, shared, 0);
  if (shmem == MAP_FAILED)
    {
      fprintf(stderr,"mmap(NULL, %ld, %x, %x, %d, 0) failed: %d %s\n",config->shmem_size,
              PROT_READ|PROT_WRITE, MAP_SHARED_VALIDATE, shared,
              errno, strerror(errno));
      err = 1;
      goto cleanup;
    }
  shmem->num_entries = 0;
  shmem->entry_start_offset = entry_start_offset;
  shmem->entry_size = entry_size;
  shmem->done = (char) 0;
  // atomic_thread_fence(std::memory_order_release);
  __sync_synchronize();
  shmem->size = config->shmem_size;

  /* Clean handling of Ctrl-C */
  signal(SIGINT, sig_handler);
  signal(SIGTERM, sig_handler);

  struct feeze_recorder_bpf *skel;

  /* Set up libbpf errors and debug info callback */
  if (false)
    {
      libbpf_set_print(libbpf_print_fn);
    }

  /* Open BPF application */
  skel = feeze_recorder_bpf__open();
  if (!skel)
    {
      fprintf(stderr, "Failed to open BPF skeleton\n");
      err = 1;
      goto cleanup;
    }

  /* Load & verify BPF programs */
  err = feeze_recorder_bpf__load(skel);
  struct bpf_object_open_opts opts = {};
  opts.sz = sizeof(opts);
  char log[65536];
  opts.kernel_log_buf = log;
  opts.kernel_log_size = sizeof(log);
  if (err)
    {
      fprintf(stderr, "Failed to load and verify BPF skeleton\n");
      fprintf(stderr, "--------------------\n%s\n------------------\n",log);
      goto cleanup;
    }

  /* Attach tracepoint handler */
  err = feeze_recorder_bpf__attach(skel);
  if (err)
    {
      fprintf(stderr, "Failed to attach BPF skeleton\n");
      fprintf(stderr, "--------------------\n%s\n------------------\n",log);
      goto cleanup;
    }

  /* Set up ring buffer polling */
  rb = ring_buffer__new(bpf_map__fd(skel->maps.rb), handle_event, NULL, NULL);
  if (!rb)
    {
      err = 1;
      fprintf(stderr, "Failed to create ring buffer\n");
      goto cleanup;
    }

  if (pthread_cond_init(&cond, NULL) != 0)
    {
      pthread_cond_destroy(&cond);
      fprintf(stderr, "pthread_cond_init failed\n");
      err = 1;
      goto cleanup;
    }

  if (false)
    {
      // start very busy threads to test the events recording at high frequency:
      pthread_mutex_init(&mutex, NULL);

      pthread_t t1 = {};
      pthread_t t2 = {};
      pthread_create(&t1, NULL, t12start, NULL);  pthread_setname_np(t1,"AAAAA");
      pthread_create(&t2, NULL, t12start, "1" );  pthread_setname_np(t2,"BBBBB");
    }

  if (false)
    {
      pthread_t t1 = {};
      pthread_create(&t1, NULL, thread_dtrace_test, NULL);  pthread_setname_np(t1,"ABCDEF");
    }

  if (config->lib_fuzion != NULL)
    {
      skel->links.handle_fuzion_probe = bpf_program__attach_usdt(skel->progs.handle_fuzion_probe, -1 /* env.pid */,
                                                                 config->lib_fuzion, "fuzion", "probe", NULL);
    }

  while (!finishing)
    {
      err = ring_buffer__poll(rb, 0 /* timeout, ms */);

      if (err < 0)
        {
          fprintf(stderr, "Error polling ring buffer: %s (%d)\n", strerror(err), err);
          finishing = true;
        }
      else
        {
          uint64_t ms_poll_delay = 10LL; // NYI: Make this configurable from Feeze GUI
          uint64_t nanos = ms_poll_delay * 1000000LL;
          uint64_t s  = nanos /   1000000000LL;
          uint64_t ns = nanos - s*1000000000LL;
          struct timespec req = (struct timespec){s, ns};
          while (nanosleep(&req, &req));
        }
    }
  if (err >= 0)
    {
      err = ring_buffer__poll(rb, 1 /* timeout, ms */);
    }

 cleanup:

  if (rb != NULL)
    {
      ring_buffer__free(rb);
    }
  if (skel != NULL)
    {
      feeze_recorder_bpf__destroy(skel);
    }
  if (shmem != MAP_FAILED)
    {
      shmem->done = (char) 1;
      __sync_synchronize();
    }
  if (shmem != MAP_FAILED && !false)
    {
      int res = munmap(shmem, config->shmem_size);
      if (res < 0)
        {
          fprintf(stderr,"munmap(%p,%ld) failed: %d %s\n",shmem, config->shmem_size, errno, strerror(errno));
          err = 1;
        }
    }
  if (shared > 0)
    {
      // int res = shm_unlink(SHARED_MEM_NAME);
      int res = close(shared);
      if (res < 0)
        {
          fprintf(stderr,"close(shared) failed: %d %s\n",errno, strerror(errno));
          err = 1;
         }
    }
  fprintf(stdout, "DONE RECORDING TO '%s'\n", shmem_file_name); fflush(stdout);

  if (config != NULL)
    {
      free(config);
    }
  // NYI: store err somewhere?
  return NULL;
}


/**
 * Does strinc `s` start with string `p`?
 */
bool str_startsWith(char *s, char *p)
{
  return strncmp(s, p, strlen(p)) == 0;
}


/**
 * Does strinc `s` end with string `p`?
 */
bool str_endsWith(char *s, char *p)
{
  size_t sl = strlen(s);
  size_t pl = strlen(p);
  return
    sl >= pl &&
    strcmp(s+sl-pl, p) == 0;
}



#define N 4096
char name[N];


/**
 * If l<max_l, copy l chars from src to dst and add a terminating `0`.
 *
 * @return l<max_l
 */
bool copy_string_if_it_fits(char *dst, char *src, int l, int max_l)
{
  if (l<max_l)
    {
      strncpy(dst, src, l);
      dst[l] = 0;
      return true;
    }
  else
    {
      return false;
    }
}

/**
 * Check if `s` has the form `"<desired> '<value>'\n"`. If so, copy the
 * `<value>` part to `dst`.
 *
 * @return true iff `s` has the desired form and the `<value>` part fits into
 * `max_l` including the terminating 0.
 *
 */
bool get_option(char *s, char *desired, char* dst, int max_l)
{
  int dl = strlen(desired);
  if (str_startsWith(s, desired) &&
      str_startsWith(s + dl, " '") &&
      str_endsWith(s, "'\n") &&
      copy_string_if_it_fits(dst, s+dl+2, strlen(s)-dl-2-2, max_l))
    {
      return true;
    }
  else
    {
      return false;
    }
}

#define LIB_FUZION "/lib/libfuzion_rt.so"

/**
 * main function
 */
int main(int argc, char**args)
{
  int returnCode = 0;
  char line[N];
  setbuf(stdout, NULL);
  printf("feeze recorder started, waiting for commands...\n"); fflush(stdout);
  size_t shmem_size = SHARED_MEM_SIZE;
  char *lib_fuzion = NULL;
  while (!exiting)
    {
      printf("Waiting for commands...\n"); fflush(stdout);
      char *s = fgets(line, N, stdin);
      // fprintf(stdout, "GOT INPUT '%s'\n", s==NULL?"null":s); // fflush(stdout);
      if (s == NULL)
        {
          exiting = true;
        }
      else if (strcmp(s, "START\n") == 0)
        {
          fprintf(stdout, "START RECORDING"); // fflush(stdout);
          pthread_t rt = {};
          pthread_create(&rt, NULL, record, name);  pthread_setname_np(rt,"feeze_record");
        }
      else if (get_option(s, "SHMEM_SIZE", name, N))
        {
          size_t l = atol(name);
          if ((l & 4095) != 0)  // page aligned
            {
              fprintf(stderr, "*** not 4K page ligned value %zu for SHMEM_SIZE set bs %s\n", l, s);
            }
          else if (l < 4096) // not too small
            {
              fprintf(stderr, "*** too small (<4K) value %zu for SHMEM_SIZE set bs %s\n", l, s);
            }
          else if (l >= ((size_t) 1) << 31)
            {
              fprintf(stderr, "*** ridiculously large value %zu for SHMEM_SIZE set bs %s\n", l, s);
            }
          else
            {
              shmem_size = l;
            }
        }
      else if (get_option(s, "FUZION_HOME", name, N))
        {
          int l1 = strlen(name);
          int l2 = strlen(LIB_FUZION);
          int l = l1 + l2 + 1;
          lib_fuzion = malloc(l);
          if (lib_fuzion == NULL)
            {
              fprintf(stderr, "*** alloc failed for %d bytes\n", l);
            }
          else if (copy_string_if_it_fits( lib_fuzion,     name,       l1, l   ) &&
                   copy_string_if_it_fits(&lib_fuzion[l1], LIB_FUZION, l2, l-l1)    )
            {
              // ok
            }
          else
            {
              assert(false);  // should not happen if calculation for `l` is correct
              lib_fuzion = NULL;
            }
        }
      else if (str_startsWith(s, "START '") &&
               str_endsWith  (s, "'\n"    )    )
        {
          strncpy(name, s+7, strlen(s)-7-2);
          name[strlen(s)-7-2] = 0;
          fprintf(stdout, "START RECORDING TO '%s'\n", name); fflush(stdout);
          pthread_t rt = {};
          struct record_config * conf = malloc(sizeof(struct record_config));
          if (conf == NULL)
            {
              fprintf(stderr, "*** out of memory\n");
            }
          else
            {
              conf->lib_fuzion = lib_fuzion;
              conf->shmem_file_name = name;
              conf->shmem_size = shmem_size;
              pthread_create(&rt, NULL, record, conf);
              pthread_setname_np(rt,"feeze_record");
            }
        }
      else if (strcmp(s, "STOP\n") == 0)
        {
          finishing = true;
        }
      else if (strcmp(s, "EXIT\n") == 0 || strcmp(s, "QUIT\n") == 0)
        {
          fprintf(stdout, "*** exiting due to command %s!\n", s); // fflush(stdout);
          exiting = true;
        }
      else
        {
          fprintf(stderr, "*** unknown command '%s'!\n", s);
        }
    }
  return returnCode;
}
