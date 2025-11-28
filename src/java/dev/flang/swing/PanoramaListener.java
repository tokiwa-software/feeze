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
 * Java source code of class dev.flang.swing.PanoramaListener
 *
 *---------------------------------------------------------------------*/


package dev.flang.swing;

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
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import dev.flang.util.Threads;

/*---------------------------------------------------------------------*/


/**
 * PanoramaListener is a helper class used by Panorama to
 * handle input events.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class PanoramaListener
  implements ActionListener,
             MouseListener,
             MouseMotionListener
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The buttons to scale the data area.  Never null, dummy buttons will be used
   * if user did not provide any.
   */
  final Component _compress, _expand, _zoom, _unzoom;


  /**
   * The Panorama this is working on.
   */
  Panorama _p;


  /**
   * While dragging: the original moust pos
   */
  private int _dragX, _dragY;


  /**
   * Set during dragging to suppress mouse clicks.
   */
  private boolean _supressMouseLeft = false;
  private boolean _supressMouseRight = false;


  /**
   * Auto-repeat is done by this thread.  Will be set to null when shutting
   * down.
   */
  volatile Autorepeat _autorepeat = new Autorepeat();
  {
    _autorepeat.start();
  }


  /**
   * Current KeyboardFocusManager
   */
  KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create this listener.
   *
   * @param p panorama this will be working on.
   *
   * @param bCompress button to compress horizonally, may be null.
   *
   * @param bExpand  button to expand horizonally, may be null.
   *
   * @param bZoomIn button to enlarge, may be null.
   *
   * @param bZoomOut button to shrink, may be null.
   */
  PanoramaListener(Panorama p,
                   JButton bCompress,
                   JButton bExpand,
                   JButton bZoomIn,
                   JButton bZoomOut)
  {
    this._p = p;
    this._compress = addButton(bCompress,"compress");
    this._expand   = addButton(bExpand  ,"expand"  );
    this._zoom     = addButton(bZoomIn  ,"zoom in" );
    this._unzoom   = addButton(bZoomOut ,"zoom out");
    manager.addKeyEventDispatcher(new Keys());
    p.addMouseListener(this);
    p.addMouseMotionListener(this);
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
        b.addMouseListener(this);
      }
    else
      {
        b = new JButton("", null);
      }
    return b;
  }


  /**
   * rememberPosForScaling records the data position of the given MouseEvent.
   *
   * @param e a mouse event, must not be null.
   */
  void rememberPosForScaling(MouseEvent e)
  {
    _p.rememberPosForScaling(e.getX(),e.getY());
  }


  /**
   * mouseReleased will stop auto-repeat.
   *
   * @param e the mouse event.
   */
  @Override
  public void mouseReleased(MouseEvent e)
  {
    Component c = e.getComponent();
    var ar = _autorepeat;
    if (ar != null &&
        (SwingUtilities.isLeftMouseButton  (e) ||
         SwingUtilities.isMiddleMouseButton(e)    ) &&
        (c ==  _p       ||
         c == _compress ||
         c == _expand   ||
         c == _zoom     ||
         c == _unzoom      ))
      {
        ar.endRepeat();
      }
  }


  /**
   * mouseClicked will handle a click and start auto-repeat
   *
   * @param e the mouse event.
   */
  public void mouseClicked(MouseEvent e)
  {
    var ar = _autorepeat;
    if (ar != null)
      {
        synchronized (ar)
          {
            if (!ar.repeating())
              {
                boolean control   = (e.getModifiers() & InputEvent.CTRL_MASK ) != 0;
                boolean shift     = (e.getModifiers() & InputEvent.SHIFT_MASK) != 0;
                boolean rawleft   = SwingUtilities.isLeftMouseButton  (e) && !_supressMouseLeft;
                boolean rawmiddle = SwingUtilities.isMiddleMouseButton(e) && !_supressMouseRight;
                boolean left      = !control && rawleft;
                boolean middle    = rawmiddle || (control && rawleft);
                if (left)
                  {
                    if (e.getComponent() ==  _p)
                      {
                        rememberPosForScaling(e);
                        if (shift)
                          {
                            _p.zoomIn(0);
                          }
                        else
                          {
                            _p.expand(0);
                          }
                        _p.recallPos();
                      }
                  }
                else if (middle)
                  {
                    if (e.getComponent() ==  _p)
                      {
                        rememberPosForScaling(e);
                        if (shift)
                          {
                            _p.zoomOut(0);
                          }
                        else
                          {
                            _p.compress(0);
                          }
                        _p.recallPos();
                      }
                  }
              }
          }
      }
  }


  /**
   * mouseEntered
   *
   * @param e the mouse event.
   */
  @Override
  public void mouseEntered(MouseEvent e) { }


  /**
   * mouseExited
   *
   * @param e the mouse event.
   */
  @Override
  public void mouseExited(MouseEvent e) { }


  /**
   * mouseMoved
   *
   * @param e the mouse event.
   */
  @Override
  public void mouseMoved(MouseEvent e)
  {
  }


  /**
   * mousePressed handle the mouse pressed event, i.e., signal to
   * _autorepeat to start repeat mode.
   *
   * @param e the mouse event.
   */
  @Override
  public void mousePressed(MouseEvent e)
  {
    var ar = _autorepeat;
    if (ar != null)
      {
        synchronized (ar)
          {
            boolean control = (e.getModifiers() & InputEvent.CTRL_MASK ) != 0;
            boolean shift   = (e.getModifiers() & InputEvent.SHIFT_MASK) != 0;
            /* treat control+left like middle: */
            boolean left    = !control && SwingUtilities.isLeftMouseButton(e);
            boolean middle  = SwingUtilities.isMiddleMouseButton(e) || (control && SwingUtilities.isLeftMouseButton(e));
            if (left)
              {
                _supressMouseLeft = false;
                Component c = e.getComponent();
                if (c ==  _p)
                  {
                    _dragX = e.getX();
                    _dragY = e.getY();
                    rememberPosForScaling(e);
                    ar.repeat(shift ? _zoom : _expand ,true);
                  }
                else if ((   c == _compress)
                         || (c == _expand)
                         || (c == _zoom)
                         || (c == _unzoom))
                  {
                    _p.rememberCenter();
                    ar.repeat(c,true);
                  }
              }
            if (middle)
              {
                _supressMouseRight = false;
                Component c = e.getComponent();
                if (c ==  _p)
                  {
                    _dragX = e.getX();
                    _dragY = e.getY();
                    rememberPosForScaling(e);
                    ar.repeat(shift ? _unzoom : _compress, false);
                  }
                else if ((   c == _compress)
                         || (c == _expand)
                         || (c == _zoom)
                         || (c == _unzoom))
                  {
                    _p.rememberCenter();
                    ar.repeat(c, false);
                  }
              }
          }
      }
  }


  /**
   * mouseDragged handles dragging (moving panorama)
   *
   * @param e the mouse event.
   */
  public void mouseDragged(MouseEvent e)
  {
    var ar = _autorepeat;
    if (ar != null)
      {
        synchronized (ar)
          {
            if (SwingUtilities.isLeftMouseButton(e))
              {
                if (e.getComponent() ==  _p)
                  {
                    if (_dragX > 0) /* repeat thread sets this to -1 to disable dragging */
                      { /* stop auto-repeat */
                        ar._activeComponent = null;
                        ar.notify();
                      }
                    else
                      {
                        rememberPosForScaling(e);
                      }
                    if (_dragX > 0) /* repeat thread sets this to -1 to disable dragging */
                      {
                        int x = e.getX();
                        int y = e.getY();
                        int dx = x - _dragX;
                        int dy = y - _dragY;
                        int max_x = _p.getWidth()  - _p._viewport.getWidth();
                        int max_y = _p.getHeight() - _p._viewport.getHeight();
                        int new_x = _p._viewport.getViewPosition().x - dx;
                        int new_y = _p._viewport.getViewPosition().y - dy;

                        new_x = Math.min(new_x, max_x);
                        new_y = Math.min(new_y, max_y);
                        new_x = Math.max(new_x, 0);
                        new_y = Math.max(new_y, 0);

                        _p._viewport.setViewPosition(new Point(new_x, new_y));

                        _supressMouseLeft = true;
                      }
                  }
              }
            else if (SwingUtilities.isRightMouseButton(e))
              {
                if (e.getComponent() ==  _p)
                  {
                    rememberPosForScaling(e);
                  }
              }
          }
      }
  }


  /**
   * actionPerformed handles click on a button.
   *
   * @param e the associated event
   */
  @Override
  public void actionPerformed(ActionEvent e)
  {
    var ar = _autorepeat;
    if (ar != null)
      {
        synchronized (ar)
          {
            switch (e.getActionCommand())
              {
              case "compress" ->
                {
                  if (ar._autorepeatFor != _compress)
                    {
                      _p.rememberCenter();
                      _p.compress(0);
                      _p.recallPos();
                    }
                }
              case "expand" ->
                {
                  if (ar._autorepeatFor != _expand)
                    {
                      _p.rememberCenter();
                      _p.expand(0);
                      _p.recallPos();
                    }
                }
              case "zoom in" ->
                {
                  if (ar._autorepeatFor != _zoom)
                    {
                      _p.rememberCenter();
                      _p.zoomIn(0);
                      _p.recallPos();
                    }
                }
              case "zoom out" ->
                {
                  if (ar._autorepeatFor != _unzoom)
                    {
                      _p.rememberCenter();
                      _p.zoomOut(0);
                      _p.recallPos();
                    }
                }
              }
          }
      }
  }


  /**
   * Notify this listener that the current call to {@code repaint()} is done.
   */
  void paintDone()
  {
    var ar = _autorepeat;
    if (ar != null)
      {
        synchronized (ar)
          {
            ar.notify();
          }
      }
  }


  /**
   * Notify this listener to terminate, i.e., stop _autorepeat thread.
   */
  public void cleanup()
  {
    var ar = _autorepeat;
    if (ar != null)
      {
        ar.interrupt();
        _autorepeat = null;
      }
    _p = null;
  }


  /**
   * Keys permit moving the panorama data with up/down/left/right keys:
   */
  class Keys implements KeyEventDispatcher
  {

    @Override
    public boolean dispatchKeyEvent(KeyEvent e)
    {
      if (e.getID() == KeyEvent.KEY_PRESSED)
        {
          if (manager.getFocusOwner() instanceof JComponent fo && fo.getTopLevelAncestor() == _p.getTopLevelAncestor())
            {
              int xscrollspeed = _p._viewport.getWidth()/10;
              int yscrollspeed = _p._viewport.getHeight()/10;
              int x =_p._viewport.getViewPosition().x;
              int y =_p._viewport.getViewPosition().y;
              int max_x = _p.getWidth()  - _p._viewport.getWidth();
              int max_y = _p.getHeight() - _p._viewport.getHeight();

              switch (e.getKeyCode())
                {
                  case KeyEvent.VK_UP:    y = Math.max(y - yscrollspeed, 0    ); break;
                  case KeyEvent.VK_DOWN:  y = Math.min(y + yscrollspeed, max_y); break;
                  case KeyEvent.VK_LEFT:  x = Math.max(x - xscrollspeed, 0    ); break;
                  case KeyEvent.VK_RIGHT: x = Math.min(x + xscrollspeed, max_x); break;
                  default:                                                       break;
                }

              _p._viewport.setViewPosition(new Point(x, y));
            }
        }
      return false;
    }
  }

  /**
   * Autorepeat is a thread that handles auto-repeat while mouse button is held
   * down.
   */
  class Autorepeat extends Thread
  {

    /**
     * The button that was clicked.  In case of a click into the panorama area,
     * this is set to the corresponding button: left: expand, shift+left:
     * compress, middle: zoom, shift+middle: unzoom.  area, this represents the
     * corresponding button depending on the state of shift and left/right
     * button.
     */
    Component _activeComponent = null;


    /**
     * When repeat has started, the value of _activeComponent will be copied to
     * this field to indicate that auto-repeat on this component has started.
     */
    Component _autorepeatFor = null;


    /**
     * Counter for repeats performed during current auto-repeat.
     */
    int _repeatCount = 0;


    /**
     * Constructor, sets the daemon flag.
     */
    Autorepeat()
    {
      setDaemon(true);
    }


    /**
     * the auto-repeat logic running in parallel and most of the time waiting
     * ton {@code Autorepeat.this} for action.
     */
    public synchronized void run()
    {
      while (_autorepeat != null)
        {
          while (_activeComponent == null)
            {
              Threads.wait(this);
            }
          Threads.wait(this, 200, 0);
          long delta = 0;
          int noWaits = 0;
          while (_activeComponent != null)
            {
              _repeatCount = _activeComponent == _autorepeatFor
                ? Math.min(_repeatCount + 1, 40)
                : 1;
              if (delta < 50 || noWaits >= 10)
                {
                  Threads.wait(this, delta < 50 ? 50-delta : 50, 0);
                  noWaits = 0;
                }
              else
                {
                  noWaits++;
                }
              delta = -System.currentTimeMillis();
              final Component button = _activeComponent;
              if (button != null)
                {
                  _dragX = -1; /* disable dragging while mouse pressed */
                  javax.swing.SwingUtilities.invokeLater(()->
                    {
                      if      (button == _compress) { _p.compress(_repeatCount); }
                      else if (button == _expand  ) { _p.expand(  _repeatCount); }
                      else if (button == _zoom    ) { _p.zoomIn(  _repeatCount); }
                      else if (button == _unzoom  ) { _p.zoomOut( _repeatCount); }
                      _p.recallPos();
                    });
                  Threads.wait(this);
                  _autorepeatFor = button;
                  if (_autorepeat._activeComponent == _compress ||
                      _autorepeat._activeComponent == _expand      )
                    {
                      _supressMouseLeft = true;
                    }
                  if (_autorepeat._activeComponent == _zoom   ||
                      _autorepeat._activeComponent == _unzoom    )
                    {
                      _supressMouseRight = true;
                    }
                }
              delta += System.currentTimeMillis();
            }
          _autorepeatFor = null;
          /* Redraw one more with !repeating() in case of lower quality drawing
           * during auto-repeat.
           */
          javax.swing.SwingUtilities.invokeLater(() -> _p.recallPos());
        }

    }


    /**
     * repeat start an auto-repeat for a given button.
     *
     * @param b the button that was pressed or that represents the action.
     */
    synchronized void repeat(Component b, boolean left)
    {
      _autorepeat._activeComponent = b;
      notify();
    }


    /**
     * endRepeat stops current auto-repeat action.
     */
    synchronized void endRepeat()
    {
      _activeComponent = null;
      _autorepeatFor = null;
      notify();
    }

    /**
     * Are we in the middle of an auto-repeat, i.e., clicks should be ignore.
     */
    synchronized boolean repeating()
    {
      return _autorepeatFor != null;
    }

  }


}
