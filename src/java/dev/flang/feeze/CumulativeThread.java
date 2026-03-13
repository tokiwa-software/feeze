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
import java.util.BitSet;

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
  volatile int[] _numRunning;


  CumulativeThread(SystemUser u)
  {
    super(u._data);

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




  public int[] numRunning()
  {
    var result = _numRunning;
    if (result == null)
      {
        synchronized (this)
          {
            result = _numRunning;
            if (result == null)
              {
                result = new int[numActions()];
                _numRunning = result;
                var n = 0;
                var covered = new BitSet();
                var running = new BitSet();
                var min = 0;
                for (var i = 0; i<numActions(); i++)
                  {
                    var ot = _data.oldThreadAt(at(i));
                    var nt = _data.newThreadAt(at(i));
                    if (ot != null && ot.user() == _user)
                      {
                        if (running.get(ot._tid) || !covered.get(ot._tid))
                          {
                            n--;
                            running.clear(ot._tid);
                          }
                        else
                          {
                          }
                        covered.set(ot._tid);
                      }
                    min = Math.min(n, min);
                    if (nt != null && nt.user() == _user)
                      {
                        if (!running.get(nt._tid))
                          {
                            n++;
                            running.set(nt._tid);
                          }
                        covered.set(nt._tid);
                      }
                    result[i] = n;
                  }

                // since some threads might have been running from the
                // beginning, increase the number of threads running such that
                // it will never be negative:
                for (var i = 0; i<numActions(); i++)
                  {
                    result[i] -= min;
                    if (false && i < 20) // NYI: DEBUG output, eventually remove this
                      {
                        var ot = _data.oldThreadAt(at(i));
                        var nt = _data.newThreadAt(at(i));
                        System.out.println("running #"+i+"/"+at(i)+" is "+result[i]+" for "+this+" old: "+ot+" new "+nt);
                        if (!startsRunning(i) &&
                            !continuesRunning(i) &&
                            !stopsRunning(i))
                          {
                            System.out.println("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^yyy!");
                          }
                      }
                  }
              }
          }
      }
    return result;
  }

  public int numRunning(int i)
  {
    if (PRECONDITIONS) require
      (0 <= i,
       i < numActions());
    var nr = numRunning();
    return nr[i];
  }

  @Override
  public boolean startsRunning(int i)
  {
    return numRunning(i) > 0 && (i == 0 || numRunning(i-1) == 0);
  }
  @Override
  public boolean continuesRunning(int i)
  {
    return (numRunning(i) > 0) == (numRunning(i-1) > 0);
  }
  @Override
  public boolean stopsRunning(int i)
  {
    return numRunning(i) == 0 && (i == 0 || numRunning(i-1) > 0);
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
