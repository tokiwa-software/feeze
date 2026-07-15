/*

This file is part of the Feeze scheduling analysis tool.

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

import dev.flang.util.ANY;

/*---------------------------------------------------------------------*/


/**
 * Data recoreded for the Feeze scheduling monitoring tool
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class Data extends ANY implements Offsets
{
  private MappedByteBuffer _b;
  int names_processed = 0;

  TreeMap<Integer, SystemUser> _usersMap = new TreeMap<>();
  ArrayList<SystemUser> _users = new ArrayList<>();

  TreeMap<Integer, SystemProcess> _processesMap = new TreeMap<>();
  ArrayList<SystemProcess> _processes = new ArrayList<>();

  TreeMap<Integer, SystemThread> _threadsMap = new TreeMap<>();
  ArrayList<SystemThread> _unsortedThreads = new ArrayList<>();
  ArrayList<SystemThread> _sortedThreads = new ArrayList<>();

  /**
   * Map from cpu id to Cpu
   */
  TreeMap<Integer, Cpu> _cpusMap = new TreeMap();

  /**
   * Cpus sorted by id, may be a subset of all available CPUs in case some were
   * never used.
   */
  ArrayList<Cpu> _cpus = new ArrayList<>();


  /**
   * CPU ids for which a Cpu instance has already been created and added to
   * _cpuMap/_cpus. Used during processNewData.
   */
  private BitSet _cpu_ids = new BitSet();


  ArrayList<Integer> _gaps = new ArrayList<>();


  Data(MappedByteBuffer b)
  {
    _b = b;
  }

  void close()
  {
    /* NYI: CLEANUP: This requires using java.lang.foreign.MemorySegment, there is no longer a proper way to unmap a MappedByteBuffer.
     *
    var cleaner = ((sun.nio.ch.DirecBuffer) _b).cleaner();
    if (cleaner != null)
      {
        cleaner.clean();
      }
     */
    _b = null;
  }

  int kind(int at)
  {
    return _b.get(entry_start_offset + at*ENTRY_SIZE + ENTRY_UNTIMED_KIND_OFFSET) & KIND_MASK;
  }

  /**
   * Is the event at given position a scheduler event (switch, waking, wakup)?
   *
   * @param at a (legal) index
   */
  boolean isSched(int at)
  {
    return ((1 << kind(at)) & ((1 << ENTRY_KIND_SCHED_SWITCH) |
                               (1 << ENTRY_KIND_SCHED_WAKING) |
                               (1 << ENTRY_KIND_SCHED_WAKEUP)   )) != 0;
  }

  /**
   * Does the event at given position come with a nanosecond timestamp?
   *
   * @param at a (legal) index
   */
  boolean isTimed(int at)
  {
    return ((1 << kind(at)) & ((1 << ENTRY_KIND_SCHED_SWITCH) |
                               (1 << ENTRY_KIND_SCHED_WAKING) |
                               (1 << ENTRY_KIND_SCHED_WAKEUP) |
                               (1 << ENTRY_KIND_USER_EVENT  ) |
                               (1 << ENTRY_KIND_GAP         )   )) != 0;
  }

  long ns(int at)
  {
    if (PRECONDITIONS) require
      (isTimed(at));

    var ns_and_kind = _b.getLong(entry_start_offset + at*ENTRY_SIZE + ENTRY_TIMED_NS_AND_KIND_OFFSET);

    // Only the upper 60 bits are used.
    //
    // NYI: HACK: Note that in case of an overflow in the ns value, we might see
    // a sudden drop from almost 2^59 to -2^59. This, however, is much less
    // likely than the representation of the time changing in the near future,
    // so I just ignore this for now.
    var ns = ns_and_kind >>> NS_RSHIFT;
    return ns;
  }

  SystemThread thread(int at, boolean old)
  {
    var tnum = old ? old_tnum(at)
                   : new_tnum(at);
    var res = _unsortedThreads.get(tnum);
    return res;
  }

  private int old_tnum(int at)
  {
    if (PRECONDITIONS) require
      (kind(at) == ENTRY_KIND_SCHED_SWITCH);

    return getUShort(at, ENTRY_SS_OLD_T_NUM_OFFSET);
  }

  private int new_tnum(int at)
  {
    if (PRECONDITIONS) require
      (kind(at) == ENTRY_KIND_SCHED_SWITCH);

    return getUShort(at, ENTRY_SS_NEW_T_NUM_OFFSET);
  }


  long byteSize()
  {
    return _b.getLong(0);
  }
  long usedBytes()
  {
    return entry_start_offset + unprocessedEntryCount()*ENTRY_SIZE;
  }
  long unprocessedEntryCount()
  {
    return _b.getLong(8);
  }


  int entryCount()
  {
    return names_processed;
  }


  SystemThread oldThreadAt(int at)
  {
    if (PRECONDITIONS) require
      (kind(at) == ENTRY_KIND_SCHED_SWITCH);

    return thread(at, true);
  }

  SystemThread newThreadAt(int at)
  {
    if (PRECONDITIONS) require
      (kind(at) == ENTRY_KIND_SCHED_SWITCH);

    return thread(at, false);
  }

  SystemThread causingThreadAt(int at)
  {
    if (PRECONDITIONS) require
      (((1 << kind(at)) & (1 << ENTRY_KIND_SCHED_WAKING |
                           1 << ENTRY_KIND_SCHED_WAKEUP  )) != 0);

    var tnum = getUShort(at, ENTRY_SW_CAUSING_T_NUM_OFFSET);
    return _unsortedThreads.get(tnum);
  }

  SystemThread affectedThreadAt(int at)
  {
    if (PRECONDITIONS) require
      (((1 << kind(at)) & (1 << ENTRY_KIND_SCHED_WAKING |
                           1 << ENTRY_KIND_SCHED_WAKEUP  )) != 0);

    var tnum = getUShort(at, ENTRY_SW_AFFECTED_T_NUM_OFFSET);
    return _unsortedThreads.get(tnum);
  }

  long nanosMin()
  {
    int at = 0;
    while (at < entryCount())
      {
        if (isTimed(at))
          {
            return ns(at);
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
        if (isTimed(at))
          {
            return ns(at);
          }
        at--;
      }
    return 0;
  }


  /**
   * Get the time at the given SCHED_SWITCH entry.
   */
  long nanosAtSwitch(int at)
  {
    if (PRECONDITIONS) require
      (isTimed(at));

    return ns(at);
  }


  /**
   * Get the time at or before given index.
   */
  long nanosAtOrBefore(int at)
  {
    while (!isTimed(at) && at > 0)
      {
        at--;
      }
    if (at == 0)
      {
        return nanosMin();
      }
    else
      {
        return nanosAtSwitch(at);
      }
  }

  byte getByte(int at, int off)
  {
    return _b.get(entry_start_offset + at*ENTRY_SIZE + off);
  }
  int getUShort(int at, int off)
  {
    return _b.getShort(entry_start_offset + at*ENTRY_SIZE + off) & 0xFFFF;
  }
  int getInt(int at, int off)
  {
    return _b.getInt(entry_start_offset + at*ENTRY_SIZE + off);
  }


  /**
   * Get name from zero or more entry of type ENTRY_KIND_MORE_CHARS following
   * at.
   *
   * @return the string taken from following more chars entries.
   */
  String getName(int at)
  {
    return getName(at, Integer.MIN_VALUE, 0);
  }


  /**
   * Get name from entry at using given off and maximum len[ght] and add zero or
   * more entry of type ENTRY_KIND_MORE_CHARS following at.
   *
   * @param at an entry index
   *
   * @param off an offset within an entry or undefined if len==0
   *
   * @param len number of bytes to take from entry at off...
   *
   * @return the string taken from entry at at given offset and following more
   * chars entries.
   */
  String getName(int at, int off, int len)
  {
    var l = 0;
    while (l < len && getByte(at, off+l) != 0)
      {
        l++;
      }
    var l0 = l;
    var more = at+1;
    while (more < unprocessedEntryCount() && kind(more) == ENTRY_KIND_MORE_CHARS)
      {
        var lm = 0;
        while (lm <  ENTRY_MC_STR_SIZE && getByte(more, ENTRY_MC_STR_OFFSET+lm) != 0)
          {
            lm++;
            l++;
          }
        more++;
      }
    var bs = new byte[l];
    var i = 0;
    while (i < len && getByte(at, off+i) != 0)
      {
        bs[i] = getByte(at, off+i);
        i++;
      }
    more = at+1;
    while (more < unprocessedEntryCount() && kind(more) == ENTRY_KIND_MORE_CHARS)
      {
        var lm = 0;
        while (lm <  ENTRY_MC_STR_SIZE && getByte(more, ENTRY_MC_STR_OFFSET+lm) != 0)
          {
            bs[i] = getByte(more, ENTRY_MC_STR_OFFSET+lm);
            lm++;
            i++;
          }
        more++;
      }
    return StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bs) /* NYI: could we do  b.subrange(..)? */).toString();
  }

  /**
   * Get the CPU id at the given SCHED_SWITCH entry
   *
   * @param at the index of the SCHED_SWITCH entry.
   *
   * @return the CPU id
   */
  int cpu_id(int at)
  {
    if (PRECONDITIONS) require
      (isSched(at));

    return getUShort(at, ENTRY_SS_CPU_ID_OFFSET);
  }

  synchronized void processNewData()
  {
    var num_entries = (int) unprocessedEntryCount();
    if (names_processed < num_entries)
      {
        while (names_processed < num_entries)
          {
            switch (kind(names_processed))
              {
              case ENTRY_KIND_UNUSED: break;
              case ENTRY_KIND_USER:
                {
                  var uid  = getInt(names_processed, ENTRY_U_UID_OFFSET);
                  var name = getName(names_processed);
                  var user = new SystemUser(this, uid, name, _users.size());
                  _usersMap.put(uid, user);
                  _users.add(user);
                  break;
                }
              case ENTRY_KIND_PROCESS:
                {
                  var pid  = getInt(names_processed, ENTRY_P_PID_OFFSET);
                  var uid  = getInt(names_processed, ENTRY_P_UID_OFFSET);
                  var name = getName(names_processed);
                  var user = _usersMap.get(uid);
                  if (user == null)
                    {
                      System.err.println("**** unknown user "+uid);
                      user = new SystemUser(this, uid, "unknown", _users.size());
                      _usersMap.put(uid, user);
                      _users.add(user);
                    }
                  var p = new SystemProcess(pid, uid, name, _processes.size(), user);
                  _processesMap.put(pid, p);
                  _processes.add(p);
                  break;
                }
              case ENTRY_KIND_THREAD:
                {
                  var tid  = getInt(names_processed, ENTRY_T_TID_OFFSET);
                  var pid  = getInt(names_processed, ENTRY_T_PID_OFFSET);
                  var t = new SystemThread(this, tid, pid, _processesMap.get(pid));
                  _threadsMap.put(tid, t);
                  t._originalNumber = _unsortedThreads.size();
                  _unsortedThreads.add(t);
                  break;
                }
              case ENTRY_KIND_SCHED_SWITCH:
                {
                  var ot = thread(names_processed, true);
                  var nt = thread(names_processed, false);
                  ot.addAction(names_processed);
                  nt.addAction(names_processed);
                  var cpu_id = cpu_id(names_processed);
                  if (cpu_id >= 0)
                    {
                      if (!_cpu_ids.get(cpu_id))
                        {
                          _cpu_ids.set(cpu_id);
                          var cpu = new Cpu(this, cpu_id);
                          _cpusMap.put(cpu_id, cpu);
                          _cpus.add(cpu);
                        }
                      var cpu = _cpusMap.get(cpu_id);
                      cpu.addAction(names_processed);
                    }
                  break;
                }
              case ENTRY_KIND_SCHED_WAKING:
              case ENTRY_KIND_SCHED_WAKEUP:
                {
                  var nt = affectedThreadAt(names_processed);
                  nt.addAction(names_processed);
                  var cpu_id = cpu_id(names_processed);
                  if (cpu_id >= 0)
                    {
                      if (!_cpu_ids.get(cpu_id))
                        {
                          _cpu_ids.set(cpu_id);
                          var cpu = new Cpu(this, cpu_id);
                          _cpusMap.put(cpu_id, cpu);
                          _cpus.add(cpu);
                        }
                    }
                  break;
                }
              case ENTRY_KIND_USER_EVENT:
                {
                  var t = userEventThread(names_processed);
                  if (t != null)
                    {
                      t.addAction(names_processed);
                    }
                  break;
                }
              case ENTRY_KIND_THREAD_NAME:
                {
                  var num = getUShort(names_processed, ENTRY_TN_T_NUM_OFFSET);
                  if (num >= 0 && num < _unsortedThreads.size())
                    {
                      var t = _unsortedThreads.get(num);
                      t.addAction(names_processed);
                    }
                  else
                    {
                      System.err.println("*** illegal thread number "+num+" in ENTRY_KIND_THREAD_NAME for entry #"+names_processed);
                    }
                  break;
                }
              case ENTRY_KIND_GAP:
                {
                  _gaps.add(names_processed);
                  break;
                }
              case ENTRY_KIND_MORE_CHARS:
                {
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
    for (var t : _unsortedThreads)
      {
        _sortedThreads.add(t);
      }
    _sortedThreads.sort((t1,t2) ->
                  {
                    var u1 = t1._p._user._num;
                    var u2 = t2._p._user._num;
                    var p1 = t1._p._num;
                    var p2 = t2._p._num;
                    var i1 = t1._tid;
                    var i2 = t2._tid;
                    return u1 != u2 ? Integer.compare(u1, u2) :
                           p1 != p2 ? Integer.compare(p1, p2)
                                    : Integer.compare(i1, i2);
                  });
    _cpus.sort((c1,c2) -> Integer.compare(c1._id, c2._id));
    for (var n = 0; n < _sortedThreads.size(); n++)
      {
        _sortedThreads.get(n)._displayedNumber = n;
      }
  }

  FeezeThread userEventThread(int at)
  {
    var num = getUShort(at, ENTRY_UE_T_NUM);
    if (num >= 0 && num < _unsortedThreads.size())
      {
        return _unsortedThreads.get(num);
      }
    else
      {
        return null;
      }
  }



  String threadName(int at)
  {
    if (PRECONDITIONS) require
      (kind(at) == ENTRY_KIND_THREAD_NAME);

    return getName(at, ENTRY_TN_NAME_OFFSET, ENTRY_TN_NAME_LENGTH);
  }


  int numCpus()
  {
    return _cpus.size();
  }

  Cpu cpu(int i)
  {
    if (PRECONDITIONS) require
      (i < numCpus());

    return _cpus.get(i);
  }


  int userEventColor(int at)
  {
    if (PRECONDITIONS) require
      (kind(at) == ENTRY_KIND_USER_EVENT);

    return _b.get(Feeze.entry_start_offset + at*ENTRY_SIZE + ENTRY_UE_COLOR_BYTE) & 0xff;
  }


  String userEventMsg(int at)
  {
    if (PRECONDITIONS) require
      (kind(at) == ENTRY_KIND_USER_EVENT);

    return getName(at, ENTRY_UE_MSG, ENTRY_UE_MSG_SIZE);
  }



}
