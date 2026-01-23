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
class SystemThread
{
  Data _data;
  int _tid;
  int _pid;
  String _name;
  SystemProcess _p;
  int _num;

  int _num_actions = 0;

  int[] _at = new int[16];

  SystemThread(Data data, int tpid, int pid, String name, SystemProcess p)
  {
    _data = data;
    _tid = tpid;
    _pid = pid;
    _name = name;
    _p = p;
    _p.addThread(this);
  }

  void addAction(int at)
  {
    if (_num_actions >= _at.length)
      {
        _at = Arrays.copyOf(_at, _at.length*2);
      }
    _at[_num_actions] = at;
    _num_actions++;
    // fix order to be strictly increasing nanos. This might have gotten mixed
    // up due to race conditions writing to ring buffers.
    var n = _num_actions-1;
    while (n > 0 && (_data.nanos(_at[n]) - _data.nanos(_at[n-1]) < 0))
      {
        var x = _at[n]; _at[n] = _at[n-1]; _at[n-1] = x;
        n--;
      }
  }

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
        n =  _data.new_name(at);
      }
    else
      {
        n =  _data.old_name(at);
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

  public String toString()
  {
    return _num_actions > 0
      ? toString(0)
      : Integer.toString(_tid);
  }

}
