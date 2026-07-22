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
import java.nio.file.Files;
import java.nio.file.Path;

import java.nio.channels.FileChannel;

import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

import dev.flang.util.ANY;
import dev.flang.util.Threads;

/*---------------------------------------------------------------------*/


/**
 * Feeze is the main class of the Feeze scheduling monitoring tool
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Feeze extends ANY implements Offsets
{


  /*----------------------------  constants  ----------------------------*/


  static String DIALOG_HEADER = "Feeze Controller";

  /*------------------------------  fields  -----------------------------*/


  static Data _data = null;

  public static Path SHARED_MEM_PATH = Path.of("/tmp/feeze_events_recorder_data");
  public static String SHARED_MEM_NAME = SHARED_MEM_PATH.toString();
  public static boolean sharedMemExists(String shMemFileName)
  {
    return Files.exists(Path.of(shMemFileName));
  }
  public static final long   SHARED_MEM_SIZE = 64*1024*1024;

  public static final String FEEZE_HOME = System.getProperties().getProperty("feeze.home",".");


  /**
   * Counter for open data frames to make sure we do not quit without asking if
   * they should be closed.
   */
  static AtomicInteger _openDataFrames_ = new AtomicInteger(0);



  public static void main(String[] args)
  {
    installIcon();
    var c = new ControlFrame();
  }

  static void showData(String shMemFileName)
  {
    FeezeDataFrame dataFrame = null;
    var done = false;
    while (!done)
      {
        try
          {
            var f = new File(shMemFileName);
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
                var b = channel.map(FileChannel.MapMode.READ_ONLY, 0, l);
                b.order(ByteOrder.LITTLE_ENDIAN);
                _data = new Data(b);
                _data.processNewData();
                if (dataFrame == null)
                  {
                    dataFrame = new FeezeDataFrame(_data);
                  }
                done = b.get(24)!=0;
                if (!done)
                  {
                    Threads.sleep(1000);
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


  static void askToQuit(JFrame frame)
  {
    if (_openDataFrames_.get() == 0 ||
        JOptionPane.showConfirmDialog(frame,
                                      "Exit feeze and close all data windows?",
                                      Feeze.DIALOG_HEADER,
                                      JOptionPane.YES_NO_OPTION) == 0)
      {
        System.exit(0);
      }
  }


  /**
   * user-local directory for application .desktop files for Ubuntu (and other
   * distros, I hope):
   */
  static String LOCAL_APPS_DIR = ".local/share/applications/";


  /**
   * Feeze .desktop file name that will contain the icon
   *
   * @see https://specifications.freedesktop.org/desktop-entry/latest/
   */
  static String LOCAL_ICON_FILE = "dev-flang-feeze-Feeze.desktop";


  /**
   * Feeze application name to be displayed by desktop
   */
  static String FEEZE_APP_NAME = "Feeze — Scheduling Tracer";


  /**
   * Install application Icon for Feeze
   */
  static void installIcon()
  {
    try
      {
        var d = Path.of(System.getProperty("user.home")).resolve(LOCAL_APPS_DIR);
        var p = d.resolve(LOCAL_ICON_FILE);
        if (Files.exists(d) && !Files.exists(p))
          {
            var iconfile = Path.of(System.getProperty("feeze.home"))
              .normalize()
              .resolve("icon.svg");
            Files.writeString(p,
                              String.format("""
                                            [Desktop Entry]
                                            Encoding=UTF-8
                                            Version=1.0
                                            Type=Application
                                            Terminal=false
                                            Name=%s
                                            Icon=%s
                                            """,
                                            FEEZE_APP_NAME,
                                            iconfile
                                            ));
          }
      }
    catch (IOException io)
      {
        System.err.println("*** warning: Attempt to create " + LOCAL_ICON_FILE + " in " + LOCAL_APPS_DIR + " resulted in " + io);
      }
  }

}
