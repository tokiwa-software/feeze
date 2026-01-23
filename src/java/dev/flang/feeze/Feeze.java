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
  static int count(int at)
  {
    return b.getInt(entry_start_offset + at*ENTRY_SIZE + ENTRY_SS_COUNT_OFFSET);
  }

}
