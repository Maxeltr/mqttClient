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
package ru.maxeltr.mqttClient;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.timeout.IdleStateHandler;

/**
 *
 * @author Maxim Eltratov <<Maxim.Eltratov@ya.ru>>
 */
public class MqttChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final MqttDecoder mqttDecoder ;
    private final MqttEncoder mqttEncoder ;
    private final ChannelHandler idleStateHandler ;
    private final ChannelHandler mqttPingHandler ;
    private final ChannelHandler mqttChannelHandler ;
    private final MqttConnectHandler mqttConnectHandler ;
    private final MqttSubscriptionHandler mqttSubscriptionHandler ;

    public MqttChannelInitializer(
            MqttDecoder mqttDecoder,
            MqttEncoder mqttEncoder,
            ChannelHandler idleStateHandler,
            ChannelHandler mqttPingHandler,
            MqttConnectHandler mqttConnectHandler,
            MqttSubscriptionHandler mqttSubscriptionHandler,
            ChannelHandler mqttChannelHandler

    ) {
        this.mqttDecoder = mqttDecoder;
        this.mqttEncoder = mqttEncoder;
        this.idleStateHandler = idleStateHandler;
        this.mqttPingHandler = mqttPingHandler;
        this.mqttConnectHandler = mqttConnectHandler;
        this.mqttSubscriptionHandler = mqttSubscriptionHandler;
        this.mqttChannelHandler = mqttChannelHandler;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ch.pipeline().addLast("mqttDecoder", this.mqttDecoder);
        ch.pipeline().addLast("mqttEncoder", this.mqttEncoder);
        ch.pipeline().addLast("idleStateHandler", this.idleStateHandler);
        ch.pipeline().addLast("mqttPingHandler", this.mqttPingHandler);
        ch.pipeline().addLast("mqttConnectHandler", this.mqttConnectHandler);
        ch.pipeline().addLast("mqttSubscriptionHandler", this.mqttSubscriptionHandler);
        ch.pipeline().addLast("mqttChannelHandler", this.mqttChannelHandler);

    }

    public MqttConnectHandler getConnectHandler() {
        return this.mqttConnectHandler;
    }

    public MqttSubscriptionHandler getSubscriptionHandler() {
        return this.mqttSubscriptionHandler;
    }
}
