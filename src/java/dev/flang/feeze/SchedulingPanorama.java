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

import java.util.ArrayList;
import java.util.TreeSet;

import javax.swing.JButton;
import javax.swing.JComponent;

import dev.flang.swing.Panorama;

import dev.flang.util.ANY;

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


  /**
   * Should there be a scale in the main scrolling area?
   */
  static final boolean SCALA_IN_MAIN_AREA = false;



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
  // static final Color[] PROCESS_COLS = new Color[] { bgcol, Color.WHITE };

  // purple, lilac, blue, cyan from normal to very bright
  static final int[][] TOKIWA_COLORS0 = { { 0x35286f, 0x6c4b99, 0x3da5da, 0x73bfb8 },
                                          { 0x43338c, 0x7a55ad, 0x3faae0, 0x7bccc5 },
                                          { 0x503da8, 0x895fc2, 0x41b0e8, 0x83d9d1 },
                                          { 0x5f48c7, 0x9769d6, 0x43b5f0, 0x8ae6dd },
                                          { 0x6c52e3, 0xa673eb, 0x45bbf7, 0x92f2e9 },
                                          { 0x7a5cff, 0xb47dff, 0x47c1ff, 0x9afff6 } };
  static final Color[][] TOKIWA_COLORS = new Color[TOKIWA_COLORS0.length][TOKIWA_COLORS0[0].length];
  static
  {
    for(var i = 0; i<TOKIWA_COLORS.length; i++)
      {
        var a = TOKIWA_COLORS[i];
        for(var j = 0; j<a.length; j++)
          {
            a[j] = new Color(TOKIWA_COLORS0[i][j]);
          }
      }
  }
  static final Color[][] TOKIWA_COLORS_PALE = new Color[5][4];
  static
  {
    for(var i = 0; i<4; i++)
      {
        var c = TOKIWA_COLORS[0][i];
        int r = c.getRed();
        int g = c.getGreen();
        int b = c.getBlue();
        for (var f = 0; f < 5; f++)
          {
            r = Math.min(255, (r + 255)/2);
            g = Math.min(255, (g + 255)/2);
            b = Math.min(255, (b + 255)/2);
            TOKIWA_COLORS_PALE[f][i] = new Color(r,g,b);
          }
      }
  }

  static final Color[] PROCESS_COLS = TOKIWA_COLORS_PALE[1];
  static final Color[][] PROCESS_COLS2 = TOKIWA_COLORS_PALE;
  static final Color[][] PROCESS_COLS3 = { { TOKIWA_COLORS_PALE[1][2], TOKIWA_COLORS_PALE[3][2], TOKIWA_COLORS_PALE[0][2] },   // blue
                                           { TOKIWA_COLORS_PALE[2][1], TOKIWA_COLORS_PALE[4][1], TOKIWA_COLORS_PALE[0][1] },   // lilac
                                           { TOKIWA_COLORS_PALE[1][3], TOKIWA_COLORS_PALE[3][3], TOKIWA_COLORS_PALE[0][3] },   // cyan
                                           { TOKIWA_COLORS_PALE[2][0], TOKIWA_COLORS_PALE[4][0], TOKIWA_COLORS_PALE[0][0] },   // purple
                                         };

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
  int[] _threadYTop;
  int[] _threadY;
  int[] _threadYBottom;



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
  long boot_ns_from_index(int  i          ) { if (ANY.PRECONDITIONS) ANY.require(_data.kind(i) == Offsets.ENTRY_KIND_SCHED_SWITCH);
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
    return zoom(NORMAL_THREAD_SPACING*(numThreads()+3));
  }


  ArrayList<SystemThread> _threads = null;

  /**
   * number of displayed threads
   */
  public int numThreads()
  {
    if (_threads == null)
      {
        _threads = new ArrayList();
        SystemUser u = null;
        for (var t : _data._threads)
          {
            if (u != t._p._user)
              {
                _threads.add(t);    // add cumulative pseudo-thread if needed.
                u = t._p._user;
              }
            _threads.add(t);   // add only if no cumulative pseudo-thread was added
          }
      }
    return _threads.size();
    // return _data._threads.size();
  }


  /**
   * Get the displayed thread with index i
   */
  public SystemThread thread(int i)
  {
    if (ANY.PRECONDITIONS) ANY.require
      (i >= 0,
       i < numThreads());

    return _threads.get(i);
  }


  /*---------------------------------------------------------------------*/


  // precalculated results of userNum() and processNum():
  //
  int[] _userNums;
  int[] _processNums;

  /**
   * The index of the user for given thread index ti.
   */
  int userNum(int ti)
  {
    if (ANY.PRECONDITIONS) ANY.require
      (ti >= 0 && ti < numThreads());

    if (_userNums == null)
      {
        _userNums = new int[numThreads()];
        _processNums = new int[numThreads()];
        SystemUser u = null;
        int un = -1;
        SystemProcess p = null;
        int pn = -1;
        for (var i = 0; i < numThreads(); i++)
          {
            var t = thread(i);
            if (u != t._p._user)
              {
                u = t._p._user;
                un++;
              }
            if (p != t._p)
              {
                p = t._p;
                pn++;
              }
            _userNums[i] = un;
            _processNums[i] = pn;
          }
      }
    return _userNums[ti];
  }


  /**
   * The number of the process of thread with index ti.
   */
  int processNum(int ti)
  {
    if (ANY.PRECONDITIONS) ANY.require
      (ti >= 0 && ti < numThreads());

    if (_processNums == null)
      {
        var ignore = userNum(ti);
      }
    return _processNums[ti];
  }


  /**
   * zoomed y coordinate of thread with given index.
   *
   * @param t a thread index, may be >=numThreads()
   *
   * @return the y coordinate of the horizontal line for this thread
   */
  int threadY(int t)
  {
    return threadY0(t)+topFrame();
  }
  int ti(int t)
  {
    if (_threadY == null)
      {
        var ignore = threadY0(t);
      }
    var l = _threadY.length;
    return Math.min(l-1, Math.max(0,t));
  }
  int threadYTop(int t)
  {
    var ti = ti(t);
    return _threadYTop[ti]+topFrame();
  }
  int threadYBottom(int t)
  {
    var ti = ti(t);
    return _threadYBottom[ti]+topFrame();
  }
  int threadY0(int t)
  {
    assert
      (t >= 0);

    var r = getVisibleRect(); // NYI: make this an argument
    var ts = SCALA_IN_MAIN_AREA ? 2*zoom(NORMAL_THREAD_SPACING) : 0;
    if (_lastThreadSpacing != ts || _lastPixelsPerNano != pixelsPerNano() ||
        _lastX != r.x || _lastW != r.width)
      {
        _threadYTop    = new int    [numThreads()];
        _threadY       = new int    [numThreads()];
        _threadYBottom = new int    [numThreads()];
        _threadShown = new boolean[numThreads()];
        double y = ts;
        for (var i = 0; i<numThreads(); i++)
          {
            var yd = threadYDelta(i, r);
            if (isFirstThreadOfUser(i))
              {
                y = y + zoomedUserNameHeight();
              }
            _threadYTop   [i] = (int) y; y = y + yd/2;
            _threadY      [i] = (int) y; y = y + yd/2;
            _threadYBottom[i] = (int) y;
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
   * Get the zoomed (but not translated) y position corresponding to the result
   * `t` that was obtained using `posy_to_thread`, i.e., after adjusting scaling
   * factors, get the new y position.
   */
  int thread_to_zoom_y(double t)
  {
    var ignore = threadY0(0);  // just interested in side effect of updating _threadY
    var ti = Math.min(Math.max(0, (int) t), _threadY.length-1);
    var ty = _threadY[ti];
    var delta = ti+1 < _threadY.length ? _threadY[ti+1]-ty : zoom((double) NORMAL_THREAD_SPACING);
    return (int) (ty + (t-ti) * delta);
  }


  /**
   * Get the y position corresponding to the result `t` that was obtained using
   * `posy_to_thread`, i.e., after adjusting scaling factors, get the new y position.
   */
  int thread_to_posy(double t)
  {
    return thread_to_zoom_y(t) + topFrame();
  }


  double zoomedUserNameHeight()
  {
    return (double) 2*Zoom.STANDARD_FONT_SIZE;
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
    var t = thread(i);
    double f = 1.0;
    if (t._tid == t._pid) // new process, so display it if any of its threads are shown
      {
        f = 0;
        int j = i;
        while (j < numThreads() && thread(j)._pid == t._tid)
          {
            f = Math.max(f, blendInFactor(thread(j), r));
            j++;
          }
      }
    else
      {
        f = blendInFactor(t, r);
      }
    _threadShown[i] = f==1;
    return
      //      (isFirstThreadOfUser(i) ? zoomedUserNameHeight() : 0) +
      //      (isFirstThreadOfProcess(i+1) ? zoom((double) NORMAL_THREAD_SPACING) : 0) +
      (1-f) * zoom((double) MIN_THREAD_SPACING) +
      (  f) * zoom((double) NORMAL_THREAD_SPACING);
  }


  boolean isFirstThreadOfUser(int i)
  {
    if (i == 0)
      {
        return true;
      }
    else if (i >= numThreads())
      {
        return false;
      }
    else
      {
        var tm1 = thread(i-1);
        var t   = thread(i);
        return t._p._user != tm1._p._user;
      }
  }
  boolean isFirstThreadOfProcess(int i)
  {
    if (i == 0)
      {
        return true;
      }
    else if (i >= numThreads())
      {
        return false;
      }
    else
      {
        var tm1 = thread(i-1);
        var t   = thread(i);
        return t._p != tm1._p;
      }
  }


  /**
   * For a given thread t, check if there are any relevant events shown in
   * visible rectangle r.  If so, return 1, otherwise, return a factor between 0
   * and 1 that corresponds to the distance the last event has to the visible
   * area.
   */
  double blendInFactor(SystemThread t, Rectangle r)
  {
    var til = actionAt(t, r.x        );
    var tim = actionAt(t, r.x+r.width);
    var tir = Math.min(t._num_actions-1, tim+1);
    var il = t._at[til];   // index of action left  of r.x
    var im = t._at[tim];   // index of action left  of r.x+r.width
    var ir = t._at[tir];   // index of action right of r.x+r.width
    double f = 1;
    if (il == im &&                      // no action within visible area
        (index_to_posx(il) >= r.x   ||
         _data.newThreadAt(il) != t   )  // and t is not running
        )
      {
        int xl = index_to_posx(il);
        int xr = index_to_posx(ir);
        int transition = r.width/2;  // width of the transition area during which we zoom threads in or out
        var fl = xl <  r.x         ? (double) Math.max(0, transition - (r.x-xl)) / transition :
                 xl <= r.x+r.width ? 1
                                   : 0;
        var fr = xr >  r.x+r.width ? (double) Math.max(0, transition - (xr-r.x-r.width)) / transition :
                 xr >= r.x         ? 1
                                   : 0;
        f = Math.max(fl,fr);
      }
    return f;
  }


  /**
   * Return the thread at the given y posisition.  Returns 0 if y is above all
   * threads and numThreads() if it is below all threads.
   *
   * @param y an actual y corrdinate.
   */
  int threadAt(int y)
  {
    var res = 0;
    while (res <= numThreads()-1 && y > threadYBottom(res))
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
            for (var i = threadAt(r.y); threadY(i-1) <= r.y+r.height && i < numThreads(); i++)
              {
                int drawCnt = 0;
                int drawCnt2 = 0;
                var last_x = x0;
                var t = thread(i);
                var y = threadY(i);
                var yt = threadYTop(i);
                var yb = threadYBottom(i);

                if (isFirstThreadOfUser(i))
                  {
                    var fc = PROCESS_COLS3[(1+userNum(i)) % 2][2];
                    g.setColor(fc);
                    g.fillRect(r.x, yt-(int) zoomedUserNameHeight(), r.x+r.width-1, yt);
                  }

                int h = threadY(i+1)-y+1;
                //                g.setColor(PROCESS_COLS2[processNum(i)*3 % 5][1 + (userNum(i) % 3)]);
                g.setColor(PROCESS_COLS3[((1+userNum(i)) % 2)][processNum(i)%2]);
                g.fillRect(r.x, yt, r.x+r.width-1, yb);

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
                    var y0 = threadY(0             ) - zoom(NORMAL_THREAD_SPACING);
                    var y1 = threadY(numThreads()-1) + zoom(NORMAL_THREAD_SPACING);
                    while (al >= 0 && _data.kind(al) != Offsets.ENTRY_KIND_SCHED_SWITCH)
                      {
                        al--;
                      }
                    g.fillRect(xmin,y0,xmax-xmin+1,y1-y0+1);
                  }
              }
          }
      }

    if (SCALA_IN_MAIN_AREA)  //  additional scala in the data area
      {
        drawScale(g, r, threadY(0) - zoom(NORMAL_THREAD_SPACING), false);
      }
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

      for (var i = Math.max(0, threadAt(r.y)-1); threadY(i-1) <= r.y+r.height && i < numThreads(); i++)
        {
          var t = thread(i);
          var y = threadY(i);
          var yt = threadYTop(i);
          var yb = threadYBottom(i);
          int h = threadY(i+1)-y+1;
          if (isFirstThreadOfUser(i))
            {
              var fc = PROCESS_COLS3[(1+userNum(i)) % 2][2];
              g.setColor(fc);
              g.fillRect(r.x, yt-(int) zoomedUserNameHeight(), r.x+r.width-1, yt);
              g.setColor(Color.white);
              _zoom.drawString(g, t._p._user._name, 3, yt - (int) zoomedUserNameHeight()/6);
            }
          var cp = false ? PROCESS_COLS[t._p._num % PROCESS_COLS.length]            :  // NYI: cleanup when display is stable
                   false ? PROCESS_COLS2[processNum(i)*3 % 5][1 + (userNum(i) % 3)]
                         : PROCESS_COLS3[(1+userNum(i)) % 2][processNum(i)%2];
          var c = new Color(Math.min(255, Math.max(0, cp.getRed  () + (background.getRed()   - backgroundave)*backgroundfactor/256)),
                            Math.min(255, Math.max(0, cp.getGreen() + (background.getGreen() - backgroundave)*backgroundfactor/256)),
                            Math.min(255, Math.max(0, cp.getBlue()  + (background.getBlue()  - backgroundave)*backgroundfactor/256)));
          g.setColor(c);
          g.fillRect(r.x, yt, r.x+r.width-1, yb);
          var from_a = actionAt(t, pr.x);

          if (_threadShown[i])
            {
              g.setColor(gray);
              g.setFont(_zoom.standardFont());
              _zoom.drawStringR(g, t.toString(from_a), r.x+r.width-3, y - zoom(2));
              if (isFirstThreadOfUser(i))
                {
                }
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
  { // NYI: CLEANUP: rememberPosForScaling receives translated posx/posy, while
    // recallX/recallY returns untranslated x/y posisiton. This should better
    // both be the same!
    return thread_to_zoom_y(_rememberedMiddleThread);
  }

}
