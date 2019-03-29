/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.dubbo.rpc.cluster.support;

import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.threadlocal.NamedInternalThreadFactory;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.RpcResult;
import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.cluster.Directory;
import com.alibaba.dubbo.rpc.cluster.LoadBalance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * When fails, record failure requests and schedule for retry on a regular interval.
 * Especially useful for services of notification.
 *
 * <a href="http://en.wikipedia.org/wiki/Failback">Failback</a>
 * 实现 Cluster 接口，失败自动恢复，后台记录失败请求
 * 定时重发，通常用于消息通知操作
 */
public class FailbackClusterInvoker<T> extends AbstractClusterInvoker<T> {

    private static final Logger logger = LoggerFactory.getLogger(FailbackClusterInvoker.class);

    // 重试频率
    private static final long RETRY_FAILED_PERIOD = 5 * 1000;

    /**
     * 定时任务线程池
     * Use {@link NamedInternalThreadFactory} to produce {@link com.alibaba.dubbo.common.threadlocal.InternalThread}
     * which with the use of {@link com.alibaba.dubbo.common.threadlocal.InternalThreadLocal} in {@link RpcContext}.
     */
    private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(2,
            new NamedInternalThreadFactory("failback-cluster-timer", true));

    // 失败任务集合
    private final ConcurrentMap<Invocation, AbstractClusterInvoker<?>> failed = new ConcurrentHashMap<Invocation, AbstractClusterInvoker<?>>();
    // 重试 Future
    private volatile ScheduledFuture<?> retryFuture;

    public FailbackClusterInvoker(Directory<T> directory) {
        super(directory);
    }

    private void addFailed(Invocation invocation, AbstractClusterInvoker<?> router) {
        // 若定时任务未初始化，进行创建
        if (retryFuture == null) {
            synchronized (this) {
                if (retryFuture == null) {
                    // 定时重试失败的任务
                    retryFuture = scheduledExecutorService.scheduleWithFixedDelay(new Runnable() {

                        @Override
                        public void run() {
                            // collect retry statistics
                            try {
                                retryFailed();
                            } catch (Throwable t) { // Defensive fault tolerance
                                logger.error("Unexpected error occur at collect statistic", t);
                            }
                        }
                    }, RETRY_FAILED_PERIOD, RETRY_FAILED_PERIOD, TimeUnit.MILLISECONDS);
                }
            }
        }
        // 若创建了，则加入 failed
        failed.put(invocation, router);
    }

    void retryFailed() {
        // 如果没有需要失败重试的任务这返回
        if (failed.size() == 0) {
            return;
        }
        // 遍历所有的失败任务，并且重试，重试一次就移除
        for (Map.Entry<Invocation, AbstractClusterInvoker<?>> entry : new HashMap<Invocation, AbstractClusterInvoker<?>>(
                failed).entrySet()) {
            Invocation invocation = entry.getKey();
            Invoker<?> invoker = entry.getValue();
            try {
                // 调用
                invoker.invoke(invocation);
                // 移除
                failed.remove(invocation);
            } catch (Throwable e) {
                logger.error("Failed retry to invoke method " + invocation.getMethodName() + ", waiting again.", e);
            }
        }
    }

    @Override
    protected Result doInvoke(Invocation invocation, List<Invoker<T>> invokers, LoadBalance loadbalance) throws RpcException {
        try {
            // 检查 invokers
            checkInvokers(invokers, invocation);
            // 根据负载均衡策略挑选出 invoker
            Invoker<T> invoker = select(loadbalance, invocation, invokers, null);
            // 调用返回结果
            return invoker.invoke(invocation);
        } catch (Throwable e) {
            logger.error("Failback to invoke method " + invocation.getMethodName() + ", wait for retry in background. Ignored exception: "
                    + e.getMessage() + ", ", e);
            // 添加失败日志
            addFailed(invocation, this);
            return new RpcResult(); // ignore
        }
    }

}
