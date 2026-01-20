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
 * Java source code of class dev.flang.feeze.Offsets
 *
 *---------------------------------------------------------------------*/


package dev.flang.feeze;

/*
 * interace that defined offsets in the shared data created by the
 * `feeze_recorder` and defined in `src/c/feeze_recorder.c`.
 *
 * NYI: this should best be generated form the C code!
 */
public interface Offsets
{
  public static int entry_start_offset = 0x20;

  public static int ENTRY_KIND_OFFSET        = 0x00;

  public static int ENTRY_SS_OLD_PID_OFFSET  = 0x08;
  //  public static int ENTRY_SS_OLD_PRI_OFFSET  = 0x0c;
  public static int ENTRY_SS_OLD_NAME_OFFSET = 0x0c;
  public static int ENTRY_SS_NEW_PID_OFFSET  = 0x1c;
  //  public static int ENTRY_SS_NEW_PRI_OFFSET  = 0x20;
  public static int ENTRY_SS_NEW_NAME_OFFSET = 0x29;
  public static int ENTRY_SS_NS_OFFSET       = 0x30;
  public static int ENTRY_SS_COUNT_OFFSET    = 0x38;

  public static int ENTRY_P_PID_OFFSET       = 0x08;
  public static int ENTRY_P_NAME_OFFSET      = 0x0c;
  public static int ENTRY_P_NAME_LENGTH      = 0x20;

  public static int ENTRY_T_TID_OFFSET       = 0x08;
  public static int ENTRY_T_PID_OFFSET       = 0x0c;
  public static int ENTRY_T_NAME_OFFSET      = 0x10;
  public static int ENTRY_T_NAME_LENGTH      = 0x20;

  public static int ENTRY_SIZE            = 0x40;

  public static int ENTRY_KIND_UNUSED       = 0;
  public static int ENTRY_KIND_SCHED_SWITCH = 1;
  public static int ENTRY_KIND_PROCESS      = 2;
  public static int ENTRY_KIND_THREAD       = 3;
}
