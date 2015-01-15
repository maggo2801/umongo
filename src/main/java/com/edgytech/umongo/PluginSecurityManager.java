/**
 * Copyright (C) 2010 EdgyTech LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.edgytech.umongo;

/**
 *
 * @author antoine
 */
public class PluginSecurityManager extends SecurityManager {

  private String pluginDir = null;

  PluginSecurityManager(final String dir) {
    pluginDir = dir;
  }

  /**
   * This is the basic method that tests whether there is a class loaded by a
   * ClassLoader anywhere on the stack. If so, it means that that untrusted code
   * is trying to perform some kind of sensitive operation. We prevent it from
   * performing that operation by throwing an exception. trusted() is called by
   * most of the check...() methods below.
   */
  protected void trusted() {
    if (inClassLoader()) {
      throw new SecurityException();
    }
  }

  /**
   * These are all the specific checks that a security manager can perform. They
   * all just call one of the methods above and throw a SecurityException if the
   * operation is not allowed. This SecurityManager subclass is perhaps a little
   * too restrictive. For example, it doesn't allow loaded code to read *any*
   * system properties, even though some of them are quite harmless.
   */
  @Override
  public void checkCreateClassLoader() {
    trusted();
  }

  @Override
  public void checkAccess(final Thread g) {
    trusted();
  }

  @Override
  public void checkAccess(final ThreadGroup g) {
    trusted();
  }

  @Override
  public void checkExit(final int status) {
    trusted();
  }

  @Override
  public void checkExec(final String cmd) {
    trusted();
  }

  @Override
  public void checkLink(final String lib) {
    trusted();
  }

  @Override
  public void checkRead(final java.io.FileDescriptor fd) {
    trusted();
  }

  @Override
  public void checkRead(final String file) {
    // String path = new File(file).getParentFile().getAbsolutePath();
    // if (! path.endsWith(pluginDir))
    trusted();
  }

  @Override
  public void checkRead(final String file, final Object context) {
    trusted();
  }

  @Override
  public void checkWrite(final java.io.FileDescriptor fd) {
    trusted();
  }

  @Override
  public void checkWrite(final String file) {
    trusted();
  }

  @Override
  public void checkDelete(final String file) {
    trusted();
  }

  @Override
  public void checkConnect(final String host, final int port) {
    trusted();
  }

  @Override
  public void checkConnect(final String host, final int port, final Object context) {
    trusted();
  }

  @Override
  public void checkListen(final int port) {
    trusted();
  }

  @Override
  public void checkAccept(final String host, final int port) {
    trusted();
  }

  @Override
  public void checkMulticast(final java.net.InetAddress maddr) {
    trusted();
  }

  @Override
  public void checkMulticast(final java.net.InetAddress maddr, final byte ttl) {
    trusted();
  }

  @Override
  public void checkPropertiesAccess() {
    trusted();
  }

  @Override
  public void checkPropertyAccess(final String key) {
    // if (! key.equals("user.dir"))
    trusted();
  }

  @Override
  public void checkPrintJobAccess() {
    trusted();
  }

  @Override
  public void checkSystemClipboardAccess() {
    trusted();
  }

  @Override
  public void checkAwtEventQueueAccess() {
    trusted();
  }

  @Override
  public void checkSetFactory() {
    trusted();
  }

  @Override
  public void checkMemberAccess(final Class<?> clazz, final int which) {
    trusted();
  }

  @Override
  public void checkSecurityAccess(final String provider) {
    trusted();
  }

  /**
   * Loaded code can only load classes from java.* packages
   */
  @Override
  public void checkPackageAccess(final String pkg) {
    if (inClassLoader() && !pkg.startsWith("java.") && !pkg.startsWith("javax.")) {
      throw new SecurityException();
    }
  }

  /**
   * Loaded code can't define classes in java.* or sun.* packages
   */
  @Override
  public void checkPackageDefinition(final String pkg) {
    if (inClassLoader() && (pkg.startsWith("java.") || pkg.startsWith("javax.") || pkg.startsWith("sun."))) {
      throw new SecurityException();
    }
  }

  /**
   * This is the one SecurityManager method that is different from the others.
   * It indicates whether a top-level window should display an "untrusted"
   * warning. The window is always allowed to be created, so this method is not
   * normally meant to throw an exception. It should return true if the window
   * does not need to display the warning, and false if it does. In this
   * example, however, our text-based Service classes should never need to
   * create windows, so we will actually throw an exception to prevent any
   * windows from being opened.
   *
   */
  @Override
  public boolean checkTopLevelWindow(final Object window) {
    trusted();
    return true;
  }
}
