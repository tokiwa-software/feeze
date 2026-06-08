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
 * Source of feeze_recorder.c
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

#include "feeze_record.h"
#include "feeze_recorder_common.h"
#include "feeze_recorder.skel.h"


/**
 * Flag used to indicate that we should stop, clean up and exit.
 */
static volatile bool exiting = false;



/**
 * Signal handler used to set `exiting` in case of ^C and ^\.
 */
static void sig_handler(int sig)
{
  exiting = true;
  //   finishing = true;   -- NYI!
}



#define SHARED_MEM_NAME "/tmp/feeze_events_recorder_data"
#define SHARED_MEM_SIZE ((off_t) 64*1024*1024)


struct record_config
{
  const char* lib_fuzion;
  size_t shmem_size;
  const char* shmem_file_name;
};

struct record_config *config = NULL;


/**
 * main function
 */
void *record1(void *arg)
{
  config = arg;

  feeze_record(config->lib_fuzion,
               config->shmem_size,
               config->shmem_file_name);

  if (config != NULL)
    {
      free(config);
    }

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

  /* Clean handling of Ctrl-C */
  signal(SIGINT, sig_handler);     // NYI: MOVE out of this and to the main fuzion / C code!
  signal(SIGTERM, sig_handler);

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
          pthread_create(&rt, NULL, record1, name);  pthread_setname_np(rt,"feeze_record");
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
              pthread_create(&rt, NULL, record1, conf);
              pthread_setname_np(rt,"feeze_record");
            }
        }
      else if (strcmp(s, "STOP\n") == 0)
        {
          feeze_finish_record();
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
