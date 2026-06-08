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
 * Source of feeze_record.c
 *
 * This is the C code of the main record function of the feeze recorder.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 *
 *---------------------------------------------------------------------*/


#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif
#include <stddef.h>
#include <stdbool.h>
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

/**
 * Flag used to indicate that we should stop recording and clean up.
 */
static volatile bool finishing = false;


typedef struct shared_buffer shared_buffer;
typedef struct entry entry;


/**
 * Format of ths shared memory buffer
 */
struct  shared_buffer
{
  volatile uint64_t size;                 // size of the shared memory in bytes
  volatile uint64_t num_entries;          // number of entries written to this buffer
  volatile int      entry_start_offset;   // byte offset of the first entry, relative to shared memory start
  volatile int      entry_size;           // entry size in bytes
  volatile char     done;                 // 0 while recording, 1 when recording is done
  volatile char     unused2;
  volatile char     unused3;
  volatile char     unused4;
};

// entry kinds
#define ENTRY_KIND_UNUSED        0    // unused kind, should not occur
#define ENTRY_KIND_SCHED_SWITCH  1    // SCHED_SWITCH trace point
#define ENTRY_KIND_SCHED_WAKING  5    // SCHED_WAKING trace point
#define ENTRY_KIND_SCHED_WAKEUP  6    // SCHED_WAKEUP trace point
#define ENTRY_KIND_USER          2    // user DTRACE_PROBE of kind "fuzion"/"probe"
#define ENTRY_KIND_PROCESS       3    // a process that is used in the trace
#define ENTRY_KIND_THREAD        4    // a thread  that is used in the trace
#define ENTRY_KIND_USER_EVENT    7    // a user    that is used in the trace
#define ENTRY_KIND_THREAD_NAME   8    // set name of a thread
#define ENTRY_KIND_GAP           9    // A gap in the data due to ringbuffer overflow
#define ENTRY_KIND_MORE_CHARS   10    // continuation of a previous char[] for strings exceeding single entries


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


// the sared memory buffer
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



uint64_t shmem_size = 0;

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
 * Push the given entry to the shared memory buffer
 */
void post_entry(struct entry *e)
{
  uint64_t ec = eventcount;
  entry* entries = (entry*) &(shmem[1]);
  if ((void*) &entries[ec+1] > (void*) &((char*)shmem)[shmem_size])
    {
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
      if ((void*) &entries[ec+1] > (void*) &((char*)shmem)[shmem_size])
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
              add_thread(e->new_pid, "");
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
 * main function of the feeze recorder.
 *
 * This will poll from the eBPF ring buffer and shuffle data to the shared
 * memory. It will not return before either the shared memory is exhausted or
 * `feeze_finish_record` was called.
 *
 * @param lib_fuzion the Fuzion library, used to attach to user space dynamic
 * traces of type `fuzion`/`probe`.
 *
 * @param shmem_size size of shared memory to allocate for trace data
 *
 * @param shmem_file_name shared memory file name.
 */
void feeze_record(const char* lib_fuzion,
                  size_t shmem_size0,
                  const char* shmem_file_name)
{
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

  eventcount = 0;
  num_threads = 0;
  num_processes = 0;
  num_users = 0;
  finishing = false;
  shmem_size = shmem_size0;

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
      fprintf(stderr,"shm_open(%s) failed: %d %s\n",shmem_file_name, errno, strerror(errno));
      err = 1;
      goto cleanup;
    }
  err = ftruncate(shared, shmem_size);
  if (err)
    {
      fprintf(stderr,"ftruncate(%d,%ld) failed: %d %s\n",shared, shmem_size, errno, strerror(errno));
      goto cleanup;
    }

  shmem = mmap(NULL, shmem_size, PROT_READ|PROT_WRITE, MAP_SHARED_VALIDATE, shared, 0);
  if (shmem == MAP_FAILED)
    {
      fprintf(stderr,"mmap(NULL, %ld, %x, %x, %d, 0) failed: %d %s\n",shmem_size,
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
  shmem->size = shmem_size;

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
  rb = ring_buffer__new(bpf_map__fd(skel->maps.feeze_rec_rb), handle_event, NULL, NULL);
  if (!rb)
    {
      err = 1;
      fprintf(stderr, "Failed to create ring buffer\n");
      goto cleanup;
    }

  if (lib_fuzion != NULL)
    {
      skel->links.handle_fuzion_probe = bpf_program__attach_usdt(skel->progs.handle_fuzion_probe, -1 /* env.pid */,
                                                                 lib_fuzion, "fuzion", "probe", NULL);
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
      int res = munmap(shmem, shmem_size);
      if (res < 0)
        {
          fprintf(stderr,"munmap(%p,%ld) failed: %d %s\n",shmem, shmem_size, errno, strerror(errno));
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

  // NYI: store err somewhere?
}


/**
 * Stop a running call to `feeze_record`.
 */
void feeze_finish_record()
{
  finishing = true;
}
