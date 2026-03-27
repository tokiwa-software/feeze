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
 * Java source code of class dev.flang.feeze.FeezeDataFrame
 *
 *---------------------------------------------------------------------*/


package dev.flang.feeze;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;

/*---------------------------------------------------------------------*/


/**
 * FeezeDataFrame opens the main frame to display recorded scheduler data.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class FeezeDataFrame extends JFrame
{

  boolean _closed = false;


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

  FeezeDataFrame(Data data)
  {
    super("Feeze Scheduling Data");
    javax.swing.SwingUtilities.invokeLater(()->
      {
        var b1 = button("🠊🠈", KeyEvent.VK_C, "compress time axis");
        var b2 = button("🠈🠊", KeyEvent.VK_X, "expand time axis");
        var b3 = button("+zoom", KeyEvent.VK_Z, "zoom in");
        var b4 = button("-zoom", KeyEvent.VK_O, "zoom out");
        var panorama = new SchedulingPanorama(data, b1, b2, b3, b4);
        var controls = new JPanel(new GridLayout(1,0));
        controls.add(b2);
        controls.add(b1);
        controls.add(b3);
        controls.add(b4);
        final var content = new JPanel(new BorderLayout());
        content.add(panorama.scroller(0, panorama.dataHeight()), BorderLayout.CENTER);
        content.add(controls, BorderLayout.SOUTH);
        content.setOpaque(true);

        setContentPane(content);
        pack();
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter()
          {
            @Override
            public void windowClosing(WindowEvent e)
            {
              closeThisFrame();
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
                  closeThisFrame();
                }
              else if (key.getKeyChar() == 'q' - 0x60)
                {
                  Feeze.askToQuit(FeezeDataFrame.this);
                }
              else if (false)
                {
                  System.out.println("typed: "+key.getKeyCode()+" "+key.getExtendedKeyCode()+" "+key.getKeyChar()+" "+((int)key.getKeyChar())+" w:"+('w'-0x90));
                }
            }
          });

        setVisible(true);
        var ignore = Feeze._openDataFrames_.incrementAndGet();
      });
  }

  synchronized void closeThisFrame()
  {
    if (!_closed)
      {
        _closed = true;

        // NYI: HACK: Must properly shutdown all threads serving this data frame!
        setVisible(false);
        dispose();
        var ignore = Feeze._openDataFrames_.decrementAndGet();
      }
  }


}
