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

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.threadlocal.NamedInternalThreadFactory;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.cluster.Directory;
import com.alibaba.dubbo.rpc.cluster.LoadBalance;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Invoke a specific number of invokers concurrently, usually used for demanding real-time operations, but need to waste more service resources.
 *
 * <a href="http://en.wikipedia.org/wiki/Fork_(topology)">Fork</a>
 *
 */
public class ForkingClusterInvoker<T> extends AbstractClusterInvoker<T> {

    /**
     * Use {@link NamedInternalThreadFactory} to produce {@link com.alibaba.dubbo.common.threadlocal.InternalThread}
     * which with the use of {@link com.alibaba.dubbo.common.threadlocal.InternalThreadLocal} in {@link RpcContext}.
     */
    private final ExecutorService executor = Executors.newCachedThreadPool(
            new NamedInternalThreadFactory("forking-cluster-timer", true));

    public ForkingClusterInvoker(Directory<T> directory) {
        super(directory);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Result doInvoke(final Invocation invocation, List<Invoker<T>> invokers, LoadBalance loadbalance) throws RpcException {
        try {
            // 校验 invoker 是否可用
            checkInvokers(invokers, invocation);
            final List<Invoker<T>> selected;
            // 获取最大并行数，默认为 2
            final int forks = getUrl().getParameter(Constants.FORKS_KEY, Constants.DEFAULT_FORKS);
            // 获取超时时间
            final int timeout = getUrl().getParameter(Constants.TIMEOUT_KEY, Constants.DEFAULT_TIMEOUT);
            // 若 forks 小于等于 0，或者并行数大于等于 invoker 数量，直接使用 invokers
            if (forks <= 0 || forks >= invokers.size()) {
                selected = invokers;
            } else {
                // 获取到的服务不能重复
                // 因为添加了去重的逻辑，所以可能存在 forks 数量依然大于 Invoker 数量
                // 存在重复调用问题
                selected = new ArrayList<Invoker<T>>();
                for (int i = 0; i < forks; i++) {
                    // TODO. Add some comment here, refer chinese version for more details.
                    Invoker<T> invoker = select(loadbalance, invocation, invokers, selected);
                    if (!selected.contains(invoker)) {//Avoid add the same invoker several times.
                        selected.add(invoker);
                    }
                }
            }
            // 设置以及调用的 Invoker 集合，到 Context 中
            RpcContext.getContext().setInvokers((List) selected);
            // 添加异常计数器
            final AtomicInteger count = new AtomicInteger();
            // 创建获取到的结果的阻塞队列
            final BlockingQueue<Object> ref = new LinkedBlockingQueue<Object>();
            // 线程池去执行 selected 中的 invoker
            for (final Invoker<T> invoker : selected) {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            // 调用并且返回结果
                            Result result = invoker.invoke(invocation);
                            // 放入阻塞队列
                            ref.offer(result);
                        } catch (Throwable e) {
                            // 如果出现异常
                            // 异常数 +1
                            int value = count.incrementAndGet();
                            // 异常数量大于等于需要调用服务器数量了，说明都错了，无需继续调用
                            // 队列中放入异常
                            if (value >= selected.size()) {
                                ref.offer(e);
                            }
                        }
                    }
                });
            }
            try {
                // 阻塞获取结果，谁先返回就是谁
                Object ret = ref.poll(timeout, TimeUnit.MILLISECONDS);
                if (ret instanceof Throwable) {
                    // 异常抛出
                    Throwable e = (Throwable) ret;
                    throw new RpcException(e instanceof RpcException ? ((RpcException) e).getCode() : 0, "Failed to forking invoke provider " + selected + ", but no luck to perform the invocation. Last error is: " + e.getMessage(), e.getCause() != null ? e.getCause() : e);
                }
                return (Result) ret;
            } catch (InterruptedException e) {
                throw new RpcException("Failed to forking invoke provider " + selected + ", but no luck to perform the invocation. Last error is: " + e.getMessage(), e);
            }
        } finally {
            // clear attachments which is binding to current thread.
            RpcContext.getContext().clearAttachments();
        }
    }
}
