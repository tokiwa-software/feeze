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


#include <vmlinux.h>
#include <bpf/bpf_helpers.h>
#include <bpf/bpf_core_read.h>
#include <bpf/usdt.bpf.h>
#include "feeze_recorder_common.h"

char LICENSE[] SEC("license") = "GPL";

// #define RING_BUF_SIZE (10 * 1024 * 1) /* 10 KB, use this to test ringbuffer overflow handling */
#define RING_BUF_SIZE (10 * 1024 * 1024) /* 10 MB. NYI: Can we make this configurable via Feeze GUI? */

/* BPF ringbuf map */
struct {
	__uint(type, BPF_MAP_TYPE_RINGBUF);
        __uint(max_entries, RING_BUF_SIZE);
} rb SEC(".maps");


/**
 * Overflow states:
 *

      +-----------------------report------------------------------+
      |                                                           |
      V                                                           |
    NONE  ----overflow---->  YES  ----try_report--->  REPORTING --+
                              A                                   |
                              |                                   |
                              +--------------overflow-------------+

 */
#define OVFL_NONE      0
#define OVFL_YES       1
#define OVFL_REPORTING 2


/**
 * Did we have a ringbuffer overflow? If so, send a GAP event next!
 *
 * This is accessed using __sync_bool_compare_and_swap for atomicity.
 */
uint64_t overflow = OVFL_NONE;


bool ringbuf_out0(struct event *e)
{
  if (bpf_ringbuf_output(&rb, e, sizeof(*e), 0)==0)
    {
      // ok
      return true;
    }
  else
    { // error

      // remember to post a RB_EVENT_GAP once the ringbuf is working again:
      __sync_bool_compare_and_swap(&overflow, OVFL_NONE, OVFL_YES);
      return false;
    }
}


/**
 * Write given even to ring buffer, handle overflow and post RB_EVENT_GAP after
 * an overflow had occured.
 */
void ringbuf_out(struct event *e)
{
  uint64_t ovfl = __sync_bool_compare_and_swap(&overflow, OVFL_YES, OVFL_REPORTING);
  if (ovfl == OVFL_NONE)  // all ok, no overflow, so post event e
    {
      ringbuf_out0(e);
    }
  else if (ovfl == OVFL_YES)  // we have to report the overflow
    {
      struct event gap;
      gap.event_kind = RB_EVENT_GAP;
      gap.old_pid = (pid_t) bpf_get_current_pid_tgid()&0xffffffff;
      gap.cpu_id = bpf_get_smp_processor_id();
      gap.ns = e->ns;
      if (bpf_ringbuf_output(&rb, &gap, sizeof(gap), 0)==0)
        {
          // ok, so back to OVFL_NONE
          uint64_t res = __sync_bool_compare_and_swap(&overflow, OVFL_REPORTING, OVFL_NONE);
          // assert(res == OVFL_REPORTING);

          // post event e
          ringbuf_out0(e);
        }
      else
        { // error, ovfl reporting failed, so go back to OVFL_YES
          uint64_t res =__sync_bool_compare_and_swap(&overflow, OVFL_REPORTING, OVFL_YES);
          // assert(res == OVFL_REPORTING);
        }
    }
}


/**
 * sched_switch is a thread switch on a given CPUs. This typically does not
 * directly switch between two arbitray threads, but seems to always switch
 * between thead with tid=0, the swapper, and some real thread.
 */
SEC("tracepoint/sched/sched_switch")
int on_task_switch(struct trace_event_raw_sched_switch *ctx)
{
  struct event es;
  if (ctx)
    {
      es.event_kind = RB_EVENT_SCHED_SWITCH;
      es.old_pid = ctx->prev_pid;
      es.old_pri = ctx->prev_prio;
      es.old_name[0] = ctx->prev_comm[0];
      es.old_name[1] = ctx->prev_comm[1];
      es.old_name[2] = ctx->prev_comm[2];
      es.old_name[3] = ctx->prev_comm[3];
      es.old_name[4] = ctx->prev_comm[4];
      es.old_name[5] = ctx->prev_comm[5];
      es.old_name[6] = ctx->prev_comm[6];
      es.old_name[7] = ctx->prev_comm[7];
      es.old_name[8] = ctx->prev_comm[8];
      es.old_name[9] = ctx->prev_comm[9];
      es.old_name[10] = ctx->prev_comm[10];
      es.old_name[11] = ctx->prev_comm[11];
      es.old_name[12] = ctx->prev_comm[12];
      es.old_name[13] = ctx->prev_comm[13];
      es.old_name[14] = ctx->prev_comm[14];
      es.old_name[15] = ctx->prev_comm[15];
      es.new_pid = ctx->next_pid;
      es.new_pri = ctx->next_prio;
      es.comm[0] = ctx->next_comm[0];
      es.comm[1] = ctx->next_comm[1];
      es.comm[2] = ctx->next_comm[2];
      es.comm[3] = ctx->next_comm[3];
      es.comm[4] = ctx->next_comm[4];
      es.comm[5] = ctx->next_comm[5];
      es.comm[6] = ctx->next_comm[6];
      es.comm[7] = ctx->next_comm[7];
      es.comm[8] = ctx->next_comm[8];
      es.comm[9] = ctx->next_comm[9];
      es.comm[10] = ctx->next_comm[10];
      es.comm[11] = ctx->next_comm[11];
      es.comm[12] = ctx->next_comm[12];
      es.comm[13] = ctx->next_comm[13];
      es.comm[14] = ctx->next_comm[14];
      es.comm[15] = ctx->next_comm[15];
      es.cpu_id = bpf_get_smp_processor_id();
      es.ns = bpf_ktime_get_ns();
      ringbuf_out(&es);
    }

  return 0;
}

/**
 * waking is if one thread changes a blocked thread into ready. This, however, does not add
 * that thread to any CPUs runqueue yet.
 */
SEC("tracepoint/sched/sched_waking")
int on_task_waking(struct trace_event_raw_sched_wakeup_template *ctx)
{
  int kind = RB_EVENT_SCHED_WAKING;
  struct event es;
  if (ctx)
    {
      es.event_kind = kind;

      /* not sure why this does not work, produces `invalid bpf_context access off=4 size=4`

         es.old_pid = ctx->ent.pid;

         so instead, using `bpf_get_current_pid_tgid`:

      */
      es.old_pid = (pid_t) bpf_get_current_pid_tgid()&0xffffffff;

      es.new_pid = ctx->pid;
      es.new_pri = ctx->prio;
      es.comm[0] = ctx->comm[0];
      es.comm[1] = ctx->comm[1];
      es.comm[2] = ctx->comm[2];
      es.comm[3] = ctx->comm[3];
      es.comm[4] = ctx->comm[4];
      es.comm[5] = ctx->comm[5];
      es.comm[6] = ctx->comm[6];
      es.comm[7] = ctx->comm[7];
      es.comm[8] = ctx->comm[8];
      es.comm[9] = ctx->comm[9];
      es.comm[10] = ctx->comm[10];
      es.comm[11] = ctx->comm[11];
      es.comm[12] = ctx->comm[12];
      es.comm[13] = ctx->comm[13];
      es.comm[14] = ctx->comm[14];
      es.comm[15] = ctx->comm[15];
      es.cpu_id = ctx->target_cpu;
      es.ns = bpf_ktime_get_ns();
      ringbuf_out(&es);
    }
  return 0;
}


/**
 * wakeup adds a thread to a CPUs runqueue.  This typcialls happens after `sched_waking`, at this point
 * we no longer know what other thread caused us to wake up.
 */
SEC("tracepoint/sched/sched_wakeup")
int on_task_wakeup(struct trace_event_raw_sched_wakeup_template *ctx)
{
  int kind = RB_EVENT_SCHED_WAKEUP;
  struct event es;
  if (ctx)
    {
      es.event_kind = kind;

      /* not sure why this does not work, produces `invalid bpf_context access off=4 size=4`

         es.old_pid = ctx->ent.pid;

         so instead, using `bpf_get_current_pid_tgid`:

      */
      es.old_pid = (pid_t) bpf_get_current_pid_tgid()&0xffffffff;

      es.new_pid = ctx->pid;
      es.new_pri = ctx->prio;
      es.comm[0] = ctx->comm[0];
      es.comm[1] = ctx->comm[1];
      es.comm[2] = ctx->comm[2];
      es.comm[3] = ctx->comm[3];
      es.comm[4] = ctx->comm[4];
      es.comm[5] = ctx->comm[5];
      es.comm[6] = ctx->comm[6];
      es.comm[7] = ctx->comm[7];
      es.comm[8] = ctx->comm[8];
      es.comm[9] = ctx->comm[9];
      es.comm[10] = ctx->comm[10];
      es.comm[11] = ctx->comm[11];
      es.comm[12] = ctx->comm[12];
      es.comm[13] = ctx->comm[13];
      es.comm[14] = ctx->comm[14];
      es.comm[15] = ctx->comm[15];
      es.cpu_id = ctx->target_cpu;
      es.ns = bpf_ktime_get_ns();
      ringbuf_out(&es);
    }
  return 0;
}


SEC("usdt")
int handle_fuzion_probe(struct pt_regs *ctx)
{
  int kind = RB_EVENT_FUZION_USER;
  struct event es;
  if (ctx)
    {
      es.event_kind = kind;

      /* not sure why this does not work, produces `invalid bpf_context access off=4 size=4`

         es.old_pid = ctx->ent.pid;

         so instead, using `bpf_get_current_pid_tgid`:

      */
      es.new_pid = (pid_t) bpf_get_current_pid_tgid()&0xffffffff;

      int argcnt = bpf_usdt_arg_cnt(ctx);
      long lres;
      int argres = bpf_usdt_arg(ctx, 0, &lres);
      es.new_pri = (int) lres;   // col
      if (argcnt == 5)
        {
          long str0, str1, str2, str3;
          int argres0 = bpf_usdt_arg(ctx, 1, &str0);
          int argres1 = bpf_usdt_arg(ctx, 2, &str1);
          int argres2 = bpf_usdt_arg(ctx, 3, &str2);
          int argres3 = bpf_usdt_arg(ctx, 4, &str3);
          es.comm[ 0]     = (char) ((str0 >>  0) & 0xff);
          es.comm[ 1]     = (char) ((str0 >>  8) & 0xff);
          es.comm[ 2]     = (char) ((str0 >> 16) & 0xff);
          es.comm[ 3]     = (char) ((str0 >> 24) & 0xff);
          es.comm[ 4]     = (char) ((str0 >> 32) & 0xff);
          es.comm[ 5]     = (char) ((str0 >> 40) & 0xff);
          es.comm[ 6]     = (char) ((str0 >> 48) & 0xff);
          es.comm[ 7]     = (char) ((str0 >> 56) & 0xff);
          es.comm[ 8]     = (char) ((str1 >>  0) & 0xff);
          es.comm[ 9]     = (char) ((str1 >>  8) & 0xff);
          es.comm[10]     = (char) ((str1 >> 16) & 0xff);
          es.comm[11]     = (char) ((str1 >> 24) & 0xff);
          es.comm[12]     = (char) ((str1 >> 32) & 0xff);
          es.comm[13]     = (char) ((str1 >> 40) & 0xff);
          es.comm[14]     = (char) ((str1 >> 48) & 0xff);
          es.comm[15]     = (char) ((str1 >> 56) & 0xff);
          es.old_name[ 0] = (char) ((str2 >>  0) & 0xff);
          es.old_name[ 1] = (char) ((str2 >>  8) & 0xff);
          es.old_name[ 2] = (char) ((str2 >> 16) & 0xff);
          es.old_name[ 3] = (char) ((str2 >> 24) & 0xff);
          es.old_name[ 4] = (char) ((str2 >> 32) & 0xff);
          es.old_name[ 5] = (char) ((str2 >> 40) & 0xff);
          es.old_name[ 6] = (char) ((str2 >> 48) & 0xff);
          es.old_name[ 7] = (char) ((str2 >> 56) & 0xff);
          es.old_name[ 8] = (char) ((str3 >>  0) & 0xff);
          es.old_name[ 9] = (char) ((str3 >>  8) & 0xff);
          es.old_name[10] = (char) ((str3 >> 16) & 0xff);
          es.old_name[11] = (char) ((str3 >> 24) & 0xff);
          es.old_name[12] = (char) ((str3 >> 32) & 0xff);
          es.old_name[13] = (char) ((str3 >> 40) & 0xff);
          es.old_name[14] = (char) ((str3 >> 48) & 0xff);
          es.old_name[15] = (char) ((str3 >> 56) & 0xff);
          es.event_kind = kind;
        }
      else
        {
          es.comm[0] = 'X';
          es.comm[1] = 0;
        }
      es.ns = bpf_ktime_get_ns();
      ringbuf_out(&es);
    }
  return 0;
}
