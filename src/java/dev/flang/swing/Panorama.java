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
 * Java source code of class dev.flang.swing.Panorama
 *
 *---------------------------------------------------------------------*/


package dev.flang.swing;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;

/*---------------------------------------------------------------------*/


/**
 * Panorama is a possible large area that permits scrolling and size
 * changes. A Panorama should be embedded in a JScrollPane.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class Panorama extends JPanel
{


  /*----------------------------  constants  ----------------------------*/


  /*
   * The frame is an area outside of the actual Panarama that permits scrolling
   * a bit outside of the data, e.g., to read text labels that extend outside of
   * the area:


  <-------------------------- width ----------------------------------------->

  +--------------------------------------------------------------------------+   A
  |                   A                                                      |   |
  |                   |                                                      |   |
  |                 frameT                                                   |   |
  |                   |                                                      |   |
  |                   V                                                      |   |
  |           +--------------------------------------------------+           |   |
  |           |    A                                             |           |   |
  |           |<---+-------------- dataWidth ------------------->|           |
  |           |    |                                             |           |   h
  |<-frameL-->|    |                                             |<--frameR->|   e
  |           |  dataHeight                                      |           |   i
  |           |    |                                             |           |   g
  |           |    |            +--------------------------+     |           |   h
  |           |    |            | r = getVisibleRect   A   |     |           |   t
  |           |    |            |                      |   |     |           |
  |           |    |            |<------r.width--------+-->|     |           |   |
  |           |    |            |                      |   |     |           |   |
  |           |    |            |                 r.height |     |           |   |
  |           |    |            |                      V   |     |           |   |
  |           |    |            +--------------------------+     |           |   |
  |           |    V                                             |           |   |
  |           +--------------------------------------------------+           |   |
  |                   A                                                      |   |
  |                   |                                                      |   |
  |                 frameB                                                   |   |
  |                   |                                                      |   |
  |                   V                                                      |   |
  +--------------------------------------------------------------------------+   V

   */


  /**
   *
   * This factor gives a default percentage of this frame relative to the
   * visible rectangle.
   */
  private static final double FRAME_SIZE_PERCENTAGE = 0.125;



  /**
   * minimum frame width, in case visible rectangle is small.
   */
  private static final int MIN_FRAME_WIDTH = 64;


  /**
   * minimum frame height, in case visible rectangle is small.
   */
  private static final int MIN_FRAME_HEIGHT = 16;


  /*----------------------------  variables  ----------------------------*/


  /**
   * width of scrollable panorama area.  Set by adjustPos().
   */
  private int _width = -1;


  /**
   * height of scrollable panorama area.  Set by adjustPos().
   */
  private int _height = -1;


  /**
   * width of left frame around data area.  Set by adjustPos().
   */
  private int _frameL = 0;


  /**
   * width of right frame around data area.  Set by adjustPos().
   */
  private int _frameR = 0;


  /**
   * height of top frame around of data area.  Set by adjustPos().
   */
  private int _frameT = 0;


  /**
   * height of bottom frame around of data area.  Set by adjustPos().
   */
  private int _frameB = 0;


  /**
   * The listener used to handle events in this Panorama.
   *
   * This is stored here to notify the listener when paint is done, when it
   * should clean up and to check if an animation is ongoing.
   */
  private volatile PanoramaListener _listener;


  /**
   * x position of the center during zoom/compress.  Set by rememberPosForScaling().
   */
  private int midScreenPosX_;


  /**
   * y position of the center during zoom/compress.  Set by rememberPosForScaling().
   */
  private int midScreenPosY_;


  /**
   * the x and y position recorded via rememberPosForScaling(), relative to
   * dataWidth().
   */
  private double rememberedX_, rememberedY_;


  /**
   * the viewport of the JScrollPane from scroller(), needed for changing the view
   */
  protected JViewport _viewport;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create Panorama using given buttons for x-compression and zoom.
   *
   * @param compress button to compress horizonally, may be null.
   *
   * @param expand button to expand horizonally, may be null.
   *
   * @param zoomIn button to enlarge, may be null.
   *
   * @param zoomOut button to shrink, may be null.
   */
  public Panorama(JButton compress,
                  JButton expand,
                  JButton zoomIn,
                  JButton zoomOut)
  {
    _listener = new PanoramaListener(this, compress, expand, zoomIn, zoomOut);
    setBackground(Color.white);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * max of three
   *
   * @paraof three
   *
   * @param b an int
   *
   * @param c an int
   *
   * @param d an int
   *
   * @return result with {@code result>=a && result>=b && result>=c && result>=d && (result==a || result==b || result==c || result==d)}.
   */
  private int max(int a, int b, int c, int d)
  {
    return Math.max(a, Math.max(b, Math.max(c, d)));
  }


  /**
   * adjustPos update _width, _height, _frameL, _frameR, _frameT and _frameB
   * such that the top left corner of the data area will be at posx, posy.
   * Revalidate and repaint.
   *
   * @param posx desired x pos of left side of the data area.
   *
   * @param posy desired y pos of top side of the data area.
   */
  public void adjustPos(int posx, int posy)
  {
    var r = getVisibleRect();
    var w = dataWidth();
    var h = dataHeight();

    var fw = (int) (r.width  * FRAME_SIZE_PERCENTAGE);
    var fh = (int) (r.height * FRAME_SIZE_PERCENTAGE);

    // make frame large enough to cover r:
    var fW = (r.width  - w) / 2;
    var fH = (r.height - h) / 2;
    _frameL = max(MIN_FRAME_WIDTH , fw, posx   , fW);
    _frameR = max(MIN_FRAME_WIDTH , fw, fW-posx, fW);
    _frameT = max(MIN_FRAME_HEIGHT, fh, posy   , fH);
    _frameB = max(MIN_FRAME_HEIGHT, fh, fH-posy, fH);

    posx    -= _frameL;
    posy    -= _frameT;
    _width   = _frameL + w + _frameR;
    _height  = _frameT + h + _frameB;

    setPreferredSize(new Dimension(_width, _height));
    setBounds(posx, posy, _width, _height);

    revalidate();
    repaint();
  }


  /**
   * Draw this component by calling paintPanorama(g) and then notify the listener
   * that painting is done.
   *
   * To redefine this, redefine paintPanorama() instead.
   *
   * @param g the graphics to draw to.
   */
  final protected void paintComponent(Graphics g)
  {
    super.paintComponent(g);
    paintPanorama(g);

    var l = _listener;
    if (l != null)
      {
        l.paintDone();
      }
  }


  /**
   * Obtain a reference to a JScrollerPane that contains this Panorama.  This
   * scroller pane has to be added to the surrounding component.
   *
   * @param w the desired width
   *
   * @param h the desired height
   */
  public JScrollPane scroller(int w, int h)
  {
    var res = new JScrollPane(this);
    res.setPreferredSize(new Dimension(w, h));

    // increment used for arrow buttons and mouse wheel.
    //
    // NYI: IMPROVEMENT: instead of hard coding this, would be better to make
    // this dependend on the window size and, e.g., always use 1/10th of the
    // window width or height, resp., as the increment. Tis should then be
    // updated dynamically on a window resize event.
    res.getVerticalScrollBar  ().setUnitIncrement(64);
    res.getHorizontalScrollBar().setUnitIncrement(64);

    adjustPos(0, MIN_FRAME_HEIGHT);
    _viewport = res.getViewport();
    return res;
  }


  /**
   * rememberCenter records the data position of the center of the visible
   * area.
   */
  void rememberCenter()
  {
    var r = getVisibleRect();
    rememberPosForScaling(r.width  / 2 - getX(),
                          r.height / 2 - getY());
  }


  /**
   * record the current position before the display is scaled.  This must be
   * overwritten for data areas that do not scale lineary to record the the
   * position in the data at posx/posy.  After rescaling, the new position of
   * this data is requested via recallX() and recallY().
   *
   * @param posx absolute x position
   *
   * @param posy absolute y position
   */
  protected void rememberPosForScaling(int posx, int posy)
  {
    midScreenPosX_ = posx + getX();
    midScreenPosY_ = posy + getY();
    rememberedX_ = (double) (posx - leftFrame()) / dataWidth();
    rememberedY_ = (double) (posy - topFrame() ) / dataHeight();
  }


  /**
   * recallX is called after the data was rescaled to determine the new x
   * position of the data that was recorded via rememberPosForScaling before
   * scaling.
   *
   * @return x position corresponding to posx passed to rememberPosForScaling.
   */
  protected int recallX()
  {
    return (int) (rememberedX_ * dataWidth());
  }


  /**
   * recallY is called after the data was rescaled to determine the new y
   * position of the data that was recorded via rememberPosForScaling before
   * scaling.
   *
   * @return y position corresponding to posy passed to rememberPosForScaling.
   */
  protected int recallY()
  {
    return (int) (rememberedY_ * dataHeight());
  }


  /**
   * recallPos calls adjustPos() with recalculated position from rememberPosForScaling
   * and recallX/recallY.
   */
  public void recallPos()
  {
    adjustPos(midScreenPosX_ - recallX(),
              midScreenPosY_ - recallY());
  }


  /**
   * dataWidth the current width of whe whole panorama in pixels, excluding the
   * frame.
   *
   * @return current width.
   */
  protected abstract int dataWidth();


  /**
   * dataHeight the current height of whe whole panorama in pixels, excluding the
   * frame.
   *
   * @return current height.
   */
  protected abstract int dataHeight();


  /**
   * expand dataWidth in horizontal direction by one step.
   *
   * @param cnt either cnt>0 for the number of this step (during auto-repeat), 0
   * for a larger single step.
   */
  protected abstract void expand(int cnt);


  /**
   * compress dataWidth in horizontal direction by one step.
   *
   * @param cnt either cnt>0 for the number of this step (during auto-repeat), 0
   * for a larger single step.
   */
  protected abstract void compress(int cnt);


  /**
   * zoomIn expands dataWidth and dataHeight by one step.
   *
   * @param cnt either cnt>0 for the number of this step (during auto-repeat), 0
   * for a larger single step.
   */
  protected abstract void zoomIn(int cnt);


  /**
   * zoomOut compresses dataWidth and dataHeight by one step.
   *
   * @param cnt either cnt>0 for the number of this step (during auto-repeat), 0
   * for a larger single step.
   */
  protected abstract void zoomOut(int cnt);


  /**
   * width of this Panorama including the frame.
   *
   * Note that width() == dataWidth() + leftFrame() + rightFrame().
   *
   * @return the width.
   */
  public int width()
  {
    return _width;
  }


  /**
   * height of this Panorama including the frame.
   *
   * Note that height() == dataHeight() + topFrame() + bottomFrame().
   *
   * @return the height.
   */
  public int height()
  {
    return _height;
  }


  /**
   * x position of first pixel of data area.  This may be larger than 0 to
   * permit scrolling to the right in case the contents are wider than the
   * screen.
   *
   * Note that width() == dataWidth() + leftFrame() + rightFrame().
   *
   * @return width of left frame
   */
  public int leftFrame()
  {
    return _frameL;
  }


  /**
   * Width of frame to the right of data area.  Set to >0 for scrolling to the
   * left in case the contents are wider than the screen.
   *
   * Note that width() == leftFrame() + dataWidth() + rightFrame().
   *
   * @return right frame width.
   */
  public int rightFrame()
  {
    return _frameL;
  }


  /**
   * y position of first pixel of data area.  A value larger than 0 permits scrolling
   * to the right in case the contents are larger than the screen.
   *
   * Note that height() == dataHeight() + topFrame() + bottomFrame().
   *
   * @return top frame width.
   */
  public int topFrame()
  {
    return _frameT;
  }


  /**
   * Width of frame under the data area.  A value larger than 0 permits
   * scrolling to the right in case the contents are larger than the screen.
   *
   * Note that height() == dataHeight() + topFrame() + bottomFrame().
   *
   * @return width of bottom frame
   */
  public int bottomFrame()
  {
    return _frameB;
  }


  /**
   * paintPanorama redraws after this Panorama was been moved/compressed/scaled.
   * It has to be redefined to perform the required drawing operations depending
   * on dataWidth(), dataHeight(), leftFrame() and topFrame().
   *
   * @param g the graphics to draw to.
   */
  protected abstract void paintPanorama(Graphics g);


  /**
   * If we are currently drawing repeatedly (e.g., repeated zooming on long
   * mouse button), this returns true.  In this case, speed is important, so
   * partial drawing is more acceptable.
   */
  protected boolean repeatedDrawing()
  {
    var l = _listener;
    return l != null && l._autorepeat.repeating();
  }


  /**
   * Clean up everything related to this Panorama, in particular threads.
   */
  public void cleanup()
  {
    var l = _listener;
    if (l != null)
      {
        l.cleanup();
        _listener = null;
      }
  }


}
