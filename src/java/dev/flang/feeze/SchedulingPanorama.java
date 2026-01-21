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
 * Java source code of class dev.flang.feeze.SchedulingPanorama
 *
 *---------------------------------------------------------------------*/


package dev.flang.feeze;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Rectangle;

import java.util.TreeSet;

import javax.swing.JButton;
import javax.swing.JComponent;

import dev.flang.swing.Panorama;

/*---------------------------------------------------------------------*/


/**
 * SchedulingPanorama is the Panorama that displays the recorded scheduling
 * data.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class SchedulingPanorama extends Panorama
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * Original distance between two thread lines when zoom factor is 1.0.
   */
  static final int NORMAL_THREAD_SPACING = 30;


  /**
   * Distance between two thread lines of faded-out threads when zoom factor is
   * 1.0.
   */
  static final int MIN_THREAD_SPACING = 2;


  /**
   * Factor for one zoom step
   */
  static final double ZOOM_STEP = 1.003125;


  /**
   * Min distance in pixels between two grades on the scale.
   */
  static final int MIN_SCALE_WIDTH = 7;


  // Colors from fuzion-lang.dev:
  static Color bgcol = new Color(247,246,237);
  static Color peach = new Color(255, 229, 180);
  static Color coral = new Color(255, 187, 110);
  static Color pink = new Color(255, 218, 239);
  static Color yellow = new Color(255, 255, 205);
  static Color green = new Color(202, 252, 202);
  static Color blue = new Color(186, 234, 240);
  static Color dark_blue = new Color(0x08,0x42, 0x98);
  static Color purple = new Color(211, 198, 221);
  static Color mizuiro = new Color(212, 230, 240);
  static Color gray = new Color(128,128,128);

  static Color light_gray = new Color(200,200,200);

  static final Color DARK_GREEN = new Color(0,191,0);
  static final Color VERY_DARK_GREEN = new Color(0,63,0);
  static final Color LILAC = new Color(81, 36, 128);
  //  static final Color[] PROCESS_COLS = new Color[] { peach, coral, pink, yellow, green, blue, purple, mizuiro, gray };
  static final Color[] PROCESS_COLS = new Color[] { bgcol, Color.WHITE };


  /*------------------------------  fields  -----------------------------*/


  /**
   * the data.
   */
  final Data _data;


  final Zoom _zoom = new Zoom();


  /**
   * The time scaling factor currently in use. This changes the compression
   * along the horizontal line. Unlike _zoom, the vertical zoom is not affected._
   */
  double _timeScale = 1.0;


  static final double SCALE_STEP = 1.025;


  /**
   * During scaling, the original time in the middle of the screen. Set by
   * rememberPosForScaling().
   */
  long _rememberedMiddleNs;


  /**
   * During scaling, the original thread number (or fraction for in-between
   * threads position) in the middle of the screen. Set by
   * rememberPosForScaling().
   */
  double _rememberedMiddleThread;


  /**
   * Values for which _threadY[] was last calculated.  Used to check if {@code
   * threadY()} must recalculate these.
   */
  int _lastThreadSpacing = -1;
  double _lastPixelsPerNano = -1;
  int _lastX = -1, _lastW = -1;


  /**
   * Cached results for {@code threadY}.
   */
  int[] _threadY;


  /**
   * Is thread for given index shown? This is set by {@code threadY()}
   * depending on the visible thread activity.
   */
  boolean[] _threadShown;


  /*---------------------------  constructors  --------------------------*/


  SchedulingPanorama(Data data,
                     JButton b1,
                     JButton b2,
                     JButton b3,
                     JButton b4)
  {
    super(b1,b2,b3,b4);
    _data = data;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Number of pixels for 1ns.
   */
  double pixelsPerNano()
  {
    return 0.0000004*_timeScale; // 400ns for 1 pixel
  }


  /*
    horizontal units:

    index         index used in _data.nanos(i)
    boot_ns       nanoseconds since boot time
    relative_ns   nanoseconds since tracing recording started
    compressed_x  x coordinate taking current time compression into account
    zoomed_x      x coordinate taking current zoom factor into account
    translated_x  x coordindate taking left gap translation into account


    horizontal conversion base functions:
   */

  /**
   * Convert index to boot_ns (nanoseconds since boot time)
   */
  long boot_ns_from_index(int  i          ) { // if (PRECONDITIONS) require(_data.kind(at) == Offsets.ENTRY_KIND_SCHED_SWITCH);
                                              return _data.nanos(i);
                                            }
  /**
   * Convert boot_ns (nanoseconds since boot time) to index
   */
  long index_from_boot_ns(long boot_ns    ) { throw new Error("NYI: index_from_bootns, not needed yet"); }

  /**
   * Convert boot_ns to relative_ns (nanoseconds since boot time to nanoseconds since trace recording started)
   */
  long relative_ns       (long boot_ns    ) { return boot_ns     - _data.nanosMin();           }

  /**
   * Convert relative_ns to boot_ns (nanoseconds since trace recording started to nanoseconds since boot time)
   */
  long absolute_ns       (long relative_ns) { return relative_ns + _data.nanosMin();           }

  /**
   * Convert relative_ns to compressed_x (x coordinate taking current time compression into account)
   */
  int    compress_x      (long ns            ) { return (int) (compress_x((double) ns) + 0.5); }
  double compress_x      (double ns          ) { return ns * pixelsPerNano();                  }

  /**
   * Convert compressed_x to relative_ns
   */
  long   uncompress_x    (int    compressed_x) { return (long) uncompress_x(compressed_x);     }
  double uncompress_x    (double compressed_x) { return compressed_x / pixelsPerNano();        }

  /**
   * Convert compressed_x to zoomed_x (x coordinate taking zoom factor into account)
   */
  int    zoom_x          (int    compressed_x) { return zoom  (compressed_x);                  }
  double zoom_x          (double compressed_x) { return zoom  (compressed_x);                  }

  /**
   * Convert zoomed_x to compressed_x
   */
  int    unzoom_x        (int    zoomed_x    ) { return unzoom(zoomed_x);                      }
  double unzoom_x        (double zoomed_x    ) { return unzoom(zoomed_x);                      }

  /**
   * Convert zoomed_x to translated_x (x coordinate taking left gap into account
   */
  int  translate_x       (int zoomed_x       ) { return zoomed_x     + leftFrame();            }

  /**
   * Convert translated_x to zoomed_x
   */
  int  untranslate_x     (int translated_x   ) { return translated_x - leftFrame();            }


  /**
   * Convert unzoomed x/y coordintate into zoomed coordinate
   */
  int    zoom            (int    xy          ) { return _zoom.zoom(xy);                        }
  double zoom            (double xy          ) { return _zoom.zoom(xy);                        }

  /**
   * Convert zoomed x/y coordintate into unzoomed coordinate
   */
  int    unzoom          (int    xy          ) { return _zoom.unzoom(xy);                      }
  double unzoom          (double xy          ) { return _zoom.unzoom(xy);                      }


  /**
   * Convert relative_ns to translated_x (x coordinate taking left gap into account)
   */
  int nanos_to_posx(long ns)
  {
    return translate_x((int) (zoom_x(compress_x((double) ns)) + 0.5));
  }


  /**
   * Convert translated_x (x coordinate taking left gap into account) to relative_ns
   */
  long posx_to_nanos(int x)
  {
    return (long) uncompress_x(unzoom_x((double) untranslate_x(x)));
  }


  /**
   * Convert index to translated_x (x coordinate taking left gap into account)
   */
  int index_to_posx(int i)
  {
    return nanos_to_posx(relative_ns(boot_ns_from_index(i)));
  }


  /**
   * Convert relative_ns to zoomed_x
   */
  int nanos_to_zoom_x(long ns)
  {
    return zoom_x(compress_x(ns));
  }


  /**
   * Convert zoomed_x to relative_ns
   */
  long zoom_x_to_nanos(int xut)
  {
    return uncompress_x(unzoom_x(xut));
  }


  /*---------------------------------------------------------------------*/


  /**
   * width of the data area in pixels
   */
  public int dataWidth()
  {
    return nanos_to_zoom_x(relative_ns(_data.nanosMax()));
  }


  /**
   * height of the data area in pixels
   */
  public int dataHeight()
  {
    return zoom(NORMAL_THREAD_SPACING*(_data._threads.size()+3));
  }


  /*---------------------------------------------------------------------*/


  /**
   * zoomed y coordinate of thread with given index.
   *
   * @param t a thread index, may be >=_data._threads.size()
   *
   * @return the y coordinate of the horizontal line for this thread
   */
  int threadY(int t)
  {
    return threadY0(t)+topFrame();
  }
  int threadY0(int t)
  {
    assert
      (t >= 0);

    var r = getVisibleRect(); // NYI: make this an argument
    var ts = 2*zoom(NORMAL_THREAD_SPACING);
    if (_lastThreadSpacing != ts || _lastPixelsPerNano != pixelsPerNano() ||
        _lastX != r.x || _lastW != r.width)
      {
        _threadY = new int[_data._threads.size()];
        _threadShown = new boolean[_data._threads.size()];
        double y = ts;
        for (var i = 0; i<_data._threads.size(); i++)
          {
            var yd = threadYDelta(i, r);
            y = y + yd/2;
            _threadY[i] = (int) y;
            y = y + yd/2;
          }
        _lastThreadSpacing = ts;
        _lastPixelsPerNano = pixelsPerNano();
        _lastX = r.x;
        _lastW = r.width;
      }

    var l = _threadY.length;
    var res = t < l ? _threadY[Math.max(0,t)] : (_threadY[l-1] + ts*(t-l));
    return res;
  }


  /**
   * For a given y coordinate, get the corresponding thread number. In case `y`
   * lies betweend threads `ti` and `ti+1`, add the fraction of the in-between
   * space that `y` is below `ti`.
   */
  double posy_to_thread(int y)
  {
    y = y - topFrame();
    var ignore = threadY0(0);  // just interested in side effect of updating _threadY
    int ti = 0;
    while (ti+1 < _threadY.length && y >= _threadY[ti+1])
      {
        ti++;
      }
    var ty = _threadY[ti];
    var delta = ti+1 < _threadY.length ? _threadY[ti+1]-ty : zoom((double) NORMAL_THREAD_SPACING);
    return ti + (y - ty) / delta;
  }

  /**
   * Get the y position corresponding to the result `t` that was obtained using
   * `posy_to_thread`, i.e., after adjusting scaling factors, get the new y position.
   */
  int thread_to_posy(double t)
  {
    var ignore = threadY0(0);  // just interested in side effect of updating _threadY
    var ti = Math.min(Math.max(0, (int) t), _threadY.length-1);
    var ty = _threadY[ti];
    var delta = ti+1 < _threadY.length ? _threadY[ti+1]-ty : zoom((double) NORMAL_THREAD_SPACING);
    var res = (int) (ty + (t-ti) * delta);
    res = res + topFrame();
    return res;
  }


  /**
   * Helper for fading out inactive threads.  For a given thread number i and a
   * visible rectangle r, this gives the zoomed height of the area to be used
   * for this thread.
   *
   * As the activity in this thread move outside and further away of the visible
   * rect, this distance becomes smaller.
   */
  double threadYDelta(int i, Rectangle r)
  {
    var t = _data._threads.get(i);
    var il = t._at[actionAt(t, r.x        )];
    var ir = t._at[actionAt(t, r.x+r.width)];
    double f = 0;
    if (il == ir && t != _data.newThreadAt(il))
      {
        int xl = index_to_posx(il);
        int xr = index_to_posx(ir);
        int dl = xl < r.x         ? r.x-xl         : 0;
        int dr = xr > r.x+r.width ? xr-r.x-r.width : 0;
        double fl, fr;
        int transition = r.width/2;
        if (xl < r.x)
          {
            fl = (double) Math.min(transition,r.x-xl) / transition;
          }
        else if (xl <= r.x+r.width)
          {
            fl = 0;
          }
        else
          {
            fl = 1;
          }
        if (xr > r.x+r.width)
          {
            fr = (double) Math.min(transition,xr-r.x-r.width) / transition;
          }
        else if (xr >= r.x)
          {
            fr = 0;
          }
        else
          {
            fr = 1;
          }
        f = Math.min(fl,fr);
      }
    _threadShown[i] = f==0;
    return
      (  f) * zoom((double) MIN_THREAD_SPACING) +
      (1-f) * zoom((double) NORMAL_THREAD_SPACING);
  }


  /**
   * Return the thread a the given y posisition. Returns 0 if y is above all
   * threads and _data.threads.size()-1 if it is below all threads.
   *
   * @param y an actual y corrdinate.
   */
  int threadAt(int y)
  {
    var res = 0;
    while (res < _data._threads.size()-1 && y > threadY(res))
      {
        res++;
      }
    return res;
  }


  /*---------------------------------------------------------------------*/


  /**
   * For a given x position, find the next action left of that position.
   */
  int actionAt(SystemThread t, int x)
  {
    var al = 0;
    var ar = t._num_actions-1;
    int res = 0;
    while (al < ar)
      {
        int am = (al+ar)/2;
        var mx = nanos_to_posx(_data.nanos(t._at[am]) - _data.nanosMin());
        if (mx <= x) { res = am; al = am+1; }
        if (mx >= x) {           ar = am-1; }
      }
    while (res < t._num_actions-1 && (nanos_to_posx(_data.nanos(t._at[res+1]) - _data.nanosMin()) <= x))
      {
        res++;
      }
    return res;
  }


  /**
   * For a given x position, find the next gap index left of that position.
   */
  int gapAt(int x)
  {
    var al = 0;
    var ar = _data._gaps.size()-1;
    int res = 0;
    while (al < ar)
      {
        int am = (al+ar)/2;
        var mx = nanos_to_posx(_data.nanos(_data._gaps.get(am)) - _data.nanosMin());
        if (mx <= x) { res = am; al = am+1; }
        if (mx >= x) {           ar = am-1; }
      }
    while (res < _data._gaps.size()-1 && (nanos_to_posx(_data.nanos(_data._gaps.get(res+1)) - _data.nanosMin()) <= x))
      {
        res++;
      }
    return res;
  }


  /**
   * paintPanorama
   *
   * @param g
   */
  protected void paintPanorama(Graphics g)
  {
    long nt = System.nanoTime();

    var r = getVisibleRect();
    g.setColor(Color.white);
    g.fillRect(r.x, r.y, r.width, r.height);

    synchronized (_data)
      {
        if (_data.entryCount() > 0)
          {
            var c = Color.gray;
            int w = 1;
            long x0 = _data.nanosMin();
            long xn = _data.nanosMax();
            for (var i = threadAt(r.y); threadY(i-1) <= r.y+r.height && i < _data._threads.size(); i++)
              {
                int drawCnt = 0;
                int drawCnt2 = 0;
                var last_x = x0;
                var t = _data._threads.get(i);
                int y = threadY(i);

                int h = threadY(i+1)-y+1;
                g.setColor(PROCESS_COLS[t._p._num % PROCESS_COLS.length]);
                g.fillRect(r.x, y-h/2, r.x+r.width, y+h/2);

                if (!_threadShown[i])
                  {
                    g.setColor(gray);
                    _zoom.drawHLine(g,1,nanos_to_posx(0),y,nanos_to_posx(xn-x0));
                    continue;
                  }

                int blurredUpToX = -1;
                int from_a = actionAt(t, r.x);
                int to_a = actionAt(t, r.x+r.width)+1;
                int NAME_DIST_X = 384;
                long NAME_DIST_NS = 1;
                while (compress_x(NAME_DIST_NS) < NAME_DIST_X)
                  {
                    NAME_DIST_NS += NAME_DIST_NS;
                  }
                long nameShownAtNS = posx_to_nanos(r.x) & ~(NAME_DIST_NS-1);
                int nameShownAt = nanos_to_posx(nameShownAtNS);
                while (nameShownAtNS < _data.nanos(_data.entryCount()-1)-_data.nanosMin() && nameShownAt < r.x+r.width)
                  {
                    int x = nameShownAt;
                    g.setColor(gray);
                    _zoom.drawString(g, t.toString(from_a), x, y - zoom(2));
                    nameShownAtNS += NAME_DIST_NS;
                    nameShownAt = nanos_to_posx(nameShownAtNS);
                  }
                for (var a = from_a; a<to_a; a++)
                  {
                    var a0 = a;
                    var at = t._at[a];
                    Color nextCol;
                    int nextWidth;
                    if (t == _data.oldThreadAt(at))
                      {
                        nextCol = Color.blue;
                        nextWidth = 1;
                      }
                    else if (t == _data.newThreadAt(at))
                      {
                        nextCol = DARK_GREEN;
                        nextWidth = 15;
                      }
                    else
                      {
                        nextCol = Color.magenta;
                        nextWidth = 20;
                      }
                    var nl = _data.nanos(at)-_data.nanosMin();
                    var nr = (a+1 >= t._num_actions ? _data.nanosMax()
                                                    : _data.nanos(t._at[a+1])) -_data.nanosMin();
                    if (nl > nr)
                      {
                        System.out.println("**** events not monotonic: "+nl+" > "+nr+" delta: "+(nl-nr)+"ns for a="+a+" (max "+t._num_actions+") at "+at+"/"+
                                           (a+1 >= t._num_actions ? -1 : t._at[a+1]));
                      }
                    nr = Math.max(nl,nr); // NYI: REMOVE when it is ensured that time is monotonic!
                    var xl = nanos_to_posx(nl);
                    var xr = nanos_to_posx(nr);
                    var nnr = (a+2 >= t._num_actions ? _data.nanosMax()
                                                     : _data.nanos(t._at[a+2])) -_data.nanosMin();
                    if (a == 0)
                      {
                        if (t == _data.oldThreadAt(at))
                          {
                            g.setColor(DARK_GREEN);
                            _zoom.drawHLine(g,15,nanos_to_posx(0),y,xl);
                          }
                        else if (t == _data.newThreadAt(at))
                          {
                            g.setColor(Color.blue);
                            _zoom.drawHLine(g,1,nanos_to_posx(0),y,xl);
                          }
                        else
                          {
                            nextCol = Color.magenta;
                            nextWidth = 20;
                          }
                      }

                    if (posx_to_nanos(xl+2) < nnr)
                      {
                        drawCnt++;
                        g.setColor(nextCol);

                        _zoom.drawHLine(g,nextWidth,xl,y,xr-1);
                      }
                    else if (blurredUpToX < xr)
                      {
                        drawCnt2++;
                        g.setColor(VERY_DARK_GREEN);
                        _zoom.drawHLine(g,15,xl,y,xr-1);
                        blurredUpToX = xr;
                      }

                    if (xr > r.x+r.width)
                      {
                        a = t._num_actions;
                      }
                    if (a+1 >= t._num_actions)
                      {
                        if (t == _data.oldThreadAt(at))
                          {
                            g.setColor(Color.blue);
                            _zoom.drawHLine(g,1,xr,y,nanos_to_posx(_data.nanos(_data.entryCount()-1)-_data.nanosMin()));
                          }
                        else if (t == _data.newThreadAt(at))
                          {
                            g.setColor(DARK_GREEN);
                            _zoom.drawHLine(g,15,xr,y,nanos_to_posx(_data.nanos(_data.entryCount()-1)-_data.nanosMin()));
                          }
                      }
                  }
              }
            var from_gap = Math.max(0,gapAt(r.x)-1);
            var to_gap   = Math.min(_data._gaps.size()-1, gapAt(r.x+r.width)+1);
            for(var a = from_gap; a <= to_gap; a++)
              {
                var ar = _data._gaps.get(a);
                var al = ar-1;
                var xmin = nanos_to_posx(al >= 0 ? _data.nanos(al) - _data.nanosMin() : 0);
                var xmax = nanos_to_posx(          _data.nanos(ar) - _data.nanosMin()    );
                if (xmax >= r.x && xmin <= r.x+r.width)
                  {
                    g.setColor(new Color(255,0,100,63));
                    var y0 = threadY(0                      ) - zoom(NORMAL_THREAD_SPACING);
                    var y1 = threadY(_data._threads.size()-1) + zoom(NORMAL_THREAD_SPACING);
                    while (al >= 0 && _data.kind(al) != Offsets.ENTRY_KIND_SCHED_SWITCH)
                      {
                        al--;
                      }
                    g.fillRect(xmin,y0,xmax-xmin+1,y1-y0+1);
                  }
              }
          }
      }

    drawScale(g, r, threadY(0) - zoom(NORMAL_THREAD_SPACING), false);
  }


  /*---------------------------------------------------------------------*/


  /**
   * drawScale draws a scale below or above the thread graph.
   *
   * @param g the graphics to draw to.
   *
   * @param r rectangle giving the x range.
   *
   * @param y the y coordinate of the area below the trace where the scale is to be drawn.
   *
   * @param below draw a scale with labels underneath or above?
   */
  void drawScale(Graphics g, Rectangle r, int y, boolean belowBase)
  {
    long grade = 1;
    int f = 0;
    do
      {
        grade = (f % 4 != 2)  ? 2*grade : 5*grade/4;
        f++;
      }
    while (compress_x(grade) < MIN_SCALE_WIDTH && grade < Long.MAX_VALUE / 5);
    drawScale(g, r.x, r.x+r.width, y, belowBase, grade);
  }


  /**
   * drawScale draws a scale below or above the thread graph.
   *
   * @param g the graphics to draw to.
   *
   * @param x0 start x coordinate of the scale to draw.
   *
   * @param x1 maximum x coordinate at which drawing of the scale will be stopped.
   *
   * @param y0 the y coordinate of the area below the trace where the scale is to be drawn.
   *
   * @param below draw a scale with labels underneath or above?
   *
   * @param grade nanoseconds between two lines of scale.
   */
  void drawScale(Graphics g, int x0, int x1, int y0, boolean below, long grade)
  {
    long timens0 = posx_to_nanos(x0);
    long timens = ((timens0 + (grade-1)) / grade) * grade;
    int x = nanos_to_posx(timens);
    int y = y0;
    while (x < x1)
      {
        boolean longer = (((timens / grade) % 5) == 0);
        g.setColor(Color.black);
        _zoom.drawLine(g,1,x, y,
                       x,
                       y + (below ? 1 : -1) * zoom(longer ? 10 : 5));
        if (((timens / grade) % 10) == 0)
          {
            drawScaleLabel(g,timens,grade*10,x,y, below);
          }
        timens += grade;
        x = nanos_to_posx(timens);
      }
  }

  /**
   * Draw label for the scale
   *
   * @param g the graphics to draw to.
   *
   * @param timens the value of the label
   *
   * @param grade is the accuracy, i.e., a step between two labels.
   *
   * @param x label's x coordinate
   *
   * @param y label's y coordinate
   *
   * @param below draw a scale with labels underneath or above?
   */
  void drawScaleLabel(Graphics g, long timens, long grade, int x, int y, boolean below)
  {
    FontMetrics fm = g.getFontMetrics(_zoom.standardFont());
    if (below)
      {
        y += zoom(10) + fm.getAscent();
      }
    else
      {
        y -= zoom(10) - fm.getLeading();
      }
    for (String s : TimeAsString.get(timens, grade))
      {
        _zoom.drawString(g, s, x - fm.stringWidth(s)/2, y);
        if (below)
          {
            y += fm.getHeight()*7/8;
          }
        else
          {
            y -= fm.getHeight()*7/8;
          }
      }
  }


  /*---------------------------------------------------------------------*/


  /**
   * Top ruler that draws the scale.
   */
  Scala _topRuler = new Scala();


  @Override
  public JComponent topRuler()
  {
    return _topRuler;
  }

  /**
   * Component to draw scala as top ruler.
   */
  public class Scala extends JComponent
  {

    public Scala()
    {
      setPreferredSize(new java.awt.Dimension(1, _zoom.STANDARD_FONT_SIZE*7/8*5+16));
    }

    protected void paintComponent(Graphics g)
    {
      var r = g.getClipBounds();
      g.setColor(new Color(255, 255, 192));  // bright yellow background
      g.fillRect(r.x, r.y, r.width, r.height);

      SchedulingPanorama.this.drawScale(g,
                                        SchedulingPanorama.this.getVisibleRect(),
                                        64,
                                        false);
    }
  }


  /*---------------------------------------------------------------------*/


  /**
   * Left ruler that draws the thread names
   */
  ThreadNames _leftRuler = new ThreadNames();


  @Override
  public JComponent leftRuler()
  {
    return _leftRuler;
  }

  /**
   * Component to draw thread names as left ruler.
   */
  public class ThreadNames extends JComponent
  {

    public ThreadNames()
    {
      setPreferredSize(new java.awt.Dimension(_zoom.STANDARD_FONT_SIZE*10, 1));
    }

    protected void paintComponent(Graphics g)
    {
      var pr = SchedulingPanorama.this.getVisibleRect();
      var background = new Color(192, 192, 255); // bright blue background
      var backgroundave = (background.getRed() + background.getGreen() + background.getBlue())/3;
      var backgroundfactor = 32;
      var r = g.getClipBounds();
      g.setColor(background);
      g.fillRect(r.x, r.y, r.width, r.height);
      g.setColor(gray);
      g.drawLine(r.x+r.width-1, r.y,
                 r.x+r.width-1, r.y+r.height);

      for (var i = threadAt(r.y); threadY(i-1) <= r.y+r.height && i < _data._threads.size(); i++)
        {
          var t = _data._threads.get(i);
          var y = threadY(i);
          int h = threadY(i+1)-y+1;
          var cp = PROCESS_COLS[t._p._num % PROCESS_COLS.length];
          var c = new Color(Math.min(255, Math.max(0, cp.getRed  () + (background.getRed()   - backgroundave)*backgroundfactor/256)),
                            Math.min(255, Math.max(0, cp.getGreen() + (background.getGreen() - backgroundave)*backgroundfactor/256)),
                            Math.min(255, Math.max(0, cp.getBlue()  + (background.getBlue()  - backgroundave)*backgroundfactor/256)));
          g.setColor(c);
          g.fillRect(r.x, y-h/2, r.x+r.width-1, y+h/2);
          var from_a = actionAt(t, pr.x);

          if (_threadShown[i])
            {
              g.setColor(gray);
              g.setFont(_zoom.standardFont());
              _zoom.drawStringR(g, t.toString(from_a), r.x+r.width-3, y - zoom(2));
            }
          g.setColor(gray);
          _zoom.drawHLine(g,1,r.x,y,r.x+r.width);
        }
    }
  }


  /*---------------------------------------------------------------------*/


  /**
   * expand time scale
   *
   * @param cnt number of steps to perform, 0 for one big step.
   */
  public void expand(int cnt)
  {
    if (cnt == 0)
      {
        cnt = 10;
      }
    var step = Math.pow(ZOOM_STEP, cnt);
    if (width() * step < Integer.MAX_VALUE)
      {
        _timeScale = _timeScale * step;
      }
  }


  /**
   * compress time scale
   *
   * @param cnt number of steps to perform, 0 for one big step.
   */
  public void compress(int cnt)
  {
    if (cnt == 0)
      {
        cnt = 10;
      }
    var step = Math.pow(ZOOM_STEP, cnt);
    if (width() / step >= 256)
      {
        _timeScale = _timeScale / step;
      }
  }


  /**
   * zoom in
   *
   * @param cnt number of steps to perform, 0 for one big step.
   */
  public void zoomIn(int cnt)
  {
    if (cnt == 0)
      {
        cnt = 10;
      }
    var step = Math.pow(ZOOM_STEP, cnt);
    if ((dataHeight() < 1000000) && (width() < Integer.MAX_VALUE / step))
      {
        _zoom._zoomFactor = _zoom._zoomFactor * step;
      }
  }


  /**
   * zoom out
   *
   * @param cnt number of steps to perform, 0 for one big step.
   */
  public void zoomOut(int cnt)
  {
    if (cnt == 0)
      {
        cnt = 10;
      }
    var step = Math.pow(ZOOM_STEP, cnt);
    if ((zoom(NORMAL_THREAD_SPACING) > 3) && (width() > 100))
      {
        _zoom._zoomFactor = _zoom._zoomFactor / step;
      }
  }


  /*---------------------------------------------------------------------*/


  @Override
  public void rememberPosForScaling(int posx, int posy)
  {
    super.rememberPosForScaling(posx,posy);
    _rememberedMiddleNs = posx_to_nanos(posx);
    _rememberedMiddleThread = posy_to_thread(posy);
  }


  @Override
  public int recallX()
  { // NYI: CLEANUP: rememberPosForScaling receives translated posx/posy, while
    // recallX/recallY returns untranslated x/y posisiton. This should better
    // both be the same!
    return nanos_to_zoom_x(_rememberedMiddleNs);
  }



  @Override
  public int recallY()
  {
    return thread_to_posy(_rememberedMiddleThread) - topFrame();
  }

}
