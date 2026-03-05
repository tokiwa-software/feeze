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
import java.io.BufferedWriter;
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


  BufferedWriter _recorderInput;


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
    addButton(control._record,"record");
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


  LinkedList<String> asyncRead(BufferedReader in, Object lock, String streamName)
  {
    var inputQueue = new LinkedList<String>();
    Threads.inDaemon(()->
               {
                 var ok = true;
                 while (ok)
                   {
                     String s;
                     try
                       {
                         s = in.readLine();
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
                 try
                   {
                     in.close();
                   }
                 catch (IOException ioe)
                   { // NYI: ignored
                   }
               });
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
    // System.out.println("ACTION: "+e.getActionCommand());
    switch (e.getActionCommand())
      {
      case "start recorder" ->
        {
          Threads.inDaemon(()->
            {
              try
                {
                  var p = Path.of(Feeze.FEEZE_HOME).resolve("bin").resolve("feeze_recorder").normalize().toAbsolutePath();
                  var p1 = new ProcessBuilder("/usr/bin/pkexec",
                                              p.toString()).start();
                  var lock = new Object();
                  var input = true ? asyncRead(p1.inputReader(), lock, "stdout") : null;
                  var error = true ? asyncRead(p1.errorReader(), lock, "stderr") : null;

                  Threads.inDaemon(()->
                    {
                      while (p1.isAlive())
                        {
                          synchronized (lock)
                            {
                              while ((input==null || input.isEmpty()) &&
                                     (error==null || error.isEmpty()) && p1.isAlive())
                                {
                                  Threads.wait(lock);
                                }
                              while (input != null && !input.isEmpty())
                                {
                                  var s = input.removeFirst();
                                  _control._recorderOutput.append(s+"\n");
                                }
                              while (error != null && !error.isEmpty())
                                {
                                  var s = error.removeFirst();
                                  _control._recorderOutput.append("ERR: "+s+"\n");
                                }
                            }
                        }
                    });
                  synchronized (ControlListener.this)
                    {
                      _recorderInput = p1.outputWriter();
                    }
                  _control._record.setEnabled(true);
                  p1.waitFor();
                }
              catch (IOException ioe)
                {
                  JOptionPane.showMessageDialog(_control._startRecorder, ioe.getMessage(), DIALOG_HEADER, JOptionPane.ERROR_MESSAGE, null /* Icon */);
                }
              catch (InterruptedException ie)
                {
                  JOptionPane.showMessageDialog(_control._startRecorder, "Interrupted! "+ie.getMessage(), DIALOG_HEADER, JOptionPane.ERROR_MESSAGE, null /* Icon */);
                }
              finally
                {
                  BufferedWriter w;
                  synchronized (ControlListener.this)
                    {
                      w = _recorderInput;
                      _recorderInput = null;
                    }
                  if (w != null)
                    {
                      try
                        {
                          w.close();
                        }
                      catch (IOException ioe)
                        {
                          // ignore.
                        }
                    }
                  _control._record.setEnabled(false);
                }
            });
        }
      case "record" ->
        {
          BufferedWriter w;
          synchronized (ControlListener.this)
            {
              w = _recorderInput;
            }
          if (w != null)
            {
              Threads.inDaemon(()->
                         {
                           try
                             {
                               w.write("START '"+_control._sharedMemName.getText()+"'\n");
                               w.flush();
                             }
                           catch (IOException ioe)
                             {
                               // ignore
                             }
                         });
            }
        }
      case "show data" ->
        {
          Threads.inDaemon(()->Feeze.showData(_control._sharedMemName.getText()));
        }
      }
  }

}
