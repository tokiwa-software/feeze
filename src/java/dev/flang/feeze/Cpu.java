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
 * Java source code of class dev.flang.feeze.Cpu
 *
 *---------------------------------------------------------------------*/


package dev.flang.feeze;

import dev.flang.util.ANY;

/*---------------------------------------------------------------------*/


/**
 * Cpu represents a CPU in recorded data
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class Cpu extends ActionSubSet
{
  final int _id;

  Cpu(Data data, int id)
  {
    super(data);
    _id = id;
  }

  void addAction(int at)
  {
    super.addAction(at);
  }

  @Override
  public boolean startsRunning(int i)
  {
    return !_data.newThreadAt(at(i)).isSwapper();
  }
  @Override
  public boolean continuesRunning(int i)
  {
    return startsRunning(i) && stopsRunning(i);
  }
  @Override
  public boolean stopsRunning(int i)
  {
    return !_data.oldThreadAt(at(i)).isSwapper();
  }

  @Override
  public String toString()
  {
    return "CPU#" + _id;
  }

}
