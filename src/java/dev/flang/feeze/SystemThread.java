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

import java.util.ArrayList;
import java.util.Arrays;

/*---------------------------------------------------------------------*/


/**
 * SystemThread represents a thread in recorded data
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class SystemThread extends FeezeThread
{


  static final String UNKNOWN_NAME = "<unknown>";

  int _tid;
  int _pid;
  SystemProcess _p;
  int _originalNumber;  // the thread number used in the feeze recording data
  int _displayedNumber; // the thread number used to display this thread
  boolean _swapper = false;


  /**
   * Indices in this ActionSubSet at which the thread name changes.
   */
  ArrayList<Integer> _newNamesAt = new ArrayList<Integer>();

  /**
   * Indices in _data of the thread name change recorded in _newNamesAt.
   */
  ArrayList<Integer> _newNames  = new ArrayList<Integer>();


  SystemThread(Data data, int tpid, int pid, SystemProcess p)
  {
    super(data);

    _tid = tpid;
    _pid = pid;
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
    if (_data.kind(at) == ENTRY_KIND_THREAD_NAME)
      {
        var sz = _newNamesAt.size();
        if (sz == 0 || _newNamesAt.get(sz-1) < numActions())  // make sure _newNamesAt is strictly increasing
          {
            _newNamesAt.add(numActions());
            _newNames.add(at);
            if (_tid == 0 && _p._pid ==-1 && !_swapper && nameFrom(numActions()).startsWith("swapper/"))
              {
                _swapper = true;
              }
          }
      }
    else
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
    if (_newNamesAt.size() == 0)
      {
        return UNKNOWN_NAME;
      }
    else
      {
        var l = 0;
        var r = _newNamesAt.size()-1;
        while (l<r)
          {
            var m = (l + r)/2;
            if (_newNamesAt.get(m) >= ai) { r = m-1; }
            if (_newNamesAt.get(m) <= ai) { l = m+1; }
          }
        var i = Math.max(0, l-1);
        return _data.threadName(_newNames.get(i));
      }
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
