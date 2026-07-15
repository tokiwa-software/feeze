/*

This file is part of the Feeze scheduling analysis tool.

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
 * Source of feeze_record.h
 *
 * This is the C include of the main record function of the feeze recorder.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 *
 *---------------------------------------------------------------------*/


#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif

#include <stddef.h>


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
                  size_t shmem_size,
                  const char* shmem_file_name);


/**
 * Stop a running call to `feeze_record`.
 */
void feeze_finish_record();
