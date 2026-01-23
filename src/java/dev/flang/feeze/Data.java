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
 * Java source code of class dev.flang.feeze.Data
 *
 *---------------------------------------------------------------------*/


package dev.flang.feeze;

import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.TreeMap;

/*---------------------------------------------------------------------*/


/**
 * Data recoreded for the Feeze scheduling monitoring tool
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class Data implements Offsets
{
  final MappedByteBuffer _b;
  int names_processed = 0;

  TreeMap<Name,Name> _namesMap = new TreeMap<>();

  ArrayList<Name> _namesList = new ArrayList<>();

  TreeMap<Integer, SystemProcess> _processesMap = new TreeMap<>();
  ArrayList<SystemProcess> _processes = new ArrayList<>();

  TreeMap<Integer, SystemThread> _threadsMap = new TreeMap<>();
  ArrayList<SystemThread> _threads = new ArrayList<>();

  ArrayList<Integer> _gaps = new ArrayList<>();

  Data(MappedByteBuffer b)
  {
    _b = b;
  }

  int kind(int at)
  {
    return Feeze.kind(at);
  }

  Name name(int at, boolean old)
  {
    var n = new Name(at, false);
    var e = _namesMap.get(n);
    if (e == null)
      {
        e = n;
        _namesMap.put(n, n);
        _namesList.add(n);
        //System.out.println("NEW NAME: "+n);
      }
    return e;
  }

  SystemThread thread(int at, boolean old)
  {
    var tpid = old ? Feeze.old_pid(at)
                   : Feeze.new_pid(at);
    return _threadsMap.get(tpid);
  }

  int entryCount()
  {
    return names_processed;
  }

  Name oldAt(int at)
  {
    return name(at, true);
  }

  Name newAt(int at)
  {
    return name(at, false);
  }

  SystemThread oldThreadAt(int at)
  {
    return thread(at, true);
  }

  SystemThread newThreadAt(int at)
  {
    return thread(at, false);
  }

  long nanosMin()
  {
    int at = 0;
    while (at < entryCount())
      {
        switch (kind(at))
          {
          case ENTRY_KIND_SCHED_SWITCH: return Feeze.ns(at);
          default: break;
          }
        at++;
      }
    return 0;
  }
  long nanosMax()
  {
    int at = entryCount()-1;
    while (0 <= at)
      {
        switch (kind(at))
          {
          case ENTRY_KIND_SCHED_SWITCH: return Feeze.ns(at);
          default: break;
          }
        at--;
      }
    return 0;
  }

  long nanos(int at)
  {
    switch (kind(at))
      {
      case ENTRY_KIND_SCHED_SWITCH: return Feeze.ns(at);
      default: throw new Error("No nanos available for kind "+kind(at)+" at "+at);
      }
  }

  BitSet _hasCount = new BitSet();

  int count(int at)
  {
    switch (kind(at))
      {
      case ENTRY_KIND_SCHED_SWITCH:
        var c = Feeze.count(at);
        _hasCount.set(c);
        return c;
      default: throw new Error("No count available for kind "+kind(at)+" at "+at);
      }
  }

  boolean hasCount(int c)
  {
    return _hasCount.get(c);
  }

  boolean isGap(int at)
  {
    return switch (kind(at))
      {
      case ENTRY_KIND_SCHED_SWITCH -> at>0 && count(at)>0 && hasCount(count(at)) && !hasCount(count(at)-1);
      default -> false;
      };
  }

  byte getByte(int at, int off)
  {
    return Feeze.b.get(entry_start_offset + at*ENTRY_SIZE + off);
  }
  int getInt(int at, int off)
  {
    return Feeze.b.getInt(entry_start_offset + at*ENTRY_SIZE + off);
  }
  String getName(int at, int off, int len)
  {
    var l = 0;
    while (l < len && getByte(at, off+l) != 0)
      {
        l++;
      }
    var bs = new byte[l];
    for (var i = 0; i<l; i++)
      {
        bs[i] = getByte(at, off+i);
      }
    return StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bs) /* NYI: could we do  b.subrange(..)? */).toString();
  }

  synchronized void processNewData()
  {
    var num_entries = (int) _b.getLong(8);
    if (names_processed < num_entries)
      {
        while (names_processed < num_entries)
          {
            if (false && names_processed > 1)
              {
                var ns = nanos(names_processed);
                var ns0 = nanos(names_processed-1);
                var delta = ns-ns0;
                if (delta <= 0)
                  {
                    System.out.println("ns not continues at "+names_processed+": "+ns0+" -> "+ns+" delta: "+delta);
                  }
              }
            switch (kind(names_processed))
              {
              case ENTRY_KIND_UNUSED      : break;
              case ENTRY_KIND_PROCESS:
                {
                  var pid  = getInt(names_processed, ENTRY_P_PID_OFFSET);
                  var name = getName(names_processed, ENTRY_P_NAME_OFFSET, ENTRY_P_NAME_LENGTH);
                  var p = new SystemProcess(pid, name, _processes.size());
                  _processesMap.put(pid, p);
                  _processes.add(p);
                  break;
                }
              case ENTRY_KIND_THREAD:
                {
                  var tid  = getInt(names_processed, ENTRY_T_TID_OFFSET);
                  var pid  = getInt(names_processed, ENTRY_T_PID_OFFSET);
                  var name = getName(names_processed, ENTRY_T_NAME_OFFSET, ENTRY_T_NAME_LENGTH);
                  var t = new SystemThread(this, tid, pid, name, _processesMap.get(pid));
                  _threadsMap.put(tid, t);
                  _threads.add(t);
                  break;
                }
              case ENTRY_KIND_SCHED_SWITCH:
                {
                  var ignore = count(names_processed);
                  var old = name(names_processed, true);
                  var nju = name(names_processed, false);
                  var ot = thread(names_processed, true);
                  var nt = thread(names_processed, false);
                  ot.addAction(names_processed);
                  nt.addAction(names_processed);
                  break;
                }
              default:
                {
                  System.err.println("*** unknown entry kind "+kind(names_processed)+" for entry #"+names_processed);
                }
              }
            names_processed++;
          }
      }
    _threads.sort((t1,t2) ->
                  Long.compare((long) t1._pid << 32 | t1._tid,
                               (long) t2._pid << 32 | t2._tid));
    _threads.sort((t1,t2) ->
                  Long.compare((long) t1._p._num << 32 | t1._tid,
                               (long) t2._p._num << 32 | t2._tid));
    for (var n = 0; n < _threads.size(); n++)
      {
        _threads.get(n)._num = n;
      }
    for (var i = 0; i<names_processed; i++)
      {
        if (isGap(i))
          {
            _gaps.add(i);
          }
      }
  }

  String new_name(int at)
  {
    return getName(at, ENTRY_SS_NEW_NAME_OFFSET, 16);
  }
  String old_name(int at)
  {
    return getName(at, ENTRY_SS_OLD_NAME_OFFSET, 16);
  }


}
