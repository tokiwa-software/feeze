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
import javax.swing.JPanel;
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

    var t1 = new JTextField(Feeze.SHARED_MEM_NAME);
    var b1 = button(" start local recorder ", KeyEvent.VK_S, "start local recording service, requires superuser status");
    var b2 = button(" show data ", KeyEvent.VK_D, "show recorded data");
    if (!Feeze.sharedMemExists())
      {
        b2.setEnabled(false);
      }
    new Thread(()->
               {
                 while (true)
                   {
                     Threads.sleep(1000);
                     var ex = Feeze.sharedMemExists();
                     if (ex != b2.isEnabled())
                       {
                         b2.setEnabled(ex);
                       }
                   }
               }
               )
    { {
      setDaemon(true);
      start();
    } };
    var controls = new JPanel(new GridLayout(1,0));
    controls.add(t1);
    controls.add(b1);
    controls.add(b2);
    final var content = new JPanel(new BorderLayout());
    content.add(controls, BorderLayout.NORTH);
    content.setOpaque(true);
    frame.setContentPane(content);
    frame.pack();
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setVisible(true);

    var _listener = new ControlListener(b1, b2);


    System.out.println("HAVE FRAME!");
  }

}
