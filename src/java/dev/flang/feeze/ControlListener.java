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
 * Java source code of class dev.flang.feeze.ControlListener
 *
 *---------------------------------------------------------------------*/


package dev.flang.feeze;

/*---------------------------------------------------------------------*/

import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;

import java.util.LinkedList;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import dev.flang.util.Threads;

/*---------------------------------------------------------------------*/


/**
 * ControlListener TBW
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class ControlListener
  implements ActionListener
{


  /*----------------------------  constants  ----------------------------*/


  static String DIALOG_HEADER = "Feeze Controller";


  /*----------------------------  variables  ----------------------------*/


  /**
   * The Control
   */
  final ControlFrame _control;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create this listener.
   *
   * @param startRecorder, showData buttons
   */
  ControlListener(ControlFrame control)
  {
    this._control = control;
    addButton(control._startRecorder,"start recorder");
    addButton(control._showData,"show data");
    //    p.addMouseListener(this);
    //_startRecorder.addActionListener(this);
    //    manager.addKeyEventDispatcher(new Keys());
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * addButton is a helper routine for the constructor to set the action command
   * and the listeners for a button.  In case the button is null, a dummy button
   * will be created.
   *
   * @param b the button or null.
   *
   * @param actionCmd the action to be added to b.
   *
   * @return b or newly created dummy button, never null.
   */
  private JButton addButton(JButton b, String actionCmd)
  {
    if (b != null)
      {
        b.setActionCommand(actionCmd);
        b.addActionListener(this);
        //        b.addMouseListener(this);
      }
    else
      {
        b = new JButton("", null);
      }
    return b;
  }


  /**
   * mouseEntered
   *
   * @param e the mouse event.
   */
  //  @Override
  //  public void mouseEntered(MouseEvent e) { }


  /**
   * mouseExited
   *
   * @param e the mouse event.
   */
  //  @Override
  //  public void mouseExited(MouseEvent e) { }


  /**
   * mouseMoved
   *
   * @param e the mouse event.
   */
  //  @Override
  //  public void mouseMoved(MouseEvent e) {  }


  /**
   * mousePressed handle the mouse pressed event, i.e., signal to
   * _autorepeat to start repeat mode.
   *
   * @param e the mouse event.
   */
  /*
  @Override
  public void mousePressed(MouseEvent e)
  {
    var c = e.getComponent();
    if (e == _startRecorder)
      {
        System.out.println("START RECORDER PRESSED!");
      }
  }
  */


  LinkedList<String> asyncRead(InputStream in, Object lock)
  {
    var input = new BufferedReader(new InputStreamReader(in));
    var inputQueue = new LinkedList<String>();
    new Thread(()->
               {
                 var ok = true;
                 while (ok)
                   {
                     String s;
                     try
                       {
                         s = input.readLine();
                         if (s == null)
                           {
                             s = "*** EOF";
                             ok = false;
                           }
                       }
                     catch (IOException ioe)
                       {
                         s = ioe.toString();
                         ok = false;
                       }
                     synchronized (lock)
                       {
                         inputQueue.add(s);
                         lock.notify();
                       }
                   }
               })
    { { setDaemon(true); start(); } };
    return inputQueue;
  }


  /**
   * actionPerformed handles click on a button.
   *
   * @param e the associated event
   */
  @Override
  public void actionPerformed(ActionEvent e)
  {
    System.out.println("ACTION: "+e.getActionCommand());
    switch (e.getActionCommand())
      {
      case "start recorder" ->
        {
          new Thread(()->
            {
              try
                {
                  //              Runtime.getRuntime().exec(new String[] { "/usr/bin/pkexec", "echo", "Hi" });
                  //Runtime.getRuntime().exec(new String[] { "/usr/bin/pkexec", "ls" });
                  var p = Path.of(Feeze.FEEZE_HOME).resolve("bin").resolve("feeze_recorder").normalize().toAbsolutePath();
                  System.out.println("path is "+p);
                  var p1 = new ProcessBuilder("/usr/bin/pkexec", p.toString()).start();
                  System.out.println("*** Started: "+p1);
                  var lock = new Object();
                  var input = asyncRead(p1.getInputStream(), lock);
                  var error = asyncRead(p1.getErrorStream(), lock);

                  while (p1.isAlive())
                    {
                      synchronized (lock)
                        {
                          while (input.isEmpty() && error.isEmpty() && p1.isAlive())
                            {
                              Threads.wait(lock);
                            }
                          while (!input.isEmpty())
                            {
                              var s = input.removeFirst();
                              // System.out.println("RECORDER: "+s);
                              _control._recorderOutput.append(s+"\n");
                            }
                          while (!error.isEmpty())
                            {
                              var s = error.removeFirst();
                              //System.out.println("RECORDER: *** ERROR *** "+s);
                              _control._recorderOutput.append("ERR: "+s+"\n");
                            }
                        }
                    }
                  p1.waitFor();
                  System.out.println("*** FINISHED: "+p1);
                  if (false)
                    {
                      var p2 = new ProcessBuilder("/usr/bin/pkexec", "pwd").inheritIO().start();
                      p2.waitFor();
                      var p3 = new ProcessBuilder("/usr/bin/pkexec", "env").inheritIO().start();
                      p3.waitFor();
                      JOptionPane.showMessageDialog(_control._startRecorder, "ok", DIALOG_HEADER, JOptionPane.INFORMATION_MESSAGE, null /* Icon */);
                    }
                }
              catch (IOException ioe)
                {
                  JOptionPane.showMessageDialog(_control._startRecorder, ioe.getMessage(), DIALOG_HEADER, JOptionPane.ERROR_MESSAGE, null /* Icon */);
                }
              catch (InterruptedException ie)
                {
                  JOptionPane.showMessageDialog(_control._startRecorder, "Interrupted! "+ie.getMessage(), DIALOG_HEADER, JOptionPane.ERROR_MESSAGE, null /* Icon */);
                }
            }
                     ).start();
        }
      case "show data" ->
        {
          new Thread(()->Feeze.showData()).start();
        }
      }
  }

}
