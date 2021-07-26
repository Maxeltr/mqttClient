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
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttConnectPayload;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttConnectVariableHeader;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttQoS;
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
public class MqttConnectHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = Logger.getLogger(MqttConnectHandler.class.getName());

    private final Config config;

    private final PromiseBroker promiseBroker;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    public MqttConnectHandler(PromiseBroker promiseBroker, Config config) {
        this.promiseBroker = promiseBroker;
        this.config = config;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof MqttMessage)) {
            ctx.fireChannelRead(msg);
            return;
        }

        MqttMessage message = (MqttMessage) msg;
        if (message.fixedHeader().messageType() == MqttMessageType.CONNACK) {
            handleConnack(ctx.channel(), (MqttConnAckMessage) message);
            ReferenceCountUtil.release(msg);
        } else if (message.fixedHeader().messageType() == MqttMessageType.DISCONNECT) {
            System.out.println(String.format("Received DISCONNECT %s. Close channel.", msg));
            logger.log(Level.INFO, String.format("Received disconnect message %s. Close channel.", msg));
            ctx.close();
        } else {
            ctx.fireChannelRead(msg);   //ctx.fireChannelRead(ReferenceCountUtil.retain(msg));
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);

        MqttFixedHeader connectFixedHeader = new MqttFixedHeader(MqttMessageType.CONNECT, false, MqttQoS.AT_MOST_ONCE, false, 0);

        MqttConnectVariableHeader connectVariableHeader = new MqttConnectVariableHeader(
                this.config.getProperty("protocolName", "MQTT"),
                Integer.parseInt(this.config.getProperty("protocolVersion", "4")),
                Boolean.parseBoolean(this.config.getProperty("hasUserName", "true")), //Boolean.getBoolean("hasUserName"),
                Boolean.parseBoolean(this.config.getProperty("hasPassword", "true")), //Boolean.getBoolean("hasPassword"),
                Boolean.parseBoolean(this.config.getProperty("willRetain", "false")), //Boolean.getBoolean("willRetain"),
                Integer.parseInt(this.config.getProperty("willQos", "0")),
                Boolean.parseBoolean(this.config.getProperty("willFlag", "false")), //Boolean.getBoolean("willFlag"),
                Boolean.parseBoolean(this.config.getProperty("cleanSeesion", "true")), //Boolean.getBoolean("cleanSeesion"),
                Integer.parseInt(this.config.getProperty("keepAliveTimer", "20")),
                MqttProperties.NO_PROPERTIES
        );

        MqttConnectPayload connectPayload = new MqttConnectPayload(
                this.config.getProperty("clientId", null),
                MqttProperties.NO_PROPERTIES,
                this.config.getProperty("willTopic", null),
                this.config.getProperty("willMessage", "").getBytes(),
                this.config.getProperty("userName", ""),
                this.config.getProperty("password", "").getBytes()
        );

        MqttConnectMessage connectMessage = new MqttConnectMessage(connectFixedHeader, connectVariableHeader, connectPayload);
        ctx.writeAndFlush(connectMessage);
        System.out.println(String.format("Sent connect message %s.", connectMessage.variableHeader()));
        logger.log(Level.INFO, String.format("Sent connect message %s.", connectMessage.variableHeader()));
    }

    private void handleConnack(Channel channel, MqttConnAckMessage message) {
        MqttConnectReturnCode returnCode = message.variableHeader().connectReturnCode();
        Promise<MqttConnAckMessage> future;
        switch (message.variableHeader().connectReturnCode()) {
            case CONNECTION_ACCEPTED:
                this.publishConnAckEvent(message);
                future = this.promiseBroker.getConnectFuture();
                if (!future.isDone()) {
                    future.setSuccess(message);
                }
                logger.log(Level.INFO, String.format("Received CONNACK message. Connection accepted %s.", message.variableHeader()));
                System.out.println(String.format("Received CONNACK message. Connection accepted %s.", message.variableHeader()));

                channel.flush();
                break;

            case CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD:
            case CONNECTION_REFUSED_IDENTIFIER_REJECTED:
            case CONNECTION_REFUSED_NOT_AUTHORIZED:
            case CONNECTION_REFUSED_SERVER_UNAVAILABLE:
            case CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION:
                this.publishConnAckEvent(message);
                future = this.promiseBroker.getConnectFuture();
                if (!future.isDone()) {
                    future.setSuccess(message);
                }
                logger.log(Level.INFO, String.format("Received CONNACK message. Connection refused %s.", message.variableHeader()));
                System.out.println(String.format("Received CONNACK message. Connection refused %s.", message.variableHeader()));

                channel.close();
                // Don't start reconnect logic here
                break;
        }

    }

    private void publishConnAckEvent(MqttConnAckMessage connAckMessage) {
        applicationEventPublisher.publishEvent(new ConnAckEvent(this, connAckMessage.variableHeader().connectReturnCode().name(), connAckMessage));

    }

}
