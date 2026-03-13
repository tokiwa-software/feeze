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
    user().cumulative().addAction(at);
  }


  @Override
  public String toString(int ai)
  {
    // if (PRECONDITIONS) require
    //   (0 <= ai,
    //   (ai < _num_actions);

    byte[] bs = new byte[16];
    var at = _at[ai];
    String n;
    if (Feeze.new_pid(at) == _tid)
      {
        n = _data.new_name(at);
      }
    else
      {
        n = _data.old_name(at);
      }
    if (_tid == _p._pid && !n.equals(_p._name))
      {
        return n+" ("+_p._name+")";
      }
    else
      {
        return n;
      }
    // return _tid+" "+n+" ("+_p+")";
  }

  @Override
  public String toString()
  {
    return _num_actions > 0
      ? toString(0)
      : Integer.toString(_tid);
  }

}
