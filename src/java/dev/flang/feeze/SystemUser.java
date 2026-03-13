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
 * SystemUser  represents a user in recorded data
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class SystemUser
{
  final Data _data;
  int _uid;
  String _name;
  int _num;
  ArrayList<SystemProcess> _processes = new ArrayList<>();
  volatile CumulativeThread _cumulative = null;

  SystemUser(Data data,
             int uid,
             String name,
             int num)
  {
    _data = data;
    _uid = uid;
    _name = name;
    _num = num;
  }

  void addProcess(SystemProcess p)
  {
    _processes.add(p);
  }


  CumulativeThread cumulative()
  {
    if (_cumulative == null)
      {
        synchronized (this)
          {
            if (_cumulative == null)
              {
                var c = new CumulativeThread(this);
                _cumulative = c;
              }
          }
      }
    return _cumulative;
  }

  public String toString()
  {
    return ""+_uid+" "+_name;
  }
}
