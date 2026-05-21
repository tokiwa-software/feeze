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
 * Java source code of class dev.flang.feeze.SystemThread
 *
 *---------------------------------------------------------------------*/


package dev.flang.feeze;

import java.util.Arrays;

/*---------------------------------------------------------------------*/


/**
 * SystemThread represents a thread in recorded data
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class SystemThread extends FeezeThread
{
  int _tid;
  int _pid;
  String _name;
  SystemProcess _p;
  int _num;
  boolean _swapper = false;

  SystemThread(Data data, int tpid, int pid, String name, SystemProcess p)
  {
    super(data);

    _tid = tpid;
    _pid = pid;
    _name = name;
    _p = p;
    _p.addThread(this);
  }


  @Override
  public SystemUser user()
  {
    return _p._user;
  }


  @Override
  public SystemProcess process()
  {
    return _p;
  }


  @Override
  void addAction(int at)
  {
    super.addAction(at);
    var u = user();
    if (u != null && // NYI: Check why it sometimes happened that this is null
        _data.kind(at) == ENTRY_KIND_SCHED_SWITCH)
      {
        u.cumulative().addAction(at);
      }
    _swapper = _swapper ||
      _p._pid == -1 && nameFrom(numActions()-1).startsWith("swapper/");
  }

  @Override
  public boolean isProcess()
  {
    return _tid == _p._pid;
  }


  boolean isSwapper()
  {
    return _swapper;
  }


  private String nameFrom(int ai)
  {
    while (ai >= 0)
      {
        var at = _at[ai];
        switch (_data.kind(at))
          {
          case ENTRY_KIND_SCHED_SWITCH -> { return Feeze.new_pid(at) == _tid ? _data.new_name(at)
                                                                             : _data.old_name(at);
                                          }
          case ENTRY_KIND_SCHED_WAKING -> { return _data.new_name(at); }
          case ENTRY_KIND_SCHED_WAKEUP -> { return _data.new_name(at); }
          default -> {}
          }
        ai--;
      }
    return _name;
  }


  @Override
  public String toString(int ai)
  {
    // if (PRECONDITIONS) require
    //   (0 <= ai,
    //   (ai < _num_actions);

    byte[] bs = new byte[16];
    var at = _at[ai];
    String n = nameFrom(ai);
    if (isProcess() && !n.equals(_p._name))
      {
        n = n + " (" + _p._name + ")";
      }
    if (!true)
      {
        n = _tid + "/" + _pid + " " + n + " (" + _p + ")";
      }
    return n;
  }

  @Override
  public String toString()
  {
    return _num_actions > 0
      ? toString(0)
      : Integer.toString(_tid);
  }

}
