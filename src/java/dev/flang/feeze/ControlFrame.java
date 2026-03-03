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
import java.awt.GridLayout;
import java.awt.event.KeyEvent;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.GroupLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import dev.flang.util.Threads;

/*---------------------------------------------------------------------*/


/**
 * ControlFrame opens a frame to control Feeze data recording and display.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class ControlFrame
{


  JTextField _sharedMemName;
  JButton _startRecorder, _showData;
  JTextArea _recorderOutput;


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
    var frame = new JFrame("Feeze Control");

    _sharedMemName = new JTextField(Feeze.SHARED_MEM_NAME);
    _startRecorder = button(" start local recorder ", KeyEvent.VK_S, "start local recording service, requires superuser status");
    _showData = button(" show data ", KeyEvent.VK_D, "show recorded data");
    if (!Feeze.sharedMemExists())
      {
        _showData.setEnabled(false);
      }
    _recorderOutput = new JTextArea(77,10);
    _recorderOutput.setText("");
    new Thread(()->
               {
                 while (true)
                   {
                     Threads.sleep(1000);
                     var ex = Feeze.sharedMemExists();
                     if (ex != _showData.isEnabled())
                       {
                         _showData.setEnabled(ex);
                       }
                   }
               }
               )
    { {
      setDaemon(true);
      start();
    } };
    var ro = new JScrollPane(_recorderOutput);

    var panel = new JPanel();
    var layout = new GroupLayout(panel);
    layout.setAutoCreateGaps(true);
    layout.setAutoCreateContainerGaps(true);
    panel.setLayout(layout);  // NYI: needed?, seems redundant
    layout.setHorizontalGroup
      (layout.createParallelGroup(GroupLayout.Alignment.LEADING)
       .addGroup(layout.createSequentialGroup()
                 .addComponent(_sharedMemName)
                 .addComponent(_startRecorder)
                 .addComponent(_showData))
       .addComponent(ro));
    layout.setVerticalGroup
      (layout.createSequentialGroup()
       .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                 .addComponent(_sharedMemName)
                 .addComponent(_startRecorder)
                 .addComponent(_showData))
       .addComponent(ro));
    var content = panel;
    content.setOpaque(true);
    frame.setContentPane(content);
    frame.pack();
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setVisible(true);

    var _listener = new ControlListener(this);
  }

}
