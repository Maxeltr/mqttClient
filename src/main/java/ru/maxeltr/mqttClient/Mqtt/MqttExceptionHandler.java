/*
 * The MIT License
 *
 * Copyright 2021 Maxim Eltratov <<Maxim.Eltratov@ya.ru>>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ru.maxeltr.mqttClient.Mqtt;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import java.net.SocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

/**
 *
 * @author Maxim Eltratov <<Maxim.Eltratov@ya.ru>>
 */
public class MqttExceptionHandler extends ChannelDuplexHandler {

    private static final Logger logger = Logger.getLogger(MqttExceptionHandler.class.getName());

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Uncaught exceptions from inbound handlers will propagate up to this handler
        logger.log(Level.INFO, String.format("Uncaught exception from inbound handlers. %n %s", ExceptionUtils.getStackTrace(cause)));
        System.out.println(String.format("Uncaught exception from inbound handlers %s.", cause.getMessage()));
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        ctx.connect(remoteAddress, localAddress, promise.addListener((ChannelFutureListener) (ChannelFuture future) -> {
            if (!future.isSuccess()) {
                Throwable failureCause = future.cause();
                logger.log(Level.INFO, String.format("Connection failed. Uncaught exception from outbound handler. %n %s", ExceptionUtils.getStackTrace(failureCause)));
                System.out.println(String.format("Connection failed. Uncaught exception from outbound handler %s.", failureCause.getMessage()));
            }
        }));
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        ctx.write(msg, promise.addListener((ChannelFutureListener) (ChannelFuture future) -> {
            if (!future.isSuccess()) {
                Throwable failureCause = future.cause();
                logger.log(Level.INFO, String.format("Write operation failed. Uncaught exception from outbound handler. %n %s", ExceptionUtils.getStackTrace(failureCause)));
                System.out.println(String.format("Write operation failed. Uncaught exception from outbound handler %s.", failureCause.getMessage()));
            }
        }));
    }

    
}
