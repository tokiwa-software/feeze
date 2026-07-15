/*

This file is part of the Feeze scheduling analysis tool.

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
 * Java source code of class dev.flang.feeze.FeezeToolTip
 *
 *---------------------------------------------------------------------*/


package dev.flang.feeze;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Rectangle;

import javax.swing.CellRendererPane;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolTip;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;

import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;

import dev.flang.util.ANY;

/*---------------------------------------------------------------------*/


/**
 * FeezeToolTip is a JToolTip used to diplay thread detail information
 * in a SchedulingPanorama.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class FeezeToolTip extends JToolTip
{

  /*------------------------------  static  -----------------------------*/


  static
  {
    ToolTipManager.sharedInstance().setDismissDelay(20000);
  }


  /*------------------------------  fields  -----------------------------*/


  private final JComponent _renderer  = new JPanel();
  private final CellRendererPane _renderPane = new CellRendererPane();

  JLabel _nameLabel  = new JLabel("name");
  JLabel _stateLabel = new JLabel("state");
  JLabel _cpuLabel   = new JLabel("CPU");
  JLabel _timeLabel  = new JLabel("time");


  /*---------------------------  constructors  --------------------------*/


  /**
   * Constructor for tooltip for given SchedulingPanorama
   *
   * @param p a SchedulingPanorama for which tooltips should be displayed in the
   * data area.
   */
  FeezeToolTip(SchedulingPanorama p)
  {
    if (ANY.PRECONDITIONS) ANY.require
      (p != null);

    setComponent(p);

    var tooltipBorder = UIManager.getBorder("ToolTip.border");
    var tooltipFont   = UIManager.getFont("ToolTip.font");
    var tooltiFontBold = tooltipFont.deriveFont(Font.BOLD);
    _renderer.setBorder(new CompoundBorder(tooltipBorder,new EmptyBorder(2,2,2,2)));
    _renderer.setLayout(new GridLayout(0,1));

    _nameLabel.setFont(tooltiFontBold);
    _renderer.add(_nameLabel);

    _stateLabel.setFont(tooltipFont);
    _renderer.add(_stateLabel);

    _cpuLabel.setFont(tooltipFont);
    _renderer.add(_cpuLabel);

    _timeLabel.setFont(tooltipFont);
    _renderer.add(_timeLabel);

    ToolTipManager.sharedInstance().registerComponent(p);
  }


  /**
   * @see javax.swing.JComponent#paintComponent(java.awt.Graphics)
   */
  @Override
  protected void paintComponent(Graphics g) {
    _renderPane.paintComponent(g, _renderer, this.getParent(), g.getClipBounds());
  }


  /**
   * @see java.awt.Container#doLayout()
   */
  @Override
  public void doLayout() {
    _renderer.doLayout();
  }


  /**
   * @return
   * @see javax.swing.JComponent#getMaximumSize()
   */
  @Override
  public Dimension getMaximumSize() {
    return _renderer.getMaximumSize();
  }


  /**
   * @return
   * @see javax.swing.JComponent#getMinimumSize()
   */
  @Override
  public Dimension getMinimumSize() {
    return _renderer.getMinimumSize();
  }


  /**
   * @return
   * @see javax.swing.JComponent#getPreferredSize()
   */
  @Override
  public Dimension getPreferredSize() {
    return _renderer.getPreferredSize();
  }


  /**
   * @param x
   * @param y
   * @param w
   * @param h
   * @see javax.swing.JComponent#setBounds(int, int, int, int)
   */
  @Override
  public void setBounds(int x, int y, int w, int h) {
    _renderer.setBounds(x, y, w, h);
    super.setBounds(x, y, w, h);
  }

}
