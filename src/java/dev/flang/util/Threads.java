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
 * Java source code of class dev.flang.util.Threads
 *
 *---------------------------------------------------------------------*/


package dev.flang.util;


/**
 * Alternatives for Object.wait/Thread.sleep/Thread.join with implicit handling
 * of {@link java.lang.InterruptedException}.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Threads
{


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Helper to call Thread.interrupted() and handle possible SecurityException
   * or Error.
   *
   * @return the current thread's interrupted state before it was cleared.
   */
  private static boolean getAndClearInterrupted()
  {
    try
      {
        return Thread.interrupted();
      }
    catch (SecurityException | Error ignore)
      {
        return false;
      }
  }


  /**
   * Helper to call {@code Thread.currentThread().interrupt()} conditionally.
   *
   * @param interrupted {@code true} iff {@code
   * Thread.currentThread.interrupt()} should be called.
   */
  private static void setInterrupted(boolean interrupted)
  {
    if (interrupted)
      {
        Thread.currentThread().interrupt();
      }
  }


  /**
   * Wait for a notify on a given object using {@link Object#wait(long, int)}
   * and ignore all interrupts().
   *
   * The interrupted state of the current thread will be ignored, but preserved,
   * i.e, we will wait even if interrupted.
   *
   * @param obj the object to wait on.
   *
   * @param ms milliseconds part of the timeout, 0 for none.
   *
   * @param ns nanoseconds part of the timeout, 0 for none.
   *
   * @throws IllegalMonitorStateException  if the current thread is not
   * the owner of obj's monitor.
   */
  public static void wait(Object obj, long ms, int ns)
  {
    boolean interrupted = getAndClearInterrupted();
    long start = ((ms == 0) && (ns == 0)) ? 0 : System.currentTimeMillis();
    var done = false;
    while (!done)
      {
        try
          {
            obj.wait(ms, ns);
            done = true;
          }
        catch (InterruptedException exception)
          {
            interrupted = true;
            if (ms != 0 || ns != 0)
              {
                long curTime = System.currentTimeMillis();
                ms = ms - (curTime - start);
                done = done || ((ms < 0) || ((ms == 0) && (ns == 0)));
                start = curTime;
              }
          }
      }
    setInterrupted(interrupted);
  }


  /**
   * Wait for a notify on a given object using {@link #wait(Object, long, int)}
   * {@code ms, 0} and ignore all interrupts().
   *
   * The interrupted state of the current thread will be ignored, but preserved,
   * i.e, we will wait even if interrupted.
   *
   * @param obj the object to wait on.
   *
   * @param ms milliseconds part of the timeout, 0 for none.
   */
  public static void wait(Object obj, long ms)
  {
    wait(obj, ms, 0);
  }


  /**
   * Wait for a notify on a given object using {@link #wait(Object, long, int)}
   * with arguments {@code 0, 0} and ignore all interrupts().
   *
   * Note that the interrupted state of the current thread will be ignored, but
   * preserved, i.e, we will wait even if interrupted.
   *
   * @param obj the object to wait on.
   */
  public static void wait(Object obj)
  {
    wait(obj, 0);
  }


  /**
   * Sleep using {@link Thread#sleep(long, int)} and ignore all interrupts().
   *
   * The interrupted state of the current thread will be ignored, but preserved,
   * i.e, we will wait even if interrupted.
   *
   * @param ms milliseconds part of the timeout.
   *
   * @param ns nanoseconds part of the timeout.
   */
  public static void sleep(long ms, int ns)
  {
    if (ms > 0 || ms == 0 && ns > 0)
      {
        var interrupted = getAndClearInterrupted();
        long start = System.currentTimeMillis();
        var done = false;
        while (!done)
          {
            try
              {
                Thread.sleep(ms, ns);
                done = true;
              }
            catch (InterruptedException exception)
              {
                interrupted = true;
                long curTime = System.currentTimeMillis();
                ms = ms - (curTime - start);
                start = curTime;
              }
          }
        while (ms >= 0);
        setInterrupted(interrupted);
      }
  }


  /**
   * Sleep using {@link #sleep(long, int)} and ignore all interrupts().
   *
   * @param ms milliseconds part of the timeout.
   */
  public static void sleep(long ms)
  {
    sleep(ms, 0);
  }


  /**
   * Wait for given thread to terminate.
   *
   * The interrupted state of the current thread will be ignored, but preserved,
   * i.e, we will wait even if interrupted.
   *
   * @param t the thread whose termination we are waiting for.
   */
  public static void join(Thread t)
  {
    var interrupted = getAndClearInterrupted();
    var done = false;
    while (!done)
      {
        try
          {
            t.join();
            done = true;
          }
        catch (InterruptedException exception)
          {
            interrupted = true;
          }
      }
    setInterrupted(interrupted);
  }

}
