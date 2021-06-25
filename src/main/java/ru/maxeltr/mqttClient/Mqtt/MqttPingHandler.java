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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import ru.maxeltr.mqttClient.Config.Config;

/**
 *
 * @author Maxim Eltratov <<Maxim.Eltratov@ya.ru>>
 */
public class MqttPingHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = Logger.getLogger(MqttPingHandler.class.getName());

    private final Config config;

    private ScheduledFuture<?> pingRespTimeout;

    public MqttPingHandler(Config config) {
        this.config = config;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof MqttMessage)) {
            ctx.fireChannelRead(msg);
            return;
        }

        MqttMessage message = (MqttMessage) msg;
        if (message.fixedHeader().messageType() == MqttMessageType.PINGREQ) {
            MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PINGRESP, false, MqttQoS.AT_MOST_ONCE, false, 0);
            ctx.channel().writeAndFlush(new MqttMessage(fixedHeader));
            logger.log(Level.FINE, String.format("Received ping request. Sent ping response. %s.", msg));
            System.out.println(String.format("Received ping request. Sent ping response. %s.", msg));

        } else if (message.fixedHeader().messageType() == MqttMessageType.PINGRESP) {
            logger.log(Level.FINE, String.format("Received ping response %s.", msg));
            System.out.println(String.format("Received ping response %s.", msg));
            if (this.pingRespTimeout != null && !this.pingRespTimeout.isCancelled() && !this.pingRespTimeout.isDone()) {
                this.pingRespTimeout.cancel(true);
                this.pingRespTimeout = null;
            }

        } else {
            ctx.fireChannelRead(ReferenceCountUtil.retain(msg));
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            switch (event.state()) {
                case READER_IDLE:
                    break;
                case WRITER_IDLE:
                    MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PINGREQ, false, MqttQoS.AT_MOST_ONCE, false, 0);
                    MqttMessage msg = new MqttMessage(fixedHeader);
                    ctx.writeAndFlush(msg);
                    logger.log(Level.FINE, String.format("Sent ping request %s.", msg));
                    System.out.println(String.format("Sent ping request %s.", msg));

                    if (this.pingRespTimeout == null) {
                        this.pingRespTimeout = ctx.channel().eventLoop().schedule(() -> {
//                            MqttFixedHeader fHeader = new MqttFixedHeader(MqttMessageType.DISCONNECT, false, MqttQoS.AT_MOST_ONCE, false, 0);
//                            ctx.channel().writeAndFlush(new MqttMessage(fHeader));
                            System.out.println(String.format("Ping response was not received for keepAlive time."));
                            logger.log(Level.WARNING, String.format("Ping response was not received for keepAlive time."));
                            //TODO ?
                        }, Integer.parseInt(this.config.getProperty("keepAliveTimer", "20")), TimeUnit.SECONDS);
                    }
                    break;
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }

    }
}
