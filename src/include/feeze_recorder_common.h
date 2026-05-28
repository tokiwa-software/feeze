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
 * Source of feeze_recorder_common.h
 *
 * This is the include file with declarations used both in bpf code and C code
 * of the feeze recorder.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 *
 *---------------------------------------------------------------------*/


#ifndef __FEEZE_RECORDER_COMMON_H
#define __FEEZE_RECORDER_COMMON_H

#define RB_EVENT_GAP           999   // gap of events before this due to ring buffer overflow
#define RB_EVENT_SCHED_SWITCH  13
#define RB_EVENT_SCHED_WAKEUP  25
#define RB_EVENT_SCHED_WAKING  47
#define RB_EVENT_FUZION_USER   67


/**
 * Event record to be exchanged via ring buffer.
 *
 * NYI: the old/new name entries should not be needed if we record events for
 * initial thread names and thread name changes instead.
 */
struct event
{
  int event_kind;
  pid_t old_pid;
  int old_pri;
  char	old_name[16 /* TASK_COMM_LEN */];
  pid_t new_pid;
  int new_pri;
  char	comm[16 /* TASK_COMM_LEN */];
  __u64 ns;
  __u32 cpu_id;
  int count;  // event count to detect missing events and ensure correct ordering
};

#endif /* __FEEZE_RECORDER_COMMON_H */
