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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttProperties.MqttProperty;
import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Promise;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import ru.maxeltr.mqttClient.Config.Config;

/**
 *
 * @author Maxim Eltratov <<Maxim.Eltratov@ya.ru>>
 */
public class MqttSubscriptionHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = Logger.getLogger(MqttSubscriptionHandler.class.getName());

    private final Config config;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    private Promise<MqttSubscriptionResult> subscriptionFuture;

    public MqttSubscriptionHandler(Config config) {
        this.config = config;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof MqttMessage)) {
            ctx.fireChannelRead(msg);
            return;
        }

        MqttMessage message = (MqttMessage) msg;
        if (message.fixedHeader().messageType() == MqttMessageType.SUBACK) {
            this.handleSubAck(ctx.channel(), (MqttSubAckMessage) message);
        } else if (message.fixedHeader().messageType() == MqttMessageType.UNSUBACK) {
//            this.handleUnsuback(ctx.channel(), (MqttSubAckMessage) message);
        } else {
            ctx.fireChannelRead(ReferenceCountUtil.retain(msg));
        }
    }

    private void handleSubAck(Channel channel, MqttSubAckMessage message) {

        for(MqttProperty p: message.idAndPropertiesVariableHeader().properties().listAll()) {
            System.out.println(String.format("propertyId %s, value %s", p.propertyId(), p.value()));
        }

        for(int p: message.payload().grantedQoSLevels()) {
            System.out.println(String.format("grantedQoSLevels %s", p));
        }

        for(int p: message.payload().reasonCodes()) {
            System.out.println(String.format("reasonCodes %s", p));
        }



        MqttSubscriptionResult mqttResultSub = new MqttSubscriptionResult(message.variableHeader().messageId());
//        this.publishSubAckEvent(mqttResultSub);
        this.subscriptionFuture.setSuccess(mqttResultSub);
        logger.log(Level.INFO, String.format("Connection accepted."));
        System.out.println(String.format("Connection accepted %s.", message.variableHeader().messageId()));

        channel.flush();


    }

    public Promise<MqttSubscriptionResult> getSubscriptionFuture() {
        return this.subscriptionFuture;
    }

    public void setSubscriptionFuture(Promise<MqttSubscriptionResult> subscriptionFuture) {
        this.subscriptionFuture = subscriptionFuture;
    }
}
