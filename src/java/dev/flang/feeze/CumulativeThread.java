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
 * Java source code of class dev.flang.feeze.CumulativeThread
 *
 *---------------------------------------------------------------------*/


package dev.flang.feeze;

import java.util.Arrays;

import dev.flang.util.ANY;

/*---------------------------------------------------------------------*/


/**
 * CumulativeThread represents an artificial thread with accumulated data from
 * several threads.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class CumulativeThread extends FeezeThread
{

  final SystemUser _user;


  CumulativeThread(SystemUser u)
  {
    _user = u;
  }


  @Override
  public SystemUser user()
  {
    return _user;
  }

  @Override
  public SystemProcess process()
  {
    return null;
  }

  @Override
  public int numActions()
  {
    return 0; // NYI!
  }

  @Override
  public int at(int i)
  {
    throw new Error(); // NYI!
  }

  /**
   * Name of this thread at given index.  Note that names can change during the
   * lifespan of a thread.
   */
  @Override
  public String toString(int ai)
  {
    return toString();
  }

  /**
   * Name of this thread.
   */
  @Override
  public String toString()
  {
    return _user._name;
  }

}
