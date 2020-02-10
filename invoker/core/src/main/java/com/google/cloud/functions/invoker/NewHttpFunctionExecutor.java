// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.cloud.functions.invoker;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.invoker.http.HttpRequestImpl;
import com.google.cloud.functions.invoker.http.HttpResponseImpl;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Executes the user's method. */
public class NewHttpFunctionExecutor extends HttpServlet {
  private static final Logger logger = Logger.getLogger("com.google.cloud.functions.invoker");

  private final HttpFunction function;

  private NewHttpFunctionExecutor(HttpFunction function) {
    this.function = function;
  }

  /**
   * Makes a {@link NewHttpFunctionExecutor} for the given class.
   *
   * @throws RuntimeException if either the given class does not implement {@link HttpFunction}
   *    or we are unable to construct an instance using its no-arg constructor.
   */
  public static NewHttpFunctionExecutor forClass(Class<?> functionClass) {
    if (!HttpFunction.class.isAssignableFrom(functionClass)) {
      throw new RuntimeException(
          "Class " + functionClass.getName() + " does not implement "
              + HttpFunction.class.getName());
    }
    Class<? extends HttpFunction> httpFunctionClass = functionClass.asSubclass(HttpFunction.class);
    try {
      HttpFunction httpFunction = httpFunctionClass.getConstructor().newInstance();
      return new NewHttpFunctionExecutor(httpFunction);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(
          "Could not construct an instance of " + functionClass.getName() + ": " + e, e);
    }
  }

  /** Executes the user's method, can handle all HTTP type methods. */
  @Override
  public void service(HttpServletRequest req, HttpServletResponse res) {
    URLRequestWrapper wrapper = new URLRequestWrapper(req);
    HttpRequestImpl reqImpl = new HttpRequestImpl(wrapper);
    HttpResponseImpl respImpl = new HttpResponseImpl(res);
    try {
      function.service(reqImpl, respImpl);
    } catch (Throwable t) {
      // TODO(b/146510646): this should be logged properly as an exception, but that currently
      //   causes integration tests to fail.
      // logger.log(Level.WARNING, "Failed to execute " + function.getClass().getName(), t);
      logger.log(Level.WARNING, "Failed to execute {0}", function.getClass().getName());
      t.printStackTrace();
      res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } finally {
      try {
        respImpl.getWriter().flush();
      } catch (IOException e) {
        // Too bad, can't flush.
      }
    }
  }
}