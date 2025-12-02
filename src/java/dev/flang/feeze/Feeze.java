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
 * Copyright (c) 2025, Tokiwa Software GmbH, Germany
 *
 * Java source code of class dev.flang.feeze.Feeze
 *
 *---------------------------------------------------------------------*/


package dev.flang.feeze;

import java.io.File;
import java.io.IOException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;

import java.nio.file.StandardOpenOption;

import java.nio.channels.FileChannel;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeMap;

/*---------------------------------------------------------------------*/


/**
 * Feeze is the main class of the Feeze scheduling monitoring tool
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Feeze implements Offsets
{

  static Data _data = null;

  public static final String SHARED_MEM_NAME = "/tmp/feeze_events_recorder_data";
  public static final long   SHARED_MEM_SIZE = 64*1024*1024;


  static MappedByteBuffer b;

  static FeezeDataFrame _dataFrame;


  public static void main(String[] args)
  {
    var done = false;
    while (true)
      {
        try
          {
            var f = new File(SHARED_MEM_NAME);
            try (var channel = FileChannel.open(f.toPath(), StandardOpenOption.READ))
              {
                long l;
                do
                  {
                    var b0 = channel.map(FileChannel.MapMode.READ_ONLY, 0, 4096);
                    b0.order(ByteOrder.LITTLE_ENDIAN);
                    l = b0.getLong(0);
                    var eso = b0.getInt(16);
                    var es  = b0.getInt(20);
                    if (eso != entry_start_offset)
                      {
                        System.err.println("*** entry start offset is "+eso+" expected "+entry_start_offset);
                        System.exit(1);
                      }
                    if (es != ENTRY_SIZE)
                      {
                        System.err.println("*** entry size is "+es+" expected "+ENTRY_SIZE);
                        System.exit(1);
                      }
                  }
                while (l == 0);
                b = channel.map(FileChannel.MapMode.READ_ONLY, 0, l);
                b.order(ByteOrder.LITTLE_ENDIAN);
                _data = new Data(b);
                _data.processNewData();
                if (_dataFrame == null)
                  {
                    _dataFrame = new FeezeDataFrame(_data);
                  }
                done = b.get(24)!=0;
                try
                  {
                    Thread.sleep(done ? Long.MAX_VALUE : 1000);
                  }
                catch (InterruptedException e)
                  {
                  }
              }
          }
        catch (IOException e)
          {
            System.out.println(e);
          }
        try
          {
            Thread.sleep(1000);
          }
        catch (InterruptedException e)
          {
          }
      }
  }
  static int kind(int at)
  {
    return b.get(entry_start_offset + at*ENTRY_SIZE + ENTRY_KIND_OFFSET) & 0xff;
  }
  static int old_pid(int at)
  {
    return b.getInt(entry_start_offset + at*ENTRY_SIZE + ENTRY_SS_OLD_PID_OFFSET);
  }
  static void old_name(int at, byte[] bs)
  {
    for (var i = 0; i<16; i++)
      {
        bs[i] = b.get(entry_start_offset + at*ENTRY_SIZE + ENTRY_SS_OLD_NAME_OFFSET+i);
      }
  }

  static int new_pid(int at)
  {
    return b.getInt(entry_start_offset + at*ENTRY_SIZE + ENTRY_SS_NEW_PID_OFFSET);
  }
  static void new_name(int at, byte[] bs)
  {
    for (var i = 0; i<16; i++)
      {
        bs[i] = b.get(entry_start_offset + at*ENTRY_SIZE + ENTRY_SS_NEW_NAME_OFFSET+i);
      }
  }
  static long ns(int at)
  {
    return b.getLong(entry_start_offset + at*ENTRY_SIZE + ENTRY_SS_NS_OFFSET);
  }

}


class Name implements Comparable<Name>
{
  int _yIndex;
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

class SystemProcess
{
  int _pid;
  String _name;
  int _num;
  ArrayList<SystemThread> _threads = new ArrayList<>();

  SystemProcess(int pid,
                String name,
                int num)
  {
    _pid = pid;
    _name = name;
    _num = num;
  }

  void addThread(SystemThread t)
  {
    _threads.add(t);
  }

  public String toString()
  {
    return ""+_pid+" "+_name;
  }
}

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


/**
 * NYI: CLEANUP:  Move to separate source file and add comments!
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
        n._yIndex = _namesList.size();
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
      default: throw new Error("No nanos available for kind "+at);
      }
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
