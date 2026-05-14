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
 * Java source code of class dev.flang.feeze.ActionSubSet
 *
 *---------------------------------------------------------------------*/


package dev.flang.feeze;

import java.util.Arrays;

import dev.flang.util.ANY;

/*---------------------------------------------------------------------*/


/**
 * ActionSubSet represents a set of actions in recorded data, like all the
 * scheduling actions of a thread or all for one CPU.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
abstract class ActionSubSet extends ANY
{

  final Data _data;
  int _num_actions = 0;

  int[] _at = new int[16];


  ActionSubSet(Data data)
  {
    _data = data;
  }

  public int numActions()
  {
    return _num_actions;
  }

  public int at(int i)
  {
    if (PRECONDITIONS) require
      (i >= 0,
       i < numActions());

    return _at[i];
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
    while (n > 0 && (_data.nanosAtSwitch(_at[n]) - _data.nanosAtSwitch(_at[n-1]) < 0))
      {
        var x = _at[n]; _at[n] = _at[n-1]; _at[n-1] = x;
        n--;
      }
  }


  public abstract boolean startsRunning(int i);
  public abstract boolean continuesRunning(int i);
  public abstract boolean stopsRunning(int i);
  public abstract boolean waking(int i);
  public abstract boolean wakesup(int i);

}
