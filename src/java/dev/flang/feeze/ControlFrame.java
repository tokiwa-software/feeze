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
 * Java source code of class dev.flang.feeze.ControlFrame
 *
 *---------------------------------------------------------------------*/


package dev.flang.feeze;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.nio.ByteOrder;

import java.nio.file.StandardOpenOption;

import java.nio.channels.FileChannel;

import java.io.File;
import java.io.IOException;

import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.JTextField;

import dev.flang.util.Threads;

/*---------------------------------------------------------------------*/


/**
 * ControlFrame opens a frame to control Feeze data recording and display.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class ControlFrame extends JFrame
{


  static int INITIAL_SHARED_MEM_SIZE = 64*1024*1024;

  JTextField _sharedMemName;
  JTextField _sharedMemSize;
  JProgressBar _usedMemBar;
  JButton _startRecorder, _record, _showData;
  JTextArea _recorderOutput; // Using JTextPane could allow text attributes like color (eg., red for stderr)

  Data _data = null;
  String _dataName = null;



  long shMemSize()
  {
    var result = (long) -1;
    var s = _sharedMemSize.getText();
    var f = (long) 1;
    if      (s.endsWith("GB")) { f = 1024*1024*1024; s = s.substring(0,s.length()-2); }
    else if (s.endsWith("MB")) { f =      1024*1024; s = s.substring(0,s.length()-2); }
    else if (s.endsWith("KB")) { f =           1024; s = s.substring(0,s.length()-2); }
    try
      {
        var l = Long.parseLong(s);
        if (Long.MAX_VALUE / f <= l)
          { // overflow!
            System.err.println("overflow: "+f+"*"+l);
          }
        else
          {
            var res = f * l;
            if ((res & 4095) == 0 &&
                res >= 4096 &&
                res < ((long) 1 << 31))
              {
                result = res;
              }
            else
              {
                System.err.println("not ok: "+res);
              }
          }
      }
    catch (NumberFormatException e)
      {
        // ignore, -1 will be returned.
      }
    return result;
  }


  /**
   * Helper to create a JButton with given text, KeyEvent and tool tip.
   */
  private static JButton button(String text, int key, String toolTip)
  {
    var res = new JButton(text, null);
    res.setMnemonic(key);
    res.setToolTipText(toolTip);
    return res;
  }

  ControlFrame()
  {
    super("Feeze Control");

    javax.swing.SwingUtilities.invokeLater(()->
      {
        var shMemNameLabel = new JLabel("Shared Memory File:");
        _sharedMemName = new JTextField(Feeze.SHARED_MEM_NAME);
        _sharedMemName.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));

        var shMemSizeLabel = new JLabel("Shared Memory Size (KB/MB):");
        _sharedMemSize = new JTextField(""+(INITIAL_SHARED_MEM_SIZE/1024/1024)+"MB");
        _sharedMemSize.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));

        var usedMemLabel = new JLabel("Used Memory:");
        _usedMemBar   = new JProgressBar(0, INITIAL_SHARED_MEM_SIZE);
        _usedMemBar.setValue(0);
        _usedMemBar.setStringPainted(true);

        _startRecorder = button("start local recorder", KeyEvent.VK_S, "start local recording service, requires superuser status");
        _record = button("record", KeyEvent.VK_R, "start local recording service, requires superuser status");
        _record.setEnabled(false);
        _showData = button("show data", KeyEvent.VK_D, "show recorded data");

        if (!Feeze.sharedMemExists(_sharedMemName.getText()))
          {
            _showData.setEnabled(false);
          }
        _recorderOutput = new JTextArea(10,77);
        _recorderOutput.setText("");
        Threads.inDaemon(()->
          {
            long shm = -2;
            while (true)
              {
                Threads.sleep(1000);  // NYI: CLEANUP: DO this whenever the input text changes or when the file changes!
                var ex = Feeze.sharedMemExists(_sharedMemName.getText());
                if (ex != _showData.isEnabled())
                  {
                    _showData.setEnabled(ex);
                  }
                if (ex)
                  {
                    var fb = _sharedMemName.getText();
                    if (_data == null || !fb.equals(_dataName))
                      {
                        if (_data != null)
                          {
                            _data.close();
                            _data = null;
                          }
                        var f = new File(_sharedMemName.getText());
                        try (var channel = FileChannel.open(f.toPath(), StandardOpenOption.READ))
                          {
                            // NYI: CLEANUP: Use java.lang.foreign.MemorySegment instead!
                            var b = channel.map(FileChannel.MapMode.READ_ONLY, 0, 4096);
                            b.order(ByteOrder.LITTLE_ENDIAN);

                            _data = new Data(b);
                          }
                        catch (IOException ioe)
                          {
                            // NYI: show ioe in some status line
                          }
                      }
                  }
                else
                  {
                    if (_data != null)
                      {
                        _data.close();
                        _data = null;
                          }
                  }
                var d = _data;
                var used0 = _usedMemBar.getValue();
                var str0  = _usedMemBar.getString();
                var used  = used0;
                var str   = str0;
                if (d != null)
                  {
                    used = (int) d.usedBytes();
                    if (used != used0)
                      {
                        var sz = d.byteSize();
                        if (sz > 9*1024*1024)
                          {
                            str = ""+used/1024/1024+"MB/"+sz/1024/1024+"MB "+(used/(sz/100))+"%";
                          }
                        else if (sz > 9*1024)
                          {
                            str = ""+used/1024+"KB/"+sz/1024+"KB "+(used/(sz/100))+"%";
                          }
                        else
                          {
                            str = ""+used+"/"+sz+" "+(used/(sz/100))+"%";
                          }
                      }
                  }
                else
                  {
                    used = 0;
                    str = "--";
                  }
                if (used != used0 || !str.equals(str0))
                  {
                    _usedMemBar.setValue(used);
                    _usedMemBar.setString(str);
                  }
                var new_shm = shMemSize();
                if (shm != new_shm)
                  {
                    shm = new_shm;
                    _sharedMemSize.setBackground(shm == -1 ? Color.PINK : Color.white);
                  }
              }
          });
        var rolabel = new JLabel("recorder output:");
        var ro = new JScrollPane(_recorderOutput);

        var panel = new JPanel();
        var layout = new GroupLayout(panel);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);
        panel.setLayout(layout);  // NYI: needed?, seems redundant
        layout.setHorizontalGroup
          (layout.createParallelGroup(GroupLayout.Alignment.LEADING)
           .addGroup(layout.createSequentialGroup()
                     .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                               .addComponent(shMemNameLabel)
                               .addComponent(shMemSizeLabel)
                               .addComponent(usedMemLabel  ))
                     .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                               .addComponent(_sharedMemName)
                               .addComponent(_sharedMemSize)
                               .addComponent(_usedMemBar   ))
                     .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING, false)
                               .addComponent(_startRecorder, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                               .addComponent(_record,        GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                               .addComponent(_showData,      GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
           .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                     .addComponent(rolabel))
           .addComponent(ro));
        layout.setVerticalGroup
          (layout.createSequentialGroup()
           .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                     .addGroup(layout.createSequentialGroup()
                               .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                         .addComponent(shMemNameLabel)
                                         .addComponent(_sharedMemName))
                               .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                         .addComponent(shMemSizeLabel)
                                         .addComponent(_sharedMemSize))
                               .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                         .addComponent(usedMemLabel)
                                         .addComponent(_usedMemBar)))
                     .addGroup(layout.createSequentialGroup()
                               .addComponent(_startRecorder)
                               .addComponent(_record)
                               .addComponent(_showData))
                     )
           .addComponent(rolabel)
           .addComponent(ro));
        var content = panel;
        content.setOpaque(true);
        setContentPane(content);
        pack();
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter()
          {
            @Override
            public void windowClosing(WindowEvent e)
            {
              Feeze.askToQuit(ControlFrame.this);
            }
          });
        setFocusable(true);
        addKeyListener(new KeyListener()
          {
            @Override public void keyPressed(KeyEvent key) { }
            @Override public void keyReleased(KeyEvent key) { }
            @Override public void keyTyped(KeyEvent key) {
              if (key.getKeyChar() == 'w' - 0x60)
                {
                  Feeze.askToQuit(ControlFrame.this);
                }
              else if (key.getKeyChar() == 'q' - 0x60)
                {
                  Feeze.askToQuit(ControlFrame.this);
                }
              else if (false)
                {
                  System.out.println("typed: "+key.getKeyCode()+" "+key.getExtendedKeyCode()+" "+key.getKeyChar()+" "+((int)key.getKeyChar())+" w:"+('w'-0x90));
                }
            }
          });
        setVisible(true);

        var _listener = new ControlListener(this);
      });
  }

}
