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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import ru.maxeltr.mqttClient.Config.Config;

/**
 *
 * @author Maxim Eltratov <<Maxim.Eltratov@ya.ru>>
 */
public class MqttChannelHandler extends SimpleChannelInboundHandler<MqttMessage> {

    private static final Logger logger = Logger.getLogger(MqttClientImpl.class.getName());

    private final Config config;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    public MqttChannelHandler(Config config) {
        this.config = config;
    }

    public void publishConnAckEvent(final String message) {

        ConnAckEvent customSpringEvent = new ConnAckEvent(this, message);
        applicationEventPublisher.publishEvent(customSpringEvent);

    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, MqttMessage msg) throws Exception {
        switch (msg.fixedHeader().messageType()) {
            case CONNACK:
                handleConack(ctx.channel(), (MqttConnAckMessage) msg);
                break;
            case SUBACK:

                break;
            case PUBLISH:

                break;
            case UNSUBACK:

                break;
            case PUBACK:

                break;
            case PUBREC:

                break;
            case PUBREL:

                break;
            case PUBCOMP:

                break;
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);

        MqttFixedHeader connectFixedHeader = new MqttFixedHeader(MqttMessageType.CONNECT, false, MqttQoS.AT_MOST_ONCE, false, 0);

        MqttConnectVariableHeader connectVariableHeader = new MqttConnectVariableHeader(
                this.config.getProperty("protocolName", ""),
                Integer.parseInt(this.config.getProperty("protocolVersion", "")),
                true,
                true,
                false,
                0,
                false,
                false,
                20,
                MqttProperties.NO_PROPERTIES
        );

        MqttConnectPayload connectPayload = new MqttConnectPayload(
                this.config.getProperty("clientId", ""),
                MqttProperties.NO_PROPERTIES,
                null,
                null,
                this.config.getProperty("userName", ""),
                this.config.getProperty("password", "").getBytes()
        );

        MqttConnectMessage connectMessage = new MqttConnectMessage(connectFixedHeader, connectVariableHeader, connectPayload);
        ctx.writeAndFlush(connectMessage);
        System.out.println("Sent CONNECT");

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
    }

    private void handleConack(Channel channel, MqttConnAckMessage message) {
        switch (message.variableHeader().connectReturnCode()) {
            case CONNECTION_ACCEPTED:

                logger.log(Level.INFO, String.format("Connection accepted %s.", MqttConnectReturnCode.CONNECTION_ACCEPTED));
                System.out.println(String.format("Connection accepted %s.", MqttConnectReturnCode.CONNECTION_ACCEPTED));

                channel.flush();
                break;

            case CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD:
            case CONNECTION_REFUSED_IDENTIFIER_REJECTED:
            case CONNECTION_REFUSED_NOT_AUTHORIZED:
            case CONNECTION_REFUSED_SERVER_UNAVAILABLE:
            case CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION:

                logger.log(Level.INFO, String.format("Connection refused %s.", message.variableHeader().connectReturnCode()));
                System.out.println(String.format("Connection refused %s.", message.variableHeader().connectReturnCode()));

                channel.close();
                // Don't start reconnect logic here
                break;
        }
    }
}
