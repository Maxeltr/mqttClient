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
    /*
     * This map stores incoming publish messages with QoS = 2 that waits PUBREL messages and then are deleted
     */
    private final Map<Integer, MqttPublishMessage> pendingPubRel = Collections.synchronizedMap(new LinkedHashMap());

    /*
     * This map stores incoming PUBREC messages that waits PUBCOMP messages and then are deleted
     */
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
                System.out.println(String.format(
                        "Received PUBLISH. Message id: %s, t: %s, d: %s, q: %s, r: %s.",
                        pubMessage.variableHeader().packetId(),
                        pubMessage.variableHeader().topicName(),
                        pubMessage.fixedHeader().isDup(),
                        pubMessage.fixedHeader().qosLevel(),
                        pubMessage.fixedHeader().isRetain()
                ));
                logger.log(Level.INFO, String.format(
                        "Received PUBLISH. Message id: %s, t: %s, d: %s, q: %s, r: %s.",
                        pubMessage.variableHeader().packetId(),
                        pubMessage.variableHeader().topicName(),
                        pubMessage.fixedHeader().isDup(),
                        pubMessage.fixedHeader().qosLevel(),
                        pubMessage.fixedHeader().isRetain()
                ));
                this.handlePublish(ctx.channel(), pubMessage);
                break;
            case PUBACK:
                MqttPubAckMessage pubAckmessage = (MqttPubAckMessage) msg;
                System.out.println(String.format(
                        "Received PUBACK. Message id: %s, d: %s, q: %s, r: %s.",
                        pubAckmessage.variableHeader().messageId(),
                        pubAckmessage.fixedHeader().isDup(),
                        pubAckmessage.fixedHeader().qosLevel(),
                        pubAckmessage.fixedHeader().isRetain()
                ));
                logger.log(Level.INFO, String.format(
                        "Received PUBACK. Message id: %s, d: %s, q: %s, r: %s.",
                        pubAckmessage.variableHeader().messageId(),
                        pubAckmessage.fixedHeader().isDup(),
                        pubAckmessage.fixedHeader().qosLevel(),
                        pubAckmessage.fixedHeader().isRetain()
                ));
                Promise future = (Promise<MqttPublishMessage>) this.promiseBroker.get(pubAckmessage.variableHeader().messageId());
                if (!future.isDone()) {
                    future.setSuccess(pubAckmessage);
                }
                break;
            case PUBREC:
                MqttMessage pubrecMessage = (MqttMessage) msg;
                MqttMessageIdVariableHeader pubrecVariableHeader = (MqttMessageIdVariableHeader) pubrecMessage.variableHeader();
                System.out.println(String.format(
                        "Received PUBREC. Message id: %s, d: %s, q: %s, r: %s.",
                        pubrecVariableHeader.messageId(),
                        msg.fixedHeader().isDup(),
                        msg.fixedHeader().qosLevel(),
                        msg.fixedHeader().isRetain()
                ));
                logger.log(Level.INFO, String.format(
                        "Received PUBREC. Message id: %s, d: %s, q: %s, r: %s.",
                        pubrecVariableHeader.messageId(),
                        msg.fixedHeader().isDup(),
                        msg.fixedHeader().qosLevel(),
                        msg.fixedHeader().isRetain()
                ));
                this.handlePubrec(ctx.channel(), msg);

                break;
            case PUBREL:
                MqttMessage pubrelMessage = (MqttMessage) msg;
                MqttMessageIdVariableHeader pubrelVariableHeader = (MqttMessageIdVariableHeader) pubrelMessage.variableHeader();
                System.out.println(String.format("Received PUBREL. Message id: %s, d: %s, q: %s, r: %s.",
                        pubrelVariableHeader.messageId(),
                        msg.fixedHeader().isDup(),
                        msg.fixedHeader().qosLevel(),
                        msg.fixedHeader().isRetain()
                ));
                logger.log(Level.INFO, String.format(
                        "Received PUBREL. Message id: %s, d: %s, q: %s, r: %s.",
                        pubrelVariableHeader.messageId(),
                        msg.fixedHeader().isDup(),
                        msg.fixedHeader().qosLevel(),
                        msg.fixedHeader().isRetain()
                ));
                this.handlePubrel(ctx.channel(), msg);
                break;
            case PUBCOMP:
                MqttMessage pubcompMessage = (MqttMessage) msg;
                MqttMessageIdVariableHeader pubcompVariableHeader = (MqttMessageIdVariableHeader) pubcompMessage.variableHeader();
                System.out.println(String.format("Received PUBCOMP. Message id: %s, d: %s, q: %s, r: %s.",
                        pubcompVariableHeader.messageId(),
                        msg.fixedHeader().isDup(),
                        msg.fixedHeader().qosLevel(),
                        msg.fixedHeader().isRetain()
                ));
                logger.log(Level.INFO, String.format(
                        "Received PUBCOMP. Message id: %s, d: %s, q: %s, r: %s.",
                        pubcompVariableHeader.messageId(),
                        msg.fixedHeader().isDup(),
                        msg.fixedHeader().qosLevel(),
                        msg.fixedHeader().isRetain()
                ));
                this.handlePubcomp(ctx.channel(), msg);
                break;

        }
    }

    private void handlePubcomp(Channel channel, MqttMessage message) {
        MqttMessageIdVariableHeader variableHeader = (MqttMessageIdVariableHeader) message.variableHeader();
        MqttMessage pubRecMessage = this.pendingPubComp.get(variableHeader.messageId());
        if (pubRecMessage == null) {
            logger.log(Level.WARNING, String.format("Collection of waiting confirmation PUBREC messages returned null instead saved pubRecMessage"));
            System.out.println(String.format("Collection of waiting confirmation PUBREC messages returned null instead saved pubRecMessage"));
        } else {
            this.pendingPubComp.remove(variableHeader.messageId());
            logger.log(Level.FINE, String.format("Remove (from pending PUBCOMP) saved pubRecMessage id: %s", variableHeader.messageId()));
            System.out.println(String.format("Remove (from pending PUBCOMP) saved pubRecMessage id: %s", variableHeader.messageId()));
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
            System.out.println(String.format("Add (to pending PUBCOMP collection) PUBREC message id: %s.", variableHeader.messageId()));
            logger.log(Level.FINE, String.format("Add (to pending PUBCOMP collection) PUBREC message id: %s.", variableHeader.messageId()));
        } else {
            System.out.println(String.format("Received PUBREC message is repeated. Message id: %s.", variableHeader.messageId()));
            logger.log(Level.WARNING, String.format("Received PUBREC message is repeated. Message id: %s.", variableHeader.messageId()));
        }

        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.AT_LEAST_ONCE, false, 0);
        MqttMessage pubrelMessage = new MqttMessage(fixedHeader, variableHeader);

        channel.writeAndFlush(pubrelMessage);

        System.out.println(String.format(
                "Sent PUBREL message id: %s, d: %s, q: %s, r: %s.",
                variableHeader.messageId(),
                pubrelMessage.fixedHeader().isDup(),
                pubrelMessage.fixedHeader().qosLevel(),
                pubrelMessage.fixedHeader().isRetain()
        ));
        logger.log(Level.INFO, String.format(
                "Sent PUBREL message id: %s, d: %s, q: %s, r: %s.",
                variableHeader.messageId(),
                pubrelMessage.fixedHeader().isDup(),
                pubrelMessage.fixedHeader().qosLevel(),
                pubrelMessage.fixedHeader().isRetain()
        ));
    }

    private void handlePubrel(Channel channel, MqttMessage message) throws InterruptedException {
        MqttMessageIdVariableHeader variableHeader = (MqttMessageIdVariableHeader) message.variableHeader();
        MqttPublishMessage publishMessage = this.pendingPubRel.get(variableHeader.messageId());
        if (publishMessage == null) {
            logger.log(Level.WARNING, String.format("Collection of waiting confirmation publish QoS2 messages returned null instead saved publishMessage"));
            System.out.println(String.format("Collection of waiting confirmation publish QoS2 messages returned null instead saved publishMessage"));
        } else {
            //TODO handle publish Message
            this.messageHandler.handleMessage(publishMessage);
            this.pendingPubRel.remove(variableHeader.messageId());
//            publishMessage.release();	//???
            logger.log(Level.FINE, String.format("Remove (from pending PUBREL) publish message id: %s", variableHeader.messageId()));
            System.out.println(String.format("Remove (from pending PUBREL) publish message id: %s", variableHeader.messageId()));
        }

        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBCOMP, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttMessage pubCompMessage = new MqttMessage(fixedHeader, variableHeader);

        channel.writeAndFlush(pubCompMessage);

        System.out.println(String.format("Sent PUBCOMP message id: %s, d: %s, q: %s, r: %s.",
                variableHeader.messageId(),
                pubCompMessage.fixedHeader().isDup(),
                pubCompMessage.fixedHeader().qosLevel(),
                pubCompMessage.fixedHeader().isRetain()
        ));
        logger.log(Level.INFO, String.format(
                "Sent PUBCOMP message id: %s, d: %s, q: %s, r: %s.",
                variableHeader.messageId(),
                pubCompMessage.fixedHeader().isDup(),
                pubCompMessage.fixedHeader().qosLevel(),
                pubCompMessage.fixedHeader().isRetain()
        ));
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

                MqttPubAckMessage pubAckMessage = new MqttPubAckMessage(fixedHeader, variableHeader);
                channel.writeAndFlush(pubAckMessage);

                System.out.println(String.format(
                        "Sent PUBACK message id: %s, d: %s, q: %s, r: %s.",
                        pubAckMessage.variableHeader().messageId(),
                        pubAckMessage.fixedHeader().isDup(),
                        pubAckMessage.fixedHeader().qosLevel(),
                        pubAckMessage.fixedHeader().isRetain()
                ));
                logger.log(Level.INFO, String.format(
                        "Sent PUBACK message id: %s, d: %s, q: %s, r: %s.",
                        pubAckMessage.variableHeader().messageId(),
                        pubAckMessage.fixedHeader().isDup(),
                        pubAckMessage.fixedHeader().qosLevel(),
                        pubAckMessage.fixedHeader().isRetain()
                ));

                break;
            case EXACTLY_ONCE:
                System.out.println(String.format("handlePublish: EXACTLY_ONCE. topicName - " + message.variableHeader().topicName() + " payload - " + message.payload().toString(Charset.forName("UTF-8"))));

                if (!this.pendingPubRel.containsKey(message.variableHeader().packetId())) {
                    this.pendingPubRel.put(message.variableHeader().packetId(), message);
                    System.out.println(String.format("Add (to pending PUBREL collection) publish message id: %s.", message.variableHeader().packetId()));
                    logger.log(Level.FINE, String.format("Add (to pending PUBREL collection) publish message id: %s.", message.variableHeader().packetId()));
                } else {
                    System.out.println(String.format("Received publish message with QoS2 is repeated id: %s.", message.variableHeader().packetId()));
                    logger.log(Level.INFO, String.format("Received publish message with QoS2 is repeated id: %s.", message.variableHeader().packetId()));
                }

                fixedHeader = new MqttFixedHeader(MqttMessageType.PUBREC, false, MqttQoS.AT_MOST_ONCE, false, 0);
                variableHeader = MqttMessageIdVariableHeader.from(message.variableHeader().packetId());
                MqttMessage pubrecMessage = new MqttMessage(fixedHeader, variableHeader);

//                message.payload().retain();	//???
                channel.writeAndFlush(pubrecMessage);

                System.out.println(String.format(
                        "Sent PUBREC message id: %s, d: %s, q: %s, r: %s.",
                        variableHeader.messageId(),
                        pubrecMessage.fixedHeader().isDup(),
                        pubrecMessage.fixedHeader().qosLevel(),
                        pubrecMessage.fixedHeader().isRetain()
                ));
                logger.log(Level.INFO, String.format(
                        "Sent PUBREC message id: %s, d: %s, q: %s, r: %s.",
                        variableHeader.messageId(),
                        pubrecMessage.fixedHeader().isDup(),
                        pubrecMessage.fixedHeader().qosLevel(),
                        pubrecMessage.fixedHeader().isRetain()
                ));
                break;
        }
    }

    @Scheduled(fixedDelay = 20000, initialDelay = 20000)
    public void retransmit() {
        System.out.println(String.format("Start retransmission in publish handler"));
        logger.log(Level.FINE, String.format("Start retransmission in publish handler"));
        Channel channel = this.ctx.channel();

        //Check amount of publish messages, that pending PUBREL. No need to retransmit PUBREC messages.
        //TODO What to do when amount = x?
        int index = 1;
        synchronized (this.pendingPubRel) {
            System.out.println(String.format("Amount pending PUBREL incoming publish messages is %s", this.pendingPubRel.size()));
            logger.log(Level.FINE, String.format("Amount pending PUBREL incoming publish messages is %s", this.pendingPubRel.size()));
            for (Map.Entry<Integer, MqttPublishMessage> pair : this.pendingPubRel.entrySet()) {
                //System.out.println(String.format("Pending PUBREL. Incoming publish message %s from %s. Message %s", index, this.pendingPubRel.size(), pair.getValue()));
                //logger.log(Level.INFO, String.format("Pending PUBREL. Incoming publish message %s from %s. Message %s", index, this.pendingPubRel.size(), pair.getValue()));
                index++;
            }
        }

        //Check amount of PUBREC messages, that pending PUBCOMP messages.
        //TODO What to do when amount = x?
        index = 1;
        synchronized (this.pendingPubComp) {
            System.out.println(String.format("Retransmission PUBREL messages for pending PUBCOMP incoming PUBREC messages. Amount incoming PUBREC messages is %s ", this.pendingPubComp.size()));
            logger.log(Level.FINE, String.format("Retransmission PUBREL messages for pending PUBCOMP incoming PUBREC messages. Amount incoming PUBREC messages is %s ", this.pendingPubComp.size()));
            for (Map.Entry<Integer, MqttMessage> pair : this.pendingPubComp.entrySet()) {
                if (channel.isActive()) {
                    MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.AT_LEAST_ONCE, false, 0);
                    MqttMessage pubrelMessage = new MqttMessage(fixedHeader, pair.getValue().variableHeader());

                    channel.writeAndFlush(pubrelMessage);

                    MqttMessageIdVariableHeader pubrelVariableHeader = (MqttMessageIdVariableHeader) pubrelMessage.variableHeader();
                    System.out.println(String.format(
                            "Retransmission PUBREL message for pending PUBCOMP incoming PUBREC message %s from %s. Sent PUBREL message id: %s",
                            index, this.pendingPubComp.size(),
                            pubrelVariableHeader.messageId()
                    ));
                    logger.log(Level.FINE, String.format(
                            "Retransmission PUBREL message for pending PUBCOMP incoming PUBREC message %s from %s. Sent PUBREL message id: %s",
                            index, this.pendingPubComp.size(),
                            pubrelVariableHeader.messageId()
                    ));
                }
                index++;
            }
        }
        System.out.println(String.format("End retransmission in publish handler"));
        logger.log(Level.FINE, String.format("End retransmission in publish handler"));
    }
}
