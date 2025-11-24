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
#include <sched.h>
#include <pthread.h>
#include <sys/types.h>

#include "feeze_recorder_common.h"
#include "feeze_recorder.skel.h"


#define SHARED_MEM_NAME "/tmp/feeze_events_recorder_data"
#define SHARED_MEM_SIZE ((off_t) 64*1024*1024)


/**
 * Flag used to indicate that we should stop, clean up and exit.
 */
static volatile bool exiting = false;


typedef struct shared_buffer shared_buffer;
typedef struct entry entry;

struct  shared_buffer
{
  volatile uint64_t size;
  volatile uint64_t num_entries;
  volatile int      entry_start_offset;
  volatile int      entry_size;
  volatile bool     done;
  volatile bool     blabla;
  volatile bool     blublu;
};

#define ENTRY_KIND_UNUSED 0
#define ENTRY_KIND_SCHED_SWITCH 1
#define ENTRY_KIND_PROCESS      2
#define ENTRY_KIND_THREAD       3

struct process_payload
{
  pid_t pid;
  char name[32];
};
struct thread_payload
{
  pid_t tid;
  pid_t pid;
  char name[32];
};
struct sched_switch_payload
{
  pid_t old_tid;
  int old_pri;
  char	old_name[16 /* TASK_COMM_LEN */];
  pid_t new_tid;
  int new_pri;
  char	new_name[16 /* TASK_COMM_LEN */];
  uint64_t ns;
};

struct entry
{
  uint8_t kind;
  uint8_t pad1;
  uint16_t pad2;
  uint32_t pad4;
  union
  {
    struct process_payload      p;
    struct thread_payload       t;
    struct sched_switch_payload ss;
  } payload;
};

#define ENTRY_SIZE 0x40


volatile uint64_t counter = 0;
shared_buffer *shmem = MAP_FAILED;


uint64_t eventcount = 0;

#define MAX_NUM_THREADS (4096)

/* all the thread ids found so far */
pid_t thread_tids[MAX_NUM_THREADS];

/* all the thread pids corresponding to the thread ids found so far, i.e.,
   tids[i] is a thread of pids[i].  */
pid_t thread_pids[MAX_NUM_THREADS];

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
}



/**
 * Push the given entry to the shared memory buffer
 */
void post_entry(struct entry *e)
{
  if (!exiting)
    {
      uint64_t ec = eventcount;
      entry* entries = (entry*) &(shmem[1]);
      if ((void*) &entries[ec+1] > (void*) &((char*)shmem)[SHARED_MEM_SIZE])
        {
          printf("shared mem buffer full\n");
          exiting = true;
        }
      else
        {
          entries[ec] = *e;
          __sync_synchronize();
          shmem->num_entries = ec+1;
          eventcount = ec+1;
          uint64_t n = eventcount; //  & ~((uint64_t) 0x3f);
          if ((n & (n-1))==0)
            {
              printf("thread switch %lu: %d/%d (%s) -> %d/%d (%s) at %luns\n",
                     eventcount,
                     e->payload.ss.old_tid, e->payload.ss.old_pri, e->payload.ss.old_name,
                     e->payload.ss.new_tid, e->payload.ss.new_pri, e->payload.ss.new_name,
                     e->payload.ss.ns);
            }
        }
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
 * NYI: CLEANUP: unused, remove?
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
  return tgid;
}


/**
 * Get name of given process fro "/proc/%d/state"
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
 * Check if process pid was already encountered. If not, create and post an
 * entry of ENTRY_KIND_PROCESS for this process.
 */
void add_process(pid_t pid)
{
  if (process_index(pid) < 0 && num_processes < MAX_NUM_PROCESSES)
    {
      process_pids[num_processes] = pid;
      num_processes++;
      struct entry en;
      en.kind = ENTRY_KIND_PROCESS;
      en.payload.p.pid = pid;
      get_process_name(pid, (char*)&en.payload.p.name, sizeof(en.payload.p.name));
      post_entry(&en);
    }
}


/**
 * Check if thread tid was already encountered. If not, create and post an
 * entry of ENTRY_KIND_THREAD for this process.
 */
void add_thread(pid_t tid, char name[16])
{
  if (thread_index(tid) < 0 && num_threads < MAX_NUM_THREADS)
    {
      pid_t pid = get_tgid(tid);
      add_process(pid);
      thread_tids[num_threads] = tid;
      thread_pids[num_threads] = pid;
      num_threads++;
      struct entry en;
      en.kind = ENTRY_KIND_THREAD;
      en.payload.t.tid = tid;
      en.payload.t.pid = pid;
      memset(en.payload.t.name, 0, sizeof(en.payload.t.name));
      strncpy(en.payload.t.name, name, sizeof(*name));
      post_entry(&en);
    }
}


/**
 * handle event arriving in the ring buffer form BPF code
 */
int handle_event(void *ctx, void *data, size_t data_sz)
{
  if (!exiting && data != NULL && data_sz == sizeof(struct event))
    {
      const struct event *e = data;
      uint64_t ec = eventcount;
      entry* entries = (entry*) &(shmem[1]);
      if ((void*) &entries[ec+1] > (void*) &((char*)shmem)[SHARED_MEM_SIZE])
        {
          printf("shared mem buffer full\n");
          exiting = true;
        }
      else
        {
          add_thread(e->old_pid, (char*) &e->old_name);
          add_thread(e->new_pid, (char*) &e->comm    );
          struct entry en;
          en.kind = ENTRY_KIND_SCHED_SWITCH;
          en.payload.ss.old_tid = e->old_pid;
          en.payload.ss.old_pri = e->old_pri;
          memcpy(&en.payload.ss.old_name, &e->old_name, sizeof(en.payload.ss.old_name));
          en.payload.ss.new_tid = e->new_pid;
          en.payload.ss.new_pri = e->new_pri;
          memcpy(&en.payload.ss.new_name, &e->comm, sizeof(en.payload.ss.new_name));
          en.payload.ss.ns = e->ns;
          post_entry(&en);
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
  while (!exiting)
    {
      pthread_mutex_lock(&mutex);
      uint64_t c = ++counter;
      uint64_t n = c & -2;
      if ((n & 65535) == 0) // after 131k thread switches, sleep for 20ms to give tbe system some time to breathe...
        {
          struct timespec duration = { 0, 200000000 }; // 20ms
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


/**
 * main function
 */
int main(int argc, char**args)
{
  int entry_start_offset = (char*) &(shmem[1]) -
                           (char*) &(shmem[0]);
  int entry_size = (char *) &(((entry*) &(shmem[1]))[1]) -
                   (char *) &(((entry*) &(shmem[1]))[0]);

  printf("entries start offset 0x%x, entry size 0x%x\n",
         entry_start_offset, entry_size);

  if (entry_size != ENTRY_SIZE)
    {
      fprintf(stderr, "sizeof(entry) should be %d, but is %lu\n",ENTRY_SIZE, sizeof(entry));
      exit(1);
    }

  struct ring_buffer *rb = NULL;
  int err;
  //  int shared = shm_open(SHARED_MEM_NAME, O_RDWR | O_CREAT | O_EXCL, 0644);
  int shared = open(SHARED_MEM_NAME, O_RDWR | O_CREAT | O_EXCL, 0644);
  if (shared < 0)
    {
      fprintf(stderr,"shm_open(%s) failed: %d %s\n",SHARED_MEM_NAME, errno, strerror(errno));
      err = 1;
      goto cleanup;
    }
  err = ftruncate(shared, SHARED_MEM_SIZE);
  if (err)
    {
      fprintf(stderr,"ftruncate(%d,%ld) failed: %d %s\n",shared, SHARED_MEM_SIZE, errno, strerror(errno));
      goto cleanup;
    }

  shmem = mmap(NULL, SHARED_MEM_SIZE, PROT_READ|PROT_WRITE, MAP_SHARED_VALIDATE, shared, 0);
  if (shmem == MAP_FAILED)
    {
      fprintf(stderr,"mmap(NULL, %ld, %x, %x, %d, 0) failed: %d %s\n",SHARED_MEM_SIZE,
              PROT_READ|PROT_WRITE, MAP_SHARED_VALIDATE, shared,
              errno, strerror(errno));
      err = 1;
      goto cleanup;
    }
  shmem->num_entries = 0;
  shmem->entry_start_offset = entry_start_offset;
  shmem->entry_size = entry_size;
  shmem->done = false;
  shmem->blabla = true;
  shmem->blublu = true;
  // atomic_thread_fence(std::memory_order_release);
  __sync_synchronize();
  shmem->size = SHARED_MEM_SIZE;

  printf("sched starting:\n");

  /* Clean handling of Ctrl-C */
  signal(SIGINT, sig_handler);
  signal(SIGTERM, sig_handler);

  struct feeze_recorder_bpf *skel;

  /* Set up libbpf errors and debug info callback */
  libbpf_set_print(libbpf_print_fn);

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

  pthread_mutex_init(&mutex, NULL);

  pthread_t t1 = {};
  pthread_t t2 = {};
  pthread_create(&t1, NULL, t12start, NULL);  pthread_setname_np(t1,"AAAAA");
  pthread_create(&t2, NULL, t12start, "1" );  pthread_setname_np(t2,"BBBBB");

  while (!exiting)
    {
      err = ring_buffer__poll(rb, 100 /* timeout, ms */);

      if (err < 0)
        {
          printf("Error polling ring buffer: %s (%d)\n", strerror(err), err);
          exiting = true;
        }
      else
        {
          sleep(1);
        }
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
      shmem->done = true;
      __sync_synchronize();
    }
  if (shmem != MAP_FAILED && !false)
    {
      int res = munmap(shmem, SHARED_MEM_SIZE);
      if (res < 0)
        {
          fprintf(stderr,"munmap(%p,%ld) failed: %d %s\n",shmem, SHARED_MEM_SIZE, errno, strerror(errno));
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
  return err==0 ? 0 : 1;
}
