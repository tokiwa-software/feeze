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

/**
 * Event record to be exchanged via ring buffer.
 *
 * NYI: the old/new name entries should not be needed if we record events for
 * initial thread names and thread name changes instead.
 */
struct event
{
  pid_t old_pid;
  int old_pri;
  char	old_name[16 /* TASK_COMM_LEN */];
  pid_t new_pid;
  int new_pri;
  char	comm[16 /* TASK_COMM_LEN */];
  __u64 ns;
};

#endif /* __FEEZE_RECORDER_COMMON_H */
