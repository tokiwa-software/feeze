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
import java.awt.Dimension;
import java.awt.GridLayout;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
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
public class ControlFrame
{


  JTextField _sharedMemName;
  JButton _startRecorder, _record, _showData;
  JTextArea _recorderOutput; // Using JTextPane could allow text attributes like color (eg., red for stderr)


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
    javax.swing.SwingUtilities.invokeLater(()->
      {
        var frame = new JFrame("Feeze Control");

        var shMemNameLabel = new JLabel("Shared Memory File:");
        _sharedMemName = new JTextField(Feeze.SHARED_MEM_NAME);
        _sharedMemName.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
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
            while (true)
              {
                Threads.sleep(1000);  // NYI: CLEANUP: DO this whenever the input text changes or when the file changes!
                var ex = Feeze.sharedMemExists(_sharedMemName.getText());
                if (ex != _showData.isEnabled())
                  {
                    _showData.setEnabled(ex);
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
                               .addComponent(_sharedMemName))
                     .addComponent(_startRecorder)
                     .addComponent(_record)
                     .addComponent(_showData))
           .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                     .addComponent(rolabel))
           .addComponent(ro));
        layout.setVerticalGroup
          (layout.createSequentialGroup()
           .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                     .addGroup(layout.createSequentialGroup()
                               .addComponent(shMemNameLabel)
                               .addComponent(_sharedMemName))
                     .addComponent(_startRecorder)
                     .addComponent(_record)
                     .addComponent(_showData))
           .addComponent(rolabel)
           .addComponent(ro));
        var content = panel;
        content.setOpaque(true);
        frame.setContentPane(content);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter()
          {
            @Override
            public void windowClosing(WindowEvent e)
            {
              if (JOptionPane.showConfirmDialog(frame,
                                                "Exit feeze and close all data windows?",
                                                Feeze.DIALOG_HEADER,
                                            JOptionPane.YES_NO_OPTION) == 0)
                {
                  System.exit(0);
                }
            }
          });

        /* NYI: key events do not work, check why!

        (!true ? panel : true ? frame : _recorderOutput)
          .addKeyListener(new KeyAdapter() {
              @Override
              public void keyPressed(KeyEvent e) {
                System.out.println("Key pressed: " + e.getKeyChar());
              }
              @Override
              public void keyReleased(KeyEvent e) {
                System.out.println("Key pressed: " + e.getKeyChar());
              }
              @Override
              public void keyTyped(KeyEvent e) {
                System.out.println("Key pressed: " + e.getKeyChar());
              }
            }
            );
        */

        frame.setVisible(true);

        var _listener = new ControlListener(this);
      });
  }

}
