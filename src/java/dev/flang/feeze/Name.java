
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
 * Java source code of class dev.flang.feeze.Name
 *
 *---------------------------------------------------------------------*/


package dev.flang.feeze;

import java.nio.ByteBuffer;

import java.nio.charset.StandardCharsets;

/*---------------------------------------------------------------------*/


/**
 * Name represents a thread name in recorded data
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class Name implements Comparable<Name>
{
  int _at;
  boolean _old;
  Name(int at, boolean old)
  {
    this._at = at;
    this._old = old;
  }

  @Override
  public int compareTo(Name o)
  {
    var tpid = pid();
    var opid = o.pid();
    var result = Integer.compare(tpid, opid);
    return result;
  }
  int pid()
  {
    return _old ? Feeze.old_pid(_at)
                : Feeze.new_pid(_at);
  }
  public String toString()
  {
    byte[] bs = new byte[16];
    if (_old)
      {
        Feeze.old_name(_at, bs);
      }
    else
      {
        Feeze.new_name(_at, bs);
      }
    return ""+pid()+" "+StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bs));
  }
}
