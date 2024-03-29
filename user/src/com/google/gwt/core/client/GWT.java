/*
 * Copyright 2008 Google Inc.
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
package com.google.gwt.core.client;

import com.google.gwt.core.client.impl.Impl;

/**
 * Supports core functionality that in some cases requires direct support from
 * the compiler and runtime systems such as runtime type information and
 * deferred binding.
 */
public final class GWT {
  /**
   * This interface is used to catch exceptions at the "top level" just before
   * they escape to the browser. This is used in places where the browser calls
   * into user code such as event callbacks, timers, and RPC.
   *
   * In Development Mode, the default handler prints a stack trace to the log
   * window. In Production Mode, the default handler is null and thus exceptions
   * are allowed to escape, which provides an opportunity to use a JavaScript
   * debugger.
   */
  public interface UncaughtExceptionHandler {
    void onUncaughtException(Throwable e);
  }

  /**
   * An {@link UncaughtExceptionHandler} that logs errors to
   * {@link GWT#log(String, Throwable)}. This is the default exception handler
   * in Development Mode. In Production Mode, the default exception handler is
   * <code>null</code>.
   */
  private static final class DefaultUncaughtExceptionHandler implements
      UncaughtExceptionHandler {
    public void onUncaughtException(Throwable e) {
      log("Uncaught exception escaped", e);
    }
  }

  /**
   * This constant is used by {@link #getPermutationStrongName} when running in
   * Development Mode.
   */
  public static final String HOSTED_MODE_PERMUTATION_STRONG_NAME = "HostedMode";

  /**
   * Always <code>null</code> in Production Mode; in Development Mode provides
   * the implementation for certain methods.
   */
  private static GWTBridge sGWTBridge = null;

  /**
   * Defaults to <code>null</code> in Production Mode and an instance of
   * {@link DefaultUncaughtExceptionHandler} in Development Mode.
   */
  private static UncaughtExceptionHandler sUncaughtExceptionHandler = null;

  /**
   * Instantiates a class via deferred binding.
   * 
   * <p>
   * The argument to {@link #create(Class)}&#160;<i>must</i> be a class literal
   * because the Production Mode compiler must be able to statically determine
   * the requested type at compile-time. This can be tricky because using a
   * {@link Class} variable may appear to work correctly in Development Mode.
   * </p>
   * 
   * @param classLiteral a class literal specifying the base class to be
   *          instantiated
   * @return the new instance, which must be typecast to the requested class.
   */
  public static <T> T create(Class<?> classLiteral) {
    if (sGWTBridge == null) {
      /*
       * In Production Mode, the compiler directly replaces calls to this method
       * with a new Object() type expression of the correct rebound type.
       */
      throw new UnsupportedOperationException(
          "ERROR: GWT.create() is only usable in client code!  It cannot be called, "
              + "for example, from server code.  If you are running a unit test, "
              + "check that your test case extends GWTTestCase and that GWT.create() "
              + "is not called from within an initializer or constructor.");
    } else {
      return sGWTBridge.<T> create(classLiteral);
    }
  }

  /**
   * Gets the URL prefix of the hosting page, useful for prepending to relative
   * paths of resources which may be relative to the host page. Typically, you
   * should use {@link #getModuleBaseURL()} unless you have a specific reason to
   * load a resource relative to the host page.
   * 
   * @return if non-empty, the base URL is guaranteed to end with a slash
   */
  public static String getHostPageBaseURL() {
    return Impl.getHostPageBaseURL();
  }

  /**
   * Gets the URL prefix of the module which should be prepended to URLs that
   * are intended to be module-relative, such as RPC entry points and files in
   * the module's public path.
   * 
   * @return if non-empty, the base URL is guaranteed to end with a slash
   */
  public static String getModuleBaseURL() {
    return Impl.getModuleBaseURL();
  }

  /**
   * Gets the name of the running module.
   */
  public static String getModuleName() {
    return Impl.getModuleName();
  }

  /**
   * Returns the permutation's strong name. This can be used to distinguish
   * between different permutations of the same module. In Development Mode,
   * this method will return {@value #HOSTED_MODE_PERMUTATION_STRONG_NAME}.
   */
  public static String getPermutationStrongName() {
    if (GWT.isScript()) {
      return Impl.getPermutationStrongName();
    } else {
      return HOSTED_MODE_PERMUTATION_STRONG_NAME;
    }
  }

  /**
   * @deprecated Use {@link Object#getClass()}, {@link Class#getName()}
   */
  @Deprecated
  public static String getTypeName(Object o) {
    return (o == null) ? null : o.getClass().getName();
  }

  /**
   * Returns the currently active uncaughtExceptionHandler. "Top level" methods
   * that dispatch events from the browser into user code must call this method
   * on entry to get the active handler. If the active handler is null, the
   * entry point must allow exceptions to escape into the browser. If the
   * handler is non-null, exceptions must be caught and routed to the handler.
   * See the source code for <code>DOM.dispatchEvent()</code> for an example
   * of how to handle this correctly.
   * 
   * @return the currently active handler, or null if no handler is active.
   */
  public static UncaughtExceptionHandler getUncaughtExceptionHandler() {
    return sUncaughtExceptionHandler;
  }

  /**
   * Returns the empty string when running in Production Mode, but returns a
   * unique string for each thread in Development Mode (for example, different
   * windows accessing the dev mode server will each have a unique id, and
   * hitting refresh without restarting dev mode will result in a new unique id
   * for a particular window.
   *
   * TODO(unnurg): Remove this function once Dev Mode rewriting classes are in
   * gwt-dev.
   */
  public static String getUniqueThreadId() {
    if (sGWTBridge != null) {
      return sGWTBridge.getThreadUniqueID();
    }
    return "";
  }

  public static String getVersion() {
    if (sGWTBridge == null) {
      return getVersion0();
    } else {
      return sGWTBridge.getVersion();
    }
  }

  /**
   * Returns <code>true</code> when running inside the normal GWT environment,
   * either in Development Mode or Production Mode. Returns <code>false</code>
   * if this code is running in a plain JVM. This might happen when running
   * shared code on the server, or during the bootstrap sequence of a
   * GWTTestCase test.
   */
  public static boolean isClient() {
    // Replaced with "true" by GWT compiler.
    return sGWTBridge != null && sGWTBridge.isClient();
  }

  /**
   * Returns <code>true</code> when running in production mode. Returns
   * <code>false</code> when running either in development mode, or when running
   * in a plain JVM.
   */
  public static boolean isProdMode() {
    // Replaced with "true" by GWT compiler.
    return false;
  }

  /**
   * Determines whether or not the running program is script or bytecode.
   */
  public static boolean isScript() {
    // Replaced with "true" by GWT compiler.
    return false;
  }

  /**
   * Logs a message to the development shell logger in Development Mode. Calls
   * are optimized out in Production Mode.
   */
  public static void log(String message) {
    log(message, null);
  }

  /**
   * Logs a message to the development shell logger in Development Mode. Calls
   * are optimized out in Production Mode.
   */
  public static void log(String message, Throwable e) {
    if (sGWTBridge != null) {
      sGWTBridge.log(message, e);
    }
  }

  /**
   * The same as {@link #runAsync(RunAsyncCallback)}, except with an extra
   * parameter to provide a name for the call. The name parameter should be
   * supplied with a class literal. No two runAsync calls in the same program
   * should use the same name.
   */
  @SuppressWarnings("unused") // parameter will be used following replacement
  public static void runAsync(Class<?> name, RunAsyncCallback callback) {
    callback.onSuccess();
  }

  /**
   * Run the specified callback once the necessary code for it has been loaded.
   */
  public static void runAsync(RunAsyncCallback callback) {
    callback.onSuccess();
  }

  /**
   * Sets a custom uncaught exception handler. See
   * {@link #getUncaughtExceptionHandler()} for details.
   * 
   * @param handler the handler that should be called when an exception is about
   *          to escape to the browser, or <code>null</code> to clear the
   *          handler and allow exceptions to escape.
   */
  public static void setUncaughtExceptionHandler(
      UncaughtExceptionHandler handler) {
    sUncaughtExceptionHandler = handler;
  }

  /**
   * Called via reflection in Development Mode; do not ever call this method in
   * Production Mode.
   */
  static void setBridge(GWTBridge bridge) {
    sGWTBridge = bridge;
    if (bridge != null) {
      setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler());
    }
  }

  private static native String getVersion0() /*-{
    return $gwt_version;
  }-*/;
}
