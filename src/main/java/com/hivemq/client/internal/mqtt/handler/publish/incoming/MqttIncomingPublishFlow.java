/*
 * Copyright 2018-present HiveMQ and the HiveMQ Community
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

package com.hivemq.client.internal.mqtt.handler.publish.incoming;

import com.hivemq.client.internal.annotations.CallByThread;
import com.hivemq.client.internal.mqtt.MqttClientConfig;
import com.hivemq.client.internal.mqtt.handler.util.FlowWithEventLoop;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import io.reactivex.Emitter;
import io.reactivex.internal.util.BackpressureHelper;
import io.reactivex.plugins.RxJavaPlugins;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Silvio Giebl
 * 目测这个类对应着一个订阅方
 */
abstract class MqttIncomingPublishFlow extends FlowWithEventLoop
        implements Emitter<Mqtt5Publish>, Subscription, Runnable {

    private static final int STATE_NO_NEW_REQUESTS = 0;
    private static final int STATE_NEW_REQUESTS = 1;
    private static final int STATE_BLOCKED = 2;

    final @NotNull Subscriber<? super Mqtt5Publish> subscriber;
    final @NotNull MqttIncomingPublishService incomingPublishService;
    final boolean manualAcknowledgement;

    private long requested;
    private final @NotNull AtomicLong newRequested = new AtomicLong();
    private final @NotNull AtomicInteger requestState = new AtomicInteger(STATE_NO_NEW_REQUESTS);

    // 只有在unsubscribe（退订）和出错的时候，done才会被设置为true
    private boolean done;
    private @Nullable Throwable error;
    // 这里的referenced代指的应该是当前订阅者接收到并将要处理的消息的数量
    private int referenced;
    // 我现在的理解是，这个代表了当前订阅方已经匹配上但还没有ack的消息的数量
    private int missingAcknowledgements;
    private long blockedIndex;
    private boolean blocking;

    MqttIncomingPublishFlow(
            final @NotNull Subscriber<? super Mqtt5Publish> subscriber,
            final @NotNull MqttClientConfig clientConfig,
            final @NotNull MqttIncomingQosHandler incomingQosHandler,
            final boolean manualAcknowledgement) {

        super(clientConfig);
        this.subscriber = subscriber;
        incomingPublishService = incomingQosHandler.incomingPublishService;
        this.manualAcknowledgement = manualAcknowledgement;
    }

    // 这种CallByThread注解仅仅是标识作用
    @CallByThread("Netty EventLoop")
    @Override
    public void onNext(final @NotNull Mqtt5Publish result) {
        subscriber.onNext(result);
        if (requested != Long.MAX_VALUE) {
            requested--;
        }
    }

    @CallByThread("Netty EventLoop")
    @Override
    public void onComplete() {
        if (done) {
            return;
        }
        done = true;
        if (setDone()) {
            subscriber.onComplete();
        } else {
            incomingPublishService.drain();
        }
    }

    @CallByThread("Netty EventLoop")
    @Override
    public void onError(final @NotNull Throwable error) {
        if (done) {
            // multiple calls with the same error are expected if flow was subscribed with multiple topic filters
            if (error != this.error) {
                RxJavaPlugins.onError(error);
            }
            return;
        }
        this.error = error;
        done = true;
        if (setDone()) {
            subscriber.onError(error);
        } else {
            incomingPublishService.drain();
        }
    }

    @Override
    protected boolean setDone() {
        return (referenced == 0) && (missingAcknowledgements == 0) && super.setDone();
    }

    @CallByThread("Netty EventLoop")
    void checkDone() {
        if (done && setDone()) {
            if (error != null) {
                subscriber.onError(error);
            } else {
                subscriber.onComplete();
            }
        }
    }

    @Override
    public void request(final long n) {
        if ((n > 0) && !isCancelled()) {
            BackpressureHelper.add(newRequested, n);
            if (requestState.getAndSet(STATE_NEW_REQUESTS) == STATE_BLOCKED) {
                eventLoop.execute(this);
                // event loop is acquired even if done:
                // - cancelled is checked
                // - onComplete/onError wait for the queue to be empty -> requestState != STATE_BLOCKED
            }
        }
    }

    @CallByThread("Netty EventLoop")
    @Override
    public void run() { // only executed if was blocking
        if (referenced > 0) { // is blocking
            incomingPublishService.drain();
        }
    }

    @CallByThread("Netty EventLoop")
    long requested(final long runIndex) {
        if (requested <= 0) {
            if (blocking && (blockedIndex != runIndex)) {
                blocking = false; // unblock in a new run iteration
            }
            if (blocking) {
                return -1;
            } else {
                for (; ; ) { // setting both requestState and newRequested is not atomic
                    if (requestState.compareAndSet(STATE_NO_NEW_REQUESTS, STATE_BLOCKED)) {
                        blockedIndex = runIndex;
                        blocking = true;
                        return 0;
                    } else { // requestState = STATE_NEW_REQUESTS
                        requestState.set(STATE_NO_NEW_REQUESTS);
                        final long newRequested = this.newRequested.getAndSet(0);
                        // If request was called concurrently we may have included the newRequested amount already but
                        // requestState is afterwards set to STATE_NEW_REQUESTS although newRequested is reset to 0.
                        // If request is not called until the next invocation of this method, newRequested may be 0.
                        if (newRequested > 0) {
                            return requested = BackpressureHelper.addCap(requested, newRequested);
                        }
                    }
                }
            }
        }
        return requested;
    }

    @Override
    protected void onCancel() {
        eventLoop.execute(this::runCancel);
    }

    @CallByThread("Netty EventLoop")
    void runCancel() { // always executed if cancelled
        if (referenced > 0) { // is blocking
            incomingPublishService.drain();
        }
    }

    @CallByThread("Netty EventLoop")
    void increaseMissingAcknowledgements() {
        missingAcknowledgements++;
    }

    @CallByThread("Netty EventLoop")
    void acknowledge(final boolean drain) {
        // 这里的drain代表了某一个消息对应的所有订阅方是否都进行了ack
        if (drain) {
            incomingPublishService.drain();
        }
        // 从订阅者的角度将需要ack但是没有ack的消息数量减一
        if (--missingAcknowledgements == 0) {
            checkDone();
        }
    }

    @CallByThread("Netty EventLoop")
    int reference() {
        return ++referenced;
    }

    @CallByThread("Netty EventLoop")
    int dereference() {
        return --referenced;
    }
}
