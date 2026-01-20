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
#include "feeze_recorder_common.h"

char LICENSE[] SEC("license") = "AGPLv3";

struct {
	__uint(type, BPF_MAP_TYPE_PERCPU_ARRAY);
	__uint(max_entries, 1);
	__type(key, int);
	__type(value, struct event);
} heap SEC(".maps");


/* BPF ringbuf map */
struct {
	__uint(type, BPF_MAP_TYPE_RINGBUF);
	__uint(max_entries, 10 * 1024 * 1024 /* 10 MB */);
} rb SEC(".maps");


/**
 * Event counter used to detect ringbuf overflows
 */
uint64_t count = 0;

SEC("tracepoint/sched/sched_switch")
int on_task_switch(struct trace_event_raw_sched_switch *ctx)
{
   struct event *e;
   int zero = 0;
   e = bpf_map_lookup_elem(&heap, &zero);
   if (e)
     {
       if (ctx)
         {
           e->old_pid = ctx->prev_pid;
           e->old_pri = ctx->prev_prio;
           e->old_name[0] = ctx->prev_comm[0];
           e->old_name[1] = ctx->prev_comm[1];
           e->old_name[2] = ctx->prev_comm[2];
           e->old_name[3] = ctx->prev_comm[3];
           e->old_name[4] = ctx->prev_comm[4];
           e->old_name[5] = ctx->prev_comm[5];
           e->old_name[6] = ctx->prev_comm[6];
           e->old_name[7] = ctx->prev_comm[7];
           e->old_name[8] = ctx->prev_comm[8];
           e->old_name[9] = ctx->prev_comm[9];
           e->old_name[10] = ctx->prev_comm[10];
           e->old_name[11] = ctx->prev_comm[11];
           e->old_name[12] = ctx->prev_comm[12];
           e->old_name[13] = ctx->prev_comm[13];
           e->old_name[14] = ctx->prev_comm[14];
           e->old_name[15] = ctx->prev_comm[15];
           e->new_pid = ctx->next_pid;
           e->new_pri = ctx->next_prio;
           e->comm[0] = ctx->next_comm[0];
           e->comm[1] = ctx->next_comm[1];
           e->comm[2] = ctx->next_comm[2];
           e->comm[3] = ctx->next_comm[3];
           e->comm[4] = ctx->next_comm[4];
           e->comm[5] = ctx->next_comm[5];
           e->comm[6] = ctx->next_comm[6];
           e->comm[7] = ctx->next_comm[7];
           e->comm[8] = ctx->next_comm[8];
           e->comm[9] = ctx->next_comm[9];
           e->comm[10] = ctx->next_comm[10];
           e->comm[11] = ctx->next_comm[11];
           e->comm[12] = ctx->next_comm[12];
           e->comm[13] = ctx->next_comm[13];
           e->comm[14] = ctx->next_comm[14];
           e->comm[15] = ctx->next_comm[15];
           e->ns = bpf_ktime_get_ns();
           e->count = __sync_fetch_and_add(&count, 1);
           if (bpf_ringbuf_output(&rb, e, sizeof(*e), 0)==0)
             {
               // ok
             }
           else
             {
               // error
             }
         }
     }

  return 0;
}
