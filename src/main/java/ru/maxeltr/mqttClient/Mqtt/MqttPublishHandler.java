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
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPubAckMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.util.concurrent.Promise;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import ru.maxeltr.mqttClient.Config.Config;
import ru.maxeltr.mqttClient.Service.MessageHandler;

/**
 *
 * @author Maxim Eltratov <<Maxim.Eltratov@ya.ru>>
 */
public class MqttPublishHandler extends SimpleChannelInboundHandler<MqttMessage> {

    private static final Logger logger = Logger.getLogger(MqttPublishHandler.class.getName());

    private final Config config;

    private final MessageHandler messageHandler;

    private final PromiseBroker promiseBroker;

    private ChannelHandlerContext ctx;

//    private final ConcurrentHashMap<Integer, MqttPublishMessage> pendingPubRel = new ConcurrentHashMap<>();
//
//    private final ConcurrentHashMap<Integer, MqttMessage> pendingPubComp = new ConcurrentHashMap<>();
    private final Map<Integer, MqttPublishMessage> pendingPubRel = Collections.synchronizedMap(new LinkedHashMap());

    private final Map<Integer, MqttMessage> pendingPubComp = Collections.synchronizedMap(new LinkedHashMap());

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    public MqttPublishHandler(PromiseBroker promiseBroker, MessageHandler messageHandler, Config config) {
        this.promiseBroker = promiseBroker;
        this.messageHandler = messageHandler;
        this.config = config;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, MqttMessage msg) throws Exception {
        switch (msg.fixedHeader().messageType()) {
            case PUBLISH:
                MqttPublishMessage pubMessage = (MqttPublishMessage) msg;
                System.out.println(String.format("Received PUBLISH for message with id %s. Message is %s.", pubMessage.variableHeader().packetId(), pubMessage));
                logger.log(Level.INFO, String.format("Received PUBLISH for message with id %s. Message is %s.", pubMessage.variableHeader().packetId(), pubMessage));
                this.handlePublish(ctx.channel(), (MqttPublishMessage) pubMessage);
                break;
            case PUBACK:
                MqttPubAckMessage pubAckmessage = (MqttPubAckMessage) msg;
                System.out.println(String.format("Received PUBACK for message with id %s. Message is %s.", pubAckmessage.variableHeader().messageId(), pubAckmessage));
                logger.log(Level.INFO, String.format("Received PUBACK for message with id %s. Message is %s.", pubAckmessage.variableHeader().messageId(), pubAckmessage));
                Promise future = (Promise<MqttPublishMessage>) this.promiseBroker.get(pubAckmessage.variableHeader().messageId());
                if (!future.isDone()) {
                    future.setSuccess(pubAckmessage);
                }
                break;
            case PUBREC:
                System.out.println(String.format("Received PUBREC. Message is %s.", msg));
                logger.log(Level.INFO, String.format("Received PUBREC. Message is %s.", msg));
                this.handlePubrec(ctx.channel(), msg);

                break;
            case PUBREL:
                System.out.println(String.format("Received PUBREL. Message is %s.", msg));
                logger.log(Level.INFO, String.format("Received PUBREL. Message is %s.", msg));
                this.handlePubrel(ctx.channel(), msg);
                break;
            case PUBCOMP:
                System.out.println(String.format("Received PUBCOMP. Message is %s.", msg));
                logger.log(Level.INFO, String.format("Received PUBCOMP. Message is %s.", msg));
                this.handlePubcomp(ctx.channel(), msg);
                break;

        }
    }

    private void handlePubcomp(Channel channel, MqttMessage message) {
        MqttMessageIdVariableHeader variableHeader = (MqttMessageIdVariableHeader) message.variableHeader();
        MqttMessage pubRecMessage = this.pendingPubComp.get(variableHeader.messageId());
        if (pubRecMessage == null) {
            logger.log(Level.INFO, String.format("Collection of waiting confirmation PUBREC messages returned null instead saved pubRecMessage"));
            System.out.println(String.format("Collection of waiting confirmation PUBREC messages returned null instead saved pubRecMessage"));
        } else {
            this.pendingPubComp.remove(variableHeader.messageId());
            logger.log(Level.INFO, String.format("Remove (from pending PUBCOMP) saved pubRecMessage %s", pubRecMessage));
            System.out.println(String.format("Remove (from pending PUBCOMP) saved pubRecMessage %s", pubRecMessage));
        }
    }

    private void handlePubrec(Channel channel, MqttMessage message) {
        MqttMessageIdVariableHeader variableHeader = (MqttMessageIdVariableHeader) message.variableHeader();
        Promise future = (Promise<MqttMessage>) this.promiseBroker.get(variableHeader.messageId());
        if (!future.isDone()) {
            future.setSuccess(message);
        }
        if (!this.pendingPubComp.containsKey(variableHeader.messageId())) {
            this.pendingPubComp.put(variableHeader.messageId(), message);
        } else {
            System.out.println(String.format("Received PUBREC message is repeated %s.", message));
            logger.log(Level.INFO, String.format("Received PUBREC message is repeated %s.", message));
        }

        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.AT_LEAST_ONCE, false, 0);
        MqttMessage pubrelMessage = new MqttMessage(fixedHeader, variableHeader);

        channel.writeAndFlush(pubrelMessage);

        System.out.println(String.format("Sent PUBREL message %s.", message));
        logger.log(Level.INFO, String.format("Sent PUBREL message %s.", message));
    }

    private void handlePubrel(Channel channel, MqttMessage message) throws InterruptedException {
        MqttMessageIdVariableHeader variableHeader = (MqttMessageIdVariableHeader) message.variableHeader();
        MqttPublishMessage publishMessage = this.pendingPubRel.get(variableHeader.messageId());
        if (publishMessage == null) {
            logger.log(Level.INFO, String.format("Collection of waiting confirmation publish QoS2 messages returned null instead saved publishMessage"));
            System.out.println(String.format("Collection of waiting confirmation publish QoS2 messages returned null instead saved publishMessage"));
        } else {
            //TODO handle publish Message
            this.messageHandler.handleMessage(publishMessage);
            this.pendingPubRel.remove(variableHeader.messageId());
//            publishMessage.release();	//???
            logger.log(Level.INFO, String.format("Remove (from pending PUBREL) publish message id %s", variableHeader.messageId()));
            System.out.println(String.format("Remove (from pending PUBREL) publish message id %s", variableHeader.messageId()));
        }

        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBCOMP, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttMessage pubCompMessage = new MqttMessage(fixedHeader, variableHeader);

        channel.writeAndFlush(pubCompMessage);

        System.out.println(String.format("Sent PUBCOMP message %s.", pubCompMessage));
        logger.log(Level.INFO, String.format("Sent PUBCOMP message %s.", pubCompMessage));

    }

    private void handlePublish(Channel channel, MqttPublishMessage message) throws InterruptedException {
        MqttFixedHeader fixedHeader;
        MqttMessageIdVariableHeader variableHeader;
        switch (message.fixedHeader().qosLevel()) {
            case AT_MOST_ONCE:
//                System.out.println(String.format(message.variableHeader().topicName() + " " + message.payload().toString(Charset.forName("UTF-8"))));

                //TODO handle publish Message
                System.out.println(String.format("handlePublish: AT_MOST_ONCE. topicName - " + message.variableHeader().topicName() + " payload - " + message.payload().toString(Charset.forName("UTF-8"))));
                System.out.println(String.format("Call MessageHandler."));
                logger.log(Level.INFO, String.format("Call MessageHandler."));
                this.messageHandler.handleMessage(message);
                System.out.println(String.format("Return from MessageHandler."));
                logger.log(Level.INFO, String.format("Return from MessageHandler."));
                break;
            case AT_LEAST_ONCE:
                System.out.println(String.format("handlePublish: AT_LEAST_ONCE. topicName - " + message.variableHeader().topicName() + " payload - " + message.payload().toString(Charset.forName("UTF-8"))));

                //TODO handle publish Message
                this.messageHandler.handleMessage(message);

                fixedHeader = new MqttFixedHeader(MqttMessageType.PUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
                variableHeader = MqttMessageIdVariableHeader.from(message.variableHeader().packetId());

                channel.writeAndFlush(new MqttPubAckMessage(fixedHeader, variableHeader));

                System.out.println(String.format("Sent PUBACK message %s.", message));
                logger.log(Level.INFO, String.format("Sent PUBACK message %s.", message));

                break;
            case EXACTLY_ONCE:
                System.out.println(String.format("handlePublish: EXACTLY_ONCE. topicName - " + message.variableHeader().topicName() + " payload - " + message.payload().toString(Charset.forName("UTF-8"))));

                if (!this.pendingPubRel.containsKey(message.variableHeader().packetId())) {
                    this.pendingPubRel.put(message.variableHeader().packetId(), message);
                } else {
                    System.out.println(String.format("Received publish message with QoS2 is repeated %s.", message));
                    logger.log(Level.INFO, String.format("Received publish message with QoS2 is repeated %s.", message));
                }

                fixedHeader = new MqttFixedHeader(MqttMessageType.PUBREC, false, MqttQoS.AT_MOST_ONCE, false, 0);
                variableHeader = MqttMessageIdVariableHeader.from(message.variableHeader().packetId());
                MqttMessage pubrecMessage = new MqttMessage(fixedHeader, variableHeader);

//                message.payload().retain();	//???
                channel.writeAndFlush(pubrecMessage);

                System.out.println(String.format("Sent PUBREC message %s.", message));
                logger.log(Level.INFO, String.format("Sent PUBREC message %s.", message));
                break;
        }
    }

    @Scheduled(fixedDelay = 20000, initialDelay = 20000)
    public void retransmission() {
        System.out.println("strart retransmission publish handler");
        Channel channel = this.ctx.channel();
        Iterator it;
        int index = 1;
        synchronized (this.pendingPubRel) {
            it = this.pendingPubRel.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry pair = (Map.Entry) it.next();
                System.out.println(String.format("Pending PUBREL. Incoming publish message %s from %s. Message %s", index, this.pendingPubRel.size(), pair.getValue()));
                logger.log(Level.INFO, String.format("Pending PUBREL. Incoming publish message %s from %s. Message %s", index, this.pendingPubRel.size(), pair.getValue()));
                index++;
            }
        }

        index = 1;
        synchronized (this.pendingPubComp) {
            it = this.pendingPubComp.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry pair = (Map.Entry) it.next();
                if (channel.isActive()) {
                    MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.AT_LEAST_ONCE, false, 0);
                    MqttMessage pubrelMessage = new MqttMessage(fixedHeader, (MqttMessageIdVariableHeader) pair.getValue());

                    channel.writeAndFlush(pubrelMessage);

                    System.out.println(String.format("Retransmission pending PUBCOMP. Sent PUBREL message. PUBREC %s from %s. Message %s", index, this.pendingPubComp.size(), pair.getValue()));
                    logger.log(Level.INFO, String.format("Retransmission pending PUBCOMP. Sent PUBREL message. PUBREC %s from %s. Message %s", index, this.pendingPubComp.size(), pair.getValue()));
                }
                index++;
            }
        }
        System.out.println("end retransmission publish handler");
    }
}
