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

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttPubAckMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttReasonCodeAndPropertiesVariableHeader;
import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttSubscribePayload;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import io.netty.handler.codec.mqtt.MqttUnsubAckMessage;
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage;
import io.netty.handler.codec.mqtt.MqttUnsubscribePayload;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import ru.maxeltr.mqttClient.Config.Config;

/**
 *
 * @author Maxim Eltratov <<Maxim.Eltratov@ya.ru>>
 */
public class MqttClientImpl implements ApplicationListener<ApplicationEvent> {

    private static final Logger logger = Logger.getLogger(MqttClientImpl.class.getName());

    private Channel channel;

    private final MqttChannelInitializer mqttChannelInitializer;

    private EventLoopGroup workerGroup;

    private final Bootstrap bootstrap;

    private final Config config;

    private final PromiseBroker promiseBroker;

    private final AtomicInteger nextMessageId = new AtomicInteger(1);

    private final ConcurrentHashMap<Integer, MqttSubscribeMessage> pendingConfirmationSubscriptions = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Integer, MqttUnsubscribeMessage> pendingConfirmationUnsubscriptions = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, MqttTopicSubscription> activeTopics = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Integer, MqttPublishMessage> pendingPubRec = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Integer, MqttUnsubscribeMessage> pendingPubComp = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Integer, MqttPublishMessage> pendingPubAck = new ConcurrentHashMap<>();

    public MqttClientImpl(MqttChannelInitializer mqttChannelInitializer, Config config, PromiseBroker promiseBroker) {
        this.mqttChannelInitializer = mqttChannelInitializer;
        this.config = config;
        this.workerGroup = new NioEventLoopGroup();
        this.bootstrap = new Bootstrap();
        bootstrap.group(this.workerGroup);
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.handler(this.mqttChannelInitializer);
        this.promiseBroker = promiseBroker;
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {

    }

    /**
     * Connect to the specified hostname/ip using the specified port
     *
     * @param host The ip address or host to connect to
     * @param port The tcp port to connect to
     * @return
     */
    public Promise<MqttConnAckMessage> connect(String host, int port) {
        Promise<MqttConnAckMessage> connectFuture = new DefaultPromise<>(this.workerGroup.next());
        this.promiseBroker.setConnectFuture(connectFuture);
        this.bootstrap.remoteAddress(host, port);

        ChannelFuture future = bootstrap.connect();
        future.addListener((ChannelFutureListener) f -> MqttClientImpl.this.channel = f.channel());

        logger.log(Level.INFO, String.format("Connecting to %s via port %s.", host, port));
        System.out.println(String.format("Connecting to %s via port %s.", host, port));

        return connectFuture;
    }

    public Promise<MqttSubAckMessage> subscribe(Map<String, MqttQoS> topicsAndQos) {
        Promise<MqttSubAckMessage> subscribeFuture = new DefaultPromise<>(this.workerGroup.next());
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.SUBSCRIBE, false, MqttQoS.AT_LEAST_ONCE, false, 0);

        List<MqttTopicSubscription> subscriptions = new ArrayList<>();
        topicsAndQos.forEach((k, v) -> {
            MqttTopicSubscription subscription = new MqttTopicSubscription(k, v);
            subscriptions.add(subscription);

        });

        int id = getNewMessageId();
        MqttMessageIdVariableHeader variableHeader = MqttMessageIdVariableHeader.from(id);
        MqttSubscribePayload payload = new MqttSubscribePayload(subscriptions);
        MqttSubscribeMessage message = new MqttSubscribeMessage(fixedHeader, variableHeader, payload);
        this.promiseBroker.add(id, subscribeFuture);
        subscribeFuture.addListener((FutureListener) (Future f) -> {
            try {
                MqttSubAckMessage subAckMessage = (MqttSubAckMessage) f.get();
                MqttSubscribeMessage subscribeMessage = MqttClientImpl.this.pendingConfirmationSubscriptions.get(subAckMessage.variableHeader().messageId());
                if (subscribeMessage == null) {
                    logger.log(Level.INFO, String.format("Collection of waiting subscriptions returned null instead subscribeMessage"));
                    System.out.println(String.format("Collection of waiting subscriptions returned null instead subscribeMessage"));
                    return;
                }

                List<MqttTopicSubscription> topics = subscribeMessage.payload().topicSubscriptions();
                List<Integer> subAckQos = subAckMessage.payload().grantedQoSLevels();
                if (subAckQos.size() != topics.size()) {
                    logger.log(Level.INFO, String.format("Number of topics to subscribe is not match number of returned granted QOS. Number of returned QoS %s. Amount topics %s", subAckQos.size(), topics.size()));
                    System.out.println(String.format("Number of topics to subscribe is not match number of returned granted QOS. Number of returned QoS  %s. Amount topics %s", subAckQos.size(), topics.size()));
                } else {
                    for (int i = 0; i < subAckQos.size(); i++) {
                        if (subAckQos.get(i) == topics.get(i).qualityOfService().value()) {
                            MqttClientImpl.this.activeTopics.put(topics.get(i).topicName(), topics.get(i));
                            logger.log(Level.INFO, String.format("Subscribed on topic %s with Qos %s.", topics.get(i).topicName(), topics.get(i).qualityOfService()));
                            System.out.println(String.format("Subscribed on topic %s with Qos %s.", topics.get(i).topicName(), topics.get(i).qualityOfService()));
                        } else {
                            logger.log(Level.INFO, String.format("Subscription on topic %s with Qos %s failed. Returned Qos %s", topics.get(i).topicName(), topics.get(i).qualityOfService(), subAckQos.get(i)));
                            System.out.println(String.format("Subscription on topic %s with Qos %s failed. Returned Qos %s", topics.get(i).topicName(), topics.get(i).qualityOfService(), subAckQos.get(i)));
                        }

                    }
                }

                MqttClientImpl.this.pendingConfirmationSubscriptions.remove(subAckMessage.variableHeader().messageId());
            } catch (InterruptedException ex) {
                logger.log(Level.SEVERE, null, ex);
            } catch (ExecutionException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        });

        this.pendingConfirmationSubscriptions.put(id, message);

//        for (IntObjectMap.PrimitiveEntry<MqttSubscribeMessage> v : this.waitingSubscriptions.entries()) {
//            System.out.println(String.format("method subscribe. waitingSubscriptions. key %s value %s", v.key(), v.value()));
//        }
//
//        Iterator it = this.activeSubscriptions.entrySet().iterator();
//        while (it.hasNext()) {
//            Map.Entry pair = (Map.Entry) it.next();
//            System.out.println(pair.getKey() + " = " + pair.getValue());
//            it.remove(); // avoids a ConcurrentModificationException
//        }
        this.writeAndFlush(message);
        System.out.println(String.format("Sent subscribe message %s.", message));
        logger.log(Level.INFO, String.format("Sent subscribe message %s.", message));

        return subscribeFuture;
    }

    public Promise<?> publish(String topic, ByteBuf payload, MqttQoS qos, boolean retain) {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBLISH, false, qos, retain, 0);
        int id = getNewMessageId();
        MqttPublishVariableHeader variableHeader = new MqttPublishVariableHeader(topic, id);
        MqttPublishMessage message = new MqttPublishMessage(fixedHeader, variableHeader, payload);

        Promise<?> publishFuture = new DefaultPromise<>(this.workerGroup.next());
        if (qos == MqttQoS.AT_MOST_ONCE) {
            publishFuture.setSuccess(null);

        } else if (qos == MqttQoS.AT_LEAST_ONCE) {
            this.promiseBroker.add(id, publishFuture);
            publishFuture.addListener((FutureListener) (Future f) -> {
                MqttPubAckMessage pubAckMessage = (MqttPubAckMessage) f.get();
                MqttPublishMessage publishMessage = MqttClientImpl.this.pendingPubAck.get(pubAckMessage.variableHeader().messageId());
                if (publishMessage == null) {
                    logger.log(Level.INFO, String.format("Collection of waiting confirmation publish QoS1 messages returned null instead publishMessage"));
                    System.out.println(String.format("Collection of waiting confirmation publish QoS1 messages returned null instead publishMessage"));
                    return;
                }
                this.pendingPubAck.remove(pubAckMessage.variableHeader().messageId());
//                publishMessage.release();	//???
            });
            this.pendingPubAck.put(id, message);

        } else if (qos == MqttQoS.EXACTLY_ONCE) {
            this.promiseBroker.add(id, publishFuture);
            publishFuture.addListener((FutureListener) (Future f) -> {
                MqttMessage pubRecMessage = (MqttMessage) f.get();
                MqttMessageIdVariableHeader idVariableHeader = (MqttMessageIdVariableHeader) pubRecMessage.variableHeader();
                MqttPublishMessage publishMessage = MqttClientImpl.this.pendingPubRec.get(idVariableHeader.messageId());
                if (publishMessage == null) {
                    logger.log(Level.INFO, String.format("Collection of waiting confirmation publish QoS2 messages returned null instead publishMessage"));
                    System.out.println(String.format("Collection of waiting confirmation publish QoS2 messages returned null instead publishMessage"));
                    return;
                }
                this.pendingPubRec.remove(idVariableHeader.messageId());
//                publishMessage.release();	//???
            });
            this.pendingPubRec.put(id, message);

        } else {
            //throw exception("Invalid MqttQoS");
        }

        this.writeAndFlush(message);
        System.out.println(String.format("Sent publish message %s.", message));
        logger.log(Level.INFO, String.format("Sent publish message %s.", message));

        return publishFuture;
    }

    public Promise<MqttUnsubAckMessage> unsubscribe(List<String> topics) {
        pendingConfirmationSubscriptions.keySet().removeAll(topics);

        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.UNSUBSCRIBE, false, MqttQoS.AT_LEAST_ONCE, false, 0);
        int id = getNewMessageId();
        MqttMessageIdVariableHeader variableHeader = MqttMessageIdVariableHeader.from(id);
        MqttUnsubscribePayload payload = new MqttUnsubscribePayload(topics);
        MqttUnsubscribeMessage message = new MqttUnsubscribeMessage(fixedHeader, variableHeader, payload);

        Promise<MqttUnsubAckMessage> unsubscribeFuture = new DefaultPromise<>(this.workerGroup.next());
        this.promiseBroker.add(id, unsubscribeFuture);
        unsubscribeFuture.addListener((FutureListener) (Future f) -> {
            MqttUnsubAckMessage unsubAckMessage = (MqttUnsubAckMessage) f.get();
            MqttUnsubscribeMessage unsubscribeMessage = MqttClientImpl.this.pendingConfirmationUnsubscriptions.get(unsubAckMessage.variableHeader().messageId());
            if (unsubscribeMessage == null) {
                logger.log(Level.INFO, String.format("Collection of waiting unsubscriptions returned null instead unsubscribeMessage"));
                System.out.println(String.format("Collection of waiting unsubscriptions returned null instead unsubscribeMessage"));
                return;
            }
            activeTopics.keySet().removeAll(unsubscribeMessage.payload().topics());
            this.pendingConfirmationUnsubscriptions.remove(unsubAckMessage.variableHeader().messageId());
        });
        this.pendingConfirmationUnsubscriptions.put(id, message);

        this.writeAndFlush(message);
        System.out.println(String.format("Sent unsubscribe message %s.", message));
        logger.log(Level.INFO, String.format("Sent unsubscribe message %s.", message));

        return unsubscribeFuture;
    }

    public void disconnect(byte reasonCode) {
        MqttFixedHeader mqttFixedHeader = new MqttFixedHeader(MqttMessageType.DISCONNECT, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttReasonCodeAndPropertiesVariableHeader mqttDisconnectVariableHeader = new MqttReasonCodeAndPropertiesVariableHeader(reasonCode, MqttProperties.NO_PROPERTIES);
        MqttMessage message = new MqttMessage(mqttFixedHeader, mqttDisconnectVariableHeader);

        this.writeAndFlush(message);

        logger.log(Level.INFO, String.format("Sent disconnection message %s", message));
        System.out.println(String.format("Sent disconnection message %s", message));
    }

    public void shutdown() {
        if (this.channel != null) {
            this.channel.close();
        }
        this.workerGroup.shutdownGracefully();

    }

    private ChannelFuture writeAndFlush(Object message) {
        if (this.channel == null) {
            return null;
        }
        if (this.channel.isActive()) {
            return this.channel.writeAndFlush(message);
        }
        return this.channel.newFailedFuture(new RuntimeException("Channel is closed"));
    }

    private int getNewMessageId() {
        this.nextMessageId.compareAndSet(0xffff, 1);
        return this.nextMessageId.getAndIncrement();
    }
}
