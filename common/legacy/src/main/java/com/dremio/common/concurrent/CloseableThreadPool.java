/*
 * Copyright (C) 2017-2019 Dremio Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dremio.common.concurrent;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Closeable thread pool. This is basically Executors.newCachedThreadPool() but also using a
 * NamedThreadFactory and calling awaitTermination on close.
 */
public class CloseableThreadPool extends ThreadPoolExecutor implements CloseableExecutorService {
  private static final org.slf4j.Logger logger =
      org.slf4j.LoggerFactory.getLogger(CloseableThreadPool.class);

  public CloseableThreadPool(String name) {
    super(
        0,
        Integer.MAX_VALUE,
        60L,
        TimeUnit.SECONDS,
        new SynchronousQueue<>(),
        new NamedThreadFactory(name));
  }

  public CloseableThreadPool(String name, int corePoolSize, RejectedExecutionHandler handler) {
    super(
        corePoolSize,
        corePoolSize,
        60L,
        TimeUnit.SECONDS,
        new SynchronousQueue<>(),
        new NamedThreadFactory(name),
        handler);
  }

  @Override
  protected void afterExecute(final Runnable r, final Throwable t) {
    if (t != null) {
      logger.error("{}.run() leaked an exception.", r.getClass().getName(), t);
    }
    super.afterExecute(r, t);
  }

  @Override
  public void close() {
    CloseableSchedulerThreadPool.close(this, logger);
  }
}
