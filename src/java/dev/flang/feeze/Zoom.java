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
 * Java source code of class dev.flang.feeze.Zoom
 *
 *---------------------------------------------------------------------*/


package dev.flang.feeze;

import java.awt.Font;
import java.awt.Graphics;

/*---------------------------------------------------------------------*/


/**
 * Zoom is a helper class that contains the drawing functions that can be zoomed
 * by a factor.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class Zoom
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * Size this is zoomed to, 1.0 is the default size.
   */
  double _zoomFactor;


  /**
   * The base font for drawing text.
   */
  Font _baseFont = new Font("Open Sans",Font.PLAIN, 12);


  /**
   * Fonts cache
   */
  Font[] _fonts = null;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create Zoom with intial zoom factor 1.
   */
  Zoom()
  {
    _zoomFactor = 1;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * zoom a given integer length l.  This function uses integers and rounds the
   * result to the nearest but never rounds to 0, but to +/-1 instead to avoid
   * supressing the display of very thin events.
   *
   * @return the zoomed length
   */
  int zoom(int l)
  {
    var zl = (int) (zoom((double) l) + 0.5);
    return
      l > 0 ? Math.max( 1, zl) :
      l < 0 ? Math.min(-1, zl)
            : 0;
  }


  /**
   * zoom a given length l using doubles.
   *
   * @return the zoomed length
   */
  double zoom(double l)
  {
    return l * _zoomFactor;
  }


  /**
   * unzoom a length given as an integer. Round to the nearest integer.
   *
   * @param zl a zoomed length (created by {@code zoom()})
   *
   * @return the original length
   */
  int unzoom(int zl)
  {
    return (int) (unzoom(zl) + 0.5);
  }


  /**
   * unzoom a given length using doubles.
   *
   * @param zl a zoomed length (created by {@code zoom()})
   *
   * @return the original length
   */
  double unzoom(double zl)
  {
    return zl / _zoomFactor;
  }


  /**
   * Draw a given line
   *
   * @param g graphics environment
   *
   * @param width the unzoomed width of the line
   *
   * @param x1 the zoomed (!) x coordinate of the start
   *
   * @param y1 the zoomed (!) y coordinate of the start
   *
   * @param x2 the zoomed (!) x coordinate of the end
   *
   * @param y2 the zoomed (!) y coordinate of the end
   */
  void drawLine(Graphics g,
                int width,
                int x1,
                int y1,
                int x2,
                int y2)
  {
    width = zoom(width);
    int dx = Math.abs(x1-x2);
    int dy = Math.abs(y1-y2);
    for (int i = 0; i < width; i++)
      {
        if (dx >= dy)
          {
            g.drawLine(x1, y1-(width/2)+i,
                       x2, y2-(width/2)+i);
          }
        else
          {
            g.drawLine(x1-(width/2)+i, y1,
                       x2-(width/2)+i, y2);
          }
      }
  }


  /**
   * draws horizontal line
   *
   * @param g graphics environment
   *
   * @param width the unzoomed width of the line
   *
   * @param x1 the zoomed (!) x coordinate of the start
   *
   * @param y the zoomed (!) y coordinate
   *
   * @param x2 the zoomed (!) x coordinate of the end
   */
  void drawHLine(Graphics g,
                 int width,
                 int x1,
                 int y1,
                 int x2)
  {
    width = zoom(width);
    y1 -= width/2;
    for (int i=0; i<width; i++)
      {
        g.drawLine(x1, y1+i,
                   x2, y1+i);
      }
  }


  /**
   * the standard fron
   *
   * @return the standard font
   */
  Font standardFont()
  {
    return font(12);
  }


  /**
   * font of given size
   *
   * @param s the size, will not be zoomed.
   *
   * @return the corresponding font.
   */
  Font font(int s)
  {
    int N = 100;
    if (_fonts == null)
      {
        _fonts = new Font[N];
      }
    s = Math.max(N-1, s);
    Font f = _fonts[s];
    if (f == null)
      {
        f = _baseFont.deriveFont(s);
        _fonts[s] = f;
      }
    return f;
  }


  /**
   * drawString draws a given string
   *
   * @param g graphics environment
   *
   * @param str the string
   *
   * @param x the zoomed (!) x coordinate
   *
   * @param < the zoomed (!) y coordinate
   */
  void drawString(Graphics g,
                  String str,
                  int x,
                  int y)
  {
    g.setFont(standardFont());
    g.drawString(str,x,y);
  }


}
