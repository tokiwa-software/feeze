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
 * Copyright (c) 2026, Tokiwa Software GmbH, Germany
 *
 * Java source code of class dev.flang.feeze.SystemProcess
 *
 *---------------------------------------------------------------------*/


package dev.flang.feeze;

import java.util.ArrayList;

/*---------------------------------------------------------------------*/


/**
 * SystemProcess  represents a process in recorded data
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class SystemProcess
{
  int _pid;
  String _name;
  int _num;
  ArrayList<SystemThread> _threads = new ArrayList<>();

  SystemProcess(int pid,
                String name,
                int num)
  {
    _pid = pid;
    _name = name;
    _num = num;
  }

  void addThread(SystemThread t)
  {
    _threads.add(t);
  }

  public String toString()
  {
    return ""+_pid+" "+_name;
  }
}
