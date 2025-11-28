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
 * Java source code of class dev.flang.feeze.TimeAsString
 *
 *---------------------------------------------------------------------*/


package dev.flang.feeze;


/*---------------------------------------------------------------------*/


/**
 * TimeAsString provides means to convert nanoseconds to Strings
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class TimeAsString
{

  /**
   * Names of time units.
   */
  static String[] _unitNames_ = new String[] { "ns", "us", "ms", "s", "min", "h", "d" , "a", "ka", "?"};


  /**
   * Factors between time units. i.e., _unitNames_[4] == "min" and
   * _unitNames_[3] == "s", and there are 60 seconds per minute, so
   * _unitFactors_[3] == 60. Terminated by Long.MAX_VALUE.
   */
  static long  [] _unitFactors_ = new long[] { 1000, 1000, 1000, 60 , 60   , 24 , 365, 1000, Long.MAX_VALUE};

  /**
   * Number of nanoseconds in one time unit.  E.g., _unitNames_[4] == "min",
   * so _unitNs_[4] == 60000000000L, the number of ns in a minute.  Terminated
   * by Long.MAX_VALUE.
   */
  static long  [] _unitNs_ = new long[_unitFactors_.length+1];
  static
  {
    int i;
    long ns = 1;
    for (i=0; i<_unitFactors_.length; i++)
      {
        _unitNs_[i] = ns;
        ns *= _unitFactors_[i];
      }
    _unitNs_[i] = Long.MAX_VALUE;
  }


  /**
   * Create a sequence of Strings with human-readable units from a time given in ns.
   *
   * @param timens time in nanoseconds
   *
   * @param grade precision in nanoseconds, should be a power of 10
   *
   * @return time-unit entries, starts with least significant entry
   */
  static String[] get(long timens, long grade)
  {
    if (grade <= 0)
      {
        throw new IllegalArgumentException("grade <= 0"); /* should never happen */
      }

    int unit = 0;
    while (grade % _unitNs_[unit + 1] == 0)
      {
        unit++;
      }

    int base = unit;
    long ns = timens;
    do
      {
        long t = ns % _unitNs_[unit + 1];
        ns -= t;
        unit++;
      }
    while (ns > 0);
    String[] result = new String[unit - base];

    unit = base;
    ns = timens;
    do
      {
        long t = ns % _unitNs_[unit + 1];
        result[unit - base] = stringUnit(t, unit);
        ns -= t;
        unit++;
      }
    while (ns > 0);
    return result;
  }


  /**
   * Converts time to String using given unit.
   *
   * @param ns time in nanoseconds
   *
   * @param unit reference to {@link #_unitNames_}
   *
   * @return string consisting of time truncated to unit followed by unit symbol
   */
  static String stringUnit(long ns, int unit)
  {
    String unitName = _unitNames_[unit];
    long   value    = ns / _unitNs_[unit];
    return Long.toString(value) + unitName;
  }

}
