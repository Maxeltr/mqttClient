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

import ru.maxeltr.mqttClient.Service.MessageDispatcher;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
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
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;
import ru.maxeltr.mqttClient.Config.Config;
import ru.maxeltr.mqttClient.Service.MessageHandler;

/**
 *
 * @author Maxim Eltratov <<Maxim.Eltratov@ya.ru>>
 */
public class MqttClientImpl implements ApplicationListener<ApplicationEvent>, CommandLineRunner  {

    private static final Logger logger = Logger.getLogger(MqttClientImpl.class.getName());

    @Autowired
    private ApplicationContext appContext;

    private Channel channel;

//    private final MqttChannelInitializer mqttChannelInitializer;
    private EventLoopGroup workerGroup;

    private Bootstrap bootstrap;

    private final Config config;

    private final Boolean reconnect;

    private final Integer reconnectDelay;

    private Boolean reconnecting;

    private Integer reconnectAttempts;

    private final PromiseBroker promiseBroker;

    private final AtomicInteger nextMessageId = new AtomicInteger(1);

    private MessageDispatcher messageDispatcher;

    private ThreadPoolTaskScheduler taskScheduler;

    private PeriodicTrigger periodicTrigger;

    private ScheduledFuture<?> retransmitScheduledFuture;

//    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    private MqttChannelInitializer mqttChannelInitializer;

    private final Map<Integer, MqttSubscribeMessage> pendingConfirmationSubscriptions = Collections.synchronizedMap(new LinkedHashMap());

    private final Map<Integer, MqttUnsubscribeMessage> pendingConfirmationUnsubscriptions = Collections.synchronizedMap(new LinkedHashMap());

    private final Map<String, MqttTopicSubscription> activeTopics = Collections.synchronizedMap(new LinkedHashMap());

    private final Map<Integer, MqttPublishMessage> pendingPubRec = Collections.synchronizedMap(new LinkedHashMap());

    private final Map<Integer, MqttPublishMessage> pendingPubAck = Collections.synchronizedMap(new LinkedHashMap());

    public MqttClientImpl(
            Config config,
            PromiseBroker promiseBroker,
            ThreadPoolTaskScheduler taskScheduler,
            PeriodicTrigger periodicTrigger,
            MqttChannelInitializer mqttChannelInitializer,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.reconnecting = false;
        this.reconnectAttempts = 0;
        this.config = config;
        this.promiseBroker = promiseBroker;
        this.taskScheduler = taskScheduler;
        this.periodicTrigger = periodicTrigger;
        this.mqttChannelInitializer = mqttChannelInitializer;
        this.applicationEventPublisher = applicationEventPublisher;

        this.reconnect = Boolean.parseBoolean(this.config.getProperty("reconnect", "true"));
        this.reconnectDelay = Integer.parseInt(this.config.getProperty("reconnectDelay", "2"));
    }

    public void setMessageDispatcher(MessageDispatcher messageDispatcher) {
        this.messageDispatcher = messageDispatcher;
    }

//    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof PingTimeoutEvent) {
            this.reconnect((MqttPingScheduleHandler) event.getSource());
            System.out.println(String.format("PingTimeoutEvent was received."));
            logger.log(Level.INFO, String.format("PingTimeoutEvent was received."));
        } else if (event instanceof ShutdownEvent) {
            this.cancelRetransmit();
            System.out.println(String.format("ShutdownEvent was received."));
            logger.log(Level.INFO, String.format("ShutdownEvent was received."));
        }
    }

    public void clearPendingMessages() {
        this.pendingConfirmationSubscriptions.clear();
        this.pendingConfirmationUnsubscriptions.clear();
        this.pendingPubRec.clear();
        this.pendingPubAck.clear();
    }

    @Override
    public void run(String... args) {
        String host = config.getProperty("host", "");
        if (host.trim().isEmpty()) {
            throw new IllegalStateException("Invalid host property");
        }

        String port = config.getProperty("port", "");
        if (port.trim().isEmpty()) {
            throw new IllegalStateException("Invalid port property");
        }

        this.connect(host, Integer.parseInt(port), f -> {
            if (f.isSuccess()) {
                this.subscribeFromConfig();
            }
        });
    }

    public Promise<MqttSubAckMessage> subscribeFromConfig() {
        String commandTopic = config.getProperty("receivingCommandsTopic", "");
        if (commandTopic.trim().isEmpty()) {
            throw new IllegalStateException("Invalid receivingCommandsTopic property");
        }

        String commandRepliesTopic = config.getProperty("receivingCommandRepliesTopic", "");
        if (commandRepliesTopic.trim().isEmpty()) {
            throw new IllegalStateException("Invalid receivingCommandRepliesTopic property");
        }

        String commandQos = config.getProperty("commandQos", "");
        if (commandQos.trim().isEmpty()) {
            throw new IllegalStateException("Invalid commandQos property");
        }

        Map<String, MqttQoS> subTopics = new HashMap();

        List<String> subQos0Topics = Arrays.asList(config.getProperty("subQos0Topics", "").split("\\s*,\\s*"));
        for (String topic : subQos0Topics) {
            if (!topic.trim().isEmpty()) {
                subTopics.put(topic, MqttQoS.AT_MOST_ONCE);
            }
        }

        List<String> subQos1Topics = Arrays.asList(config.getProperty("subQos1Topics", "").split("\\s*,\\s*"));
        for (String topic : subQos1Topics) {
            if (!topic.trim().isEmpty()) {
                subTopics.put(topic, MqttQoS.AT_LEAST_ONCE);
            }
        }

        List<String> subQos2Topics = Arrays.asList(config.getProperty("subQos2Topics", "").split("\\s*,\\s*"));
        for (String topic : subQos2Topics) {
            if (!topic.trim().isEmpty()) {
                subTopics.put(topic, MqttQoS.EXACTLY_ONCE);
            }
        }

        subTopics.put(commandTopic, MqttQoS.valueOf(commandQos));
        subTopics.put(commandRepliesTopic, MqttQoS.valueOf(commandQos));

        return this.subscribe(subTopics);
    }

    /**
     * Connect to the specified hostname/ip using the specified port
     *
     * @param host The ip address or host to connect to
     * @param port The tcp port to connect to
     * @return
     */
    public Promise<MqttConnAckMessage> connect(String host, int port) {
        workerGroup = new NioEventLoopGroup();
        bootstrap = new Bootstrap();
        bootstrap.group(this.workerGroup);
        bootstrap.channel(NioSocketChannel.class);
//        bootstrap.handler(new MyMqttChannelInitializer());

//        ThreadPoolTaskScheduler threadPoolTaskScheduler = (ThreadPoolTaskScheduler) this.appContext.getBean("threadPoolTaskScheduler");
//        PeriodicTrigger retransmitPeriodicTrigger = (PeriodicTrigger) this.appContext.getBean("retransmitPeriodicTrigger");
//        MessageHandler messageHandler = (MessageHandler) this.appContext.getBean("messageHandler");
//
//        int maxBytesInMessage = Integer.parseInt(config.getProperty("maxBytesInMessage", "8092"));
//        MqttDecoder mqttDecoder = new MqttDecoder(maxBytesInMessage);
//
//        int keepAliveTimer = Integer.parseInt(config.getProperty("keepAliveTimer", "20"));
//        IdleStateHandler idleStateHandler = new IdleStateHandler(0, keepAliveTimer, 0, TimeUnit.SECONDS);
//        MqttPingScheduleHandler mqttPingScheduleHandler = new MqttPingScheduleHandler(config, threadPoolTaskScheduler);
//        MqttPublishHandler mqttPublishHandler = new MqttPublishHandler(promiseBroker, messageHandler, config, threadPoolTaskScheduler, retransmitPeriodicTrigger);
//        MqttConnectHandler mqttConnectHandler = new MqttConnectHandler(promiseBroker, config);
//        MqttSubscriptionHandler mqttSubscriptionHandler = new MqttSubscriptionHandler(promiseBroker, config);
//        MqttExceptionHandler mqttExceptionHandler = new MqttExceptionHandler();
//        MqttChannelInitializer mqttChannelInitializer = new MqttChannelInitializer(mqttDecoder, MqttEncoder.INSTANCE, idleStateHandler, mqttPingScheduleHandler, mqttConnectHandler, mqttSubscriptionHandler, mqttPublishHandler, mqttExceptionHandler);
//        bootstrap.handler(mqttChannelInitializer);
//        bootstrap.handler((MqttChannelInitializer) MqttClientImpl.this.appContext.getBean("mqttChannelInitializer"));
        bootstrap.handler(this.mqttChannelInitializer);

        Promise<MqttConnAckMessage> connectFuture = new DefaultPromise<>(this.workerGroup.next());
        this.promiseBroker.setConnectFuture(connectFuture);
        this.bootstrap.remoteAddress(host, port);

        ChannelFuture future = bootstrap.connect();
        future.addListener((ChannelFutureListener) f -> MqttClientImpl.this.channel = f.channel());

        logger.log(Level.INFO, String.format("Connecting to %s via port %s.", host, port));
        System.out.println(String.format("Connecting to %s via port %s.", host, port));

        return connectFuture;
    }

    /**
     * Connect to the specified hostname/ip using the specified port
     *
     * @param host The ip address or host to connect to
     * @param port The tcp port to connect to
     * @listener The listener is notified when connecting is done
     * @return
     */
    public Promise<MqttConnAckMessage> connect(String host, int port, GenericFutureListener listener) {
        Promise<MqttConnAckMessage> connectFuture = this.connect(host, port);
        connectFuture.addListener(listener);

        return connectFuture;
    }

    public void reconnect(MqttPingScheduleHandler pingHandler) {
        if (!this.reconnect) {
            pingHandler.cancelPing();
            logger.log(Level.INFO, String.format("Reconnect is not allowed by config."));
            System.out.println(String.format("Reconnect is not allowed by config."));
            return;
        }

        String host = config.getProperty("host", "");
        if (host.trim().isEmpty()) {
            throw new IllegalStateException("Invalid host property");
        }

        String port = config.getProperty("port", "");
        if (port.trim().isEmpty()) {
            throw new IllegalStateException("Invalid port property");
        }

        if (this.reconnecting) {
            logger.log(Level.INFO, String.format("Unable to start reconnecting. The connection is being reconnected."));
            System.out.println(String.format("Unable to start reconnecting. The connection is being reconnected."));
            return;
        }
        this.reconnecting = true;
        this.reconnectAttempts = this.reconnectAttempts + 1;

        System.out.println(String.format("Start reconnect! Attempt %s. %n%n", this.reconnectAttempts));
        logger.log(Level.INFO, String.format("Start reconnect! Attempt %s.", this.reconnectAttempts));
        this.shutdown();

        Boolean cleanSeesion = Boolean.parseBoolean(this.config.getProperty("cleanSeesion", "true"));
        if (cleanSeesion) {
            this.clearPendingMessages();
        }
        this.activeTopics.clear();

        try {
            Thread.sleep(TimeUnit.SECONDS.toMillis(this.reconnectDelay));
            this.connect(host, Integer.parseInt(port), f -> {
                if (f.isSuccess()) {
                    logger.log(Level.INFO, String.format("Reconnection is successful."));
                    System.out.println(String.format("Reconnection is successful."));
                    this.subscribeFromConfig();
                    pingHandler.cancelPing();
                }
                this.reconnecting = false;
            });
        } catch (InterruptedException ex) {
            this.reconnecting = false;
            Logger.getLogger(MqttClientImpl.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

//    public Boolean isInetAvailable() {
//
//    }
    public Promise<MqttSubAckMessage> subscribe(Map<String, MqttQoS> topicsAndQos) {
        Promise<MqttSubAckMessage> subscribeFuture = new DefaultPromise<>(this.workerGroup.next());
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.SUBSCRIBE, false, MqttQoS.AT_LEAST_ONCE, false, 0);

        List<MqttTopicSubscription> subscriptions = new ArrayList<>();
        topicsAndQos.forEach((k, v) -> {
            MqttTopicSubscription subscription = new MqttTopicSubscription(k, v);
            subscriptions.add(subscription);
            logger.log(Level.INFO, String.format("Subscribe on topic: %s", subscription.topicName()));
            System.out.println(String.format("Subscribe on topic: %s", subscription.topicName()));
        });

        int id = getNewMessageId();
        MqttMessageIdVariableHeader variableHeader = MqttMessageIdVariableHeader.from(id);
        MqttSubscribePayload payload = new MqttSubscribePayload(subscriptions);
        MqttSubscribeMessage message = new MqttSubscribeMessage(fixedHeader, variableHeader, payload);
        this.promiseBroker.add(id, subscribeFuture);
        subscribeFuture.addListener((FutureListener) (Future f) -> {
//            try {
            MqttSubAckMessage subAckMessage = (MqttSubAckMessage) f.get();
            MqttSubscribeMessage subscribeMessage = MqttClientImpl.this.pendingConfirmationSubscriptions.get(subAckMessage.variableHeader().messageId());
            if (subscribeMessage == null) {
                logger.log(Level.WARNING, String.format("Collection of waiting subscriptions returned null instead subscribeMessage"));
                System.out.println(String.format("Collection of waiting subscriptions returned null instead subscribeMessage"));
                return;
            }

            List<MqttTopicSubscription> topics = subscribeMessage.payload().topicSubscriptions();
            List<Integer> subAckQos = subAckMessage.payload().grantedQoSLevels();
            if (subAckQos.size() != topics.size()) {
                logger.log(Level.WARNING, String.format("Number of topics to subscribe is not match number of returned granted QOS. Number of returned QoS %s. Amount topics %s", subAckQos.size(), topics.size()));
                System.out.println(String.format("Number of topics to subscribe is not match number of returned granted QOS. Number of returned QoS  %s. Amount topics %s", subAckQos.size(), topics.size()));
            } else {
                for (int i = 0; i < subAckQos.size(); i++) {
                    if (subAckQos.get(i) == topics.get(i).qualityOfService().value()) {
                        MqttClientImpl.this.activeTopics.put(topics.get(i).topicName(), topics.get(i));
                        logger.log(Level.INFO, String.format("Subscribed on topic \"%s\" with Qos %s.", topics.get(i).topicName(), topics.get(i).qualityOfService()));
                        System.out.println(String.format("Subscribed on topic \"%s\" with Qos %s.", topics.get(i).topicName(), topics.get(i).qualityOfService()));
                    } else {
                        logger.log(Level.INFO, String.format("Subscription on topic \"%s\" with Qos %s failed. Returned Qos %s", topics.get(i).topicName(), topics.get(i).qualityOfService(), subAckQos.get(i)));
                        System.out.println(String.format("Subscription on topic \"%s\" with Qos %s failed. Returned Qos %s", topics.get(i).topicName(), topics.get(i).qualityOfService(), subAckQos.get(i)));
                    }

                }
            }

            MqttClientImpl.this.pendingConfirmationSubscriptions.remove(subAckMessage.variableHeader().messageId());
            logger.log(Level.FINE, String.format("Remove (from pending subscriptions) saved subscription message id: %s", subAckMessage.variableHeader().messageId()));
            System.out.println(String.format("Remove (from pending subscriptions) saved subscription message id %s", subAckMessage.variableHeader().messageId()));
//            } catch (InterruptedException ex) {
//                logger.log(Level.SEVERE, null, ex);
//            } catch (ExecutionException ex) {
//                logger.log(Level.SEVERE, null, ex);
//            }
        });

        this.pendingConfirmationSubscriptions.put(id, message);
        logger.log(Level.FINE, String.format("Add (to pending subscription collection) subscription message %s", message));
        System.out.println(String.format("Add (to pending subscription collection) subscription message %s", message));

        this.writeAndFlush(message);
        System.out.println(String.format("Sent subscribe message %s.", message));
        logger.log(Level.INFO, String.format("Sent subscribe message id: %s, d: %s, q: %s, r: %s. Message: %s", message.variableHeader().messageId(), message.fixedHeader().isDup(), message.fixedHeader().qosLevel(), message.fixedHeader().isRetain(), message));

        return subscribeFuture;
    }

    public Promise<?> publish(String topic, ByteBuf payload, MqttQoS qos, boolean retain) {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBLISH, false, qos, retain, 0);
        int id = qos == MqttQoS.AT_MOST_ONCE ? -1 : this.getNewMessageId();
        MqttPublishVariableHeader variableHeader = new MqttPublishVariableHeader(topic, id);
        MqttPublishMessage message = new MqttPublishMessage(fixedHeader, variableHeader, payload);

        Promise<?> publishFuture = new DefaultPromise<>(this.workerGroup.next());
        if (qos == MqttQoS.AT_MOST_ONCE) {
            if (!publishFuture.isDone()) {
                publishFuture.setSuccess(null);
            }

        } else if (qos == MqttQoS.AT_LEAST_ONCE) {
            this.promiseBroker.add(id, publishFuture);
            publishFuture.addListener((FutureListener) (Future f) -> {
                MqttPubAckMessage pubAckMessage = (MqttPubAckMessage) f.get();
                MqttPublishMessage publishMessage = MqttClientImpl.this.pendingPubAck.get(pubAckMessage.variableHeader().messageId());
                if (publishMessage == null) {
                    logger.log(Level.WARNING, String.format("Collection of waiting confirmation publish QoS1 messages returned null instead publishMessage"));
                    System.out.println(String.format("Collection of waiting confirmation publish QoS1 messages returned null instead publishMessage"));
                    return;
                }
                this.pendingPubAck.remove(pubAckMessage.variableHeader().messageId());
                logger.log(Level.FINE, String.format("Remove (from pending PUBACK) saved publish message id: %s", pubAckMessage.variableHeader().messageId()));
                System.out.println(String.format("Remove (from pending PUBACK) saved publish message id %s", pubAckMessage.variableHeader().messageId()));
                ReferenceCountUtil.release(publishMessage);
            });
            ReferenceCountUtil.retain(message); //TODO is it nessesary?
            this.pendingPubAck.put(id, message);
            logger.log(Level.FINE, String.format("Add (to pending PUBACK collection) publish message id: %s", message.variableHeader().packetId()));
            System.out.println(String.format("Add (to pending PUBACK collection) publish message id: %s", message.variableHeader().packetId()));

        } else if (qos == MqttQoS.EXACTLY_ONCE) {
            this.promiseBroker.add(id, publishFuture);
            publishFuture.addListener((FutureListener) (Future f) -> {
                MqttMessage pubRecMessage = (MqttMessage) f.get();
                MqttMessageIdVariableHeader idVariableHeader = (MqttMessageIdVariableHeader) pubRecMessage.variableHeader();
                MqttPublishMessage publishMessage = MqttClientImpl.this.pendingPubRec.get(idVariableHeader.messageId());
                if (publishMessage == null) {
                    logger.log(Level.WARNING, String.format("Collection of waiting confirmation publish QoS2 messages returned null instead publishMessage"));
                    System.out.println(String.format("Collection of waiting confirmation publish QoS2 messages returned null instead publishMessage"));
                    return;
                }
                this.pendingPubRec.remove(idVariableHeader.messageId());
                logger.log(Level.FINE, String.format("Remove (from pending PUBREC) saved publish message id: %s", idVariableHeader.messageId()));
                System.out.println(String.format("Remove (from pending PUBREC) saved publish message id: %s", idVariableHeader.messageId()));
                ReferenceCountUtil.release(publishMessage);
            });
            ReferenceCountUtil.retain(message); //TODO is it nessesary?
            this.pendingPubRec.put(id, message);
            logger.log(Level.FINE, String.format("Add (to pending PUBREC collection) publish message id: %s", message.variableHeader().packetId()));
            System.out.println(String.format("Add (to pending PUBREC collection) publish message id: %s", message.variableHeader().packetId()));
        } else {
            logger.log(Level.SEVERE, String.format("Invalid MqttQoS %s", qos));
            throw new IllegalArgumentException("Invalid MqttQoS given");
        }

        this.writeAndFlush(message);

        System.out.println(String.format("Sent publish message id: %s, t: %s, d: %s, q: %s, r: %s.",
                message.variableHeader().packetId(),
                message.variableHeader().topicName(),
                message.fixedHeader().isDup(),
                message.fixedHeader().qosLevel(),
                message.fixedHeader().isRetain()
        ));

        logger.log(Level.INFO, String.format("Sent publish message id: %s, t: %s, d: %s, q: %s, r: %s.",
                message.variableHeader().packetId(),
                message.variableHeader().topicName(),
                message.fixedHeader().isDup(),
                message.fixedHeader().qosLevel(),
                message.fixedHeader().isRetain()
        ));

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
                logger.log(Level.WARNING, String.format("Collection of waiting unsubscriptions returned null instead unsubscribeMessage"));
                System.out.println(String.format("Collection of waiting unsubscriptions returned null instead unsubscribeMessage"));
                return;
            }
            activeTopics.keySet().removeAll(unsubscribeMessage.payload().topics());
            this.pendingConfirmationUnsubscriptions.remove(unsubAckMessage.variableHeader().messageId());
            logger.log(Level.FINE, String.format("Remove (from pending unsubscriptions) saved unsubscriptions message id: %s", unsubAckMessage.variableHeader().messageId()));
            System.out.println(String.format("Remove (from pending unsubscriptions) saved unsubscriptions message id: %s", unsubAckMessage.variableHeader().messageId()));
        });
        this.pendingConfirmationUnsubscriptions.put(id, message);
        logger.log(Level.FINE, String.format("Add (to pending unsubscription collection) unsubscription message id: %s", message.variableHeader().messageId()));
        System.out.println(String.format("Add (to pending unsubscription collection) unsubscription message id: %s", message.variableHeader().messageId()));

        this.writeAndFlush(message);

        System.out.println(String.format("Sent unsubscribe message id: %s, d: %s, q: %s, r: %s.",
                message.variableHeader().messageId(),
                message.fixedHeader().isDup(),
                message.fixedHeader().qosLevel(),
                message.fixedHeader().isRetain()
        ));
        logger.log(Level.INFO, String.format("Sent unsubscribe message id: %s, d: %s, q: %s, r: %s.",
                message.variableHeader().messageId(),
                message.fixedHeader().isDup(),
                message.fixedHeader().qosLevel(),
                message.fixedHeader().isRetain()
        ));

        return unsubscribeFuture;
    }

    public void disconnect(byte reasonCode) {
        MqttFixedHeader mqttFixedHeader = new MqttFixedHeader(MqttMessageType.DISCONNECT, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttReasonCodeAndPropertiesVariableHeader mqttDisconnectVariableHeader = new MqttReasonCodeAndPropertiesVariableHeader(reasonCode, MqttProperties.NO_PROPERTIES);
        MqttMessage message = new MqttMessage(mqttFixedHeader, mqttDisconnectVariableHeader);

        this.writeAndFlush(message);

        logger.log(Level.INFO, String.format(
                "Sent disconnection message reason: %s, d: %s, q: %s, r: %s.",
                mqttDisconnectVariableHeader.reasonCode(),
                message.fixedHeader().isDup(),
                message.fixedHeader().qosLevel(),
                message.fixedHeader().isRetain()
        ));
        System.out.println(String.format("Sent disconnection message reason: %s, d: %s, q: %s, r: %s.",
                mqttDisconnectVariableHeader.reasonCode(),
                message.fixedHeader().isDup(),
                message.fixedHeader().qosLevel(),
                message.fixedHeader().isRetain()
        ));
    }

    public void shutdown() {
        this.publishShutdownEvent();
        if (this.channel != null) {
            this.channel.close();
        }
        this.workerGroup.shutdownGracefully();
        logger.log(Level.INFO, String.format("Shutdown gracefully"));
        System.out.println(String.format("Shutdown gracefully"));
    }

    private void publishShutdownEvent() {
        applicationEventPublisher.publishEvent(new ShutdownEvent(this, "Shutdown."));
        System.out.println(String.format("Publish ShutdownEvent."));
        logger.log(Level.INFO, String.format("Publish ShutdownEvent."));

    }

    private ChannelFuture writeAndFlush(Object message) {
        if (this.channel == null) {
            logger.log(Level.WARNING, String.format("Cannot write and flush message. Channel is null"));
            System.out.println(String.format("Cannot write and flush message. Channel is null"));
            return null;
        }
        if (this.channel.isActive()) {
            return this.channel.writeAndFlush(message);
        }
        logger.log(Level.SEVERE, String.format("Cannot write and flush message. Channel is closed."));
        System.out.println(String.format("Cannot write and flush message. Channel is closed."));
        return this.channel.newFailedFuture(new RuntimeException("Cannot write and flush message. Channel is closed."));
    }

    private int getNewMessageId() {
        this.nextMessageId.compareAndSet(0xffff, 1);
        return this.nextMessageId.getAndIncrement();
    }

    @PostConstruct
    public void scheduleRunnableWithCronTrigger() {
        this.taskScheduler.schedule(new RetransmitTask(), this.periodicTrigger);
        System.out.println(String.format("Start retransmit task. %s", this.hashCode()));
        logger.log(Level.FINE, String.format("Start retransmit task. %s", this.hashCode()));
    }

    public void cancelRetransmit() {
        this.retransmitScheduledFuture.cancel(false);
        System.out.println(String.format("Retransmit was canceled. %s", this.hashCode()));
        logger.log(Level.FINE, String.format("Retransmit was canceled. %s", this.hashCode()));
    }

    class RetransmitTask implements Runnable {

        @Override
        public void run() {
            System.out.println(String.format("Strart retransmission in MqttClientImpl"));
            logger.log(Level.FINE, String.format("Strart retransmission in MqttClientImpl"));

            int index = 1;
            synchronized (pendingConfirmationSubscriptions) {
                System.out.println(String.format("Retransmission pending confirmation subscriptions. Amount subscription messages is %s", pendingConfirmationSubscriptions.size()));
                logger.log(Level.FINE, String.format("Retransmission pending confirmation subscriptions. Amount subscription messages is %s", pendingConfirmationSubscriptions.size()));
                for (Map.Entry<Integer, MqttSubscribeMessage> pair : pendingConfirmationSubscriptions.entrySet()) {
                    if (channel.isActive()) {
                        writeAndFlush(pair.getValue());

                        System.out.println(String.format(
                                "Retransmission pending confirmation subscription. %s from %s. Message id: %s",
                                index, pendingConfirmationSubscriptions.size(),
                                pair.getValue().variableHeader().messageId()
                        ));
                        logger.log(Level.FINE, String.format(
                                "Retransmission pending confirmation subscription. %s from %s. Message id: %s",
                                index,
                                pendingConfirmationSubscriptions.size(),
                                pair.getValue().variableHeader().messageId()
                        ));

                    }
                    index++;
                }

                index = 1;
                synchronized (pendingConfirmationUnsubscriptions) {
                    System.out.println(String.format("Retransmission pending confirmation unsubscriptions. Amount unsubscription messages is %s", pendingConfirmationUnsubscriptions.size()));
                    logger.log(Level.FINE, String.format("Retransmission pending confirmation unsubscriptions. Amount unsubscription messages is %s", pendingConfirmationUnsubscriptions.size()));
                    for (Map.Entry<Integer, MqttUnsubscribeMessage> pair : pendingConfirmationUnsubscriptions.entrySet()) {
                        if (channel.isActive()) {
                            writeAndFlush(pair.getValue());

                            System.out.println(String.format(
                                    "Retransmission pending confirmation unsubscription. %s from %s. Message id: %s",
                                    index, pendingConfirmationUnsubscriptions.size(),
                                    pair.getValue().variableHeader().messageId()
                            ));
                            logger.log(Level.FINE, String.format(
                                    "Retransmission pending confirmation unsubscription. %s from %s. Message id: %s",
                                    index, pendingConfirmationUnsubscriptions.size(),
                                    pair.getValue().variableHeader().messageId()
                            ));
                        }
                        index++;
                    }
                }

                index = 1;
                synchronized (pendingPubRec) {
                    System.out.println(String.format("Retransmission publish messages for pending PUBREC publish messages (QoS2). Amount publish messages is %s", pendingPubRec.size()));
                    logger.log(Level.FINE, String.format("Retransmission publish messages for pending PUBREC publish messages (QoS2). Amount publish messages is %s", pendingPubRec.size()));
                    for (Map.Entry<Integer, MqttPublishMessage> pair : pendingPubRec.entrySet()) {
                        if (channel.isActive()) {
                            MqttPublishMessage originalMessage = (MqttPublishMessage) pair.getValue();
                            MqttFixedHeader fixedHeader = new MqttFixedHeader(
                                    originalMessage.fixedHeader().messageType(),
                                    true, //change Dup on true
                                    originalMessage.fixedHeader().qosLevel(),
                                    originalMessage.fixedHeader().isRetain(),
                                    originalMessage.fixedHeader().remainingLength()
                            );
                            MqttPublishMessage message = new MqttPublishMessage(fixedHeader, originalMessage.variableHeader(), originalMessage.payload());

                            writeAndFlush(message);
                            System.out.println(String.format(
                                    "Retransmission publish message for pending PUBREC publish message (QoS2). %s from %s. Message id: %s",
                                    index, pendingPubRec.size(),
                                    message.variableHeader().packetId()
                            ));
                            logger.log(Level.FINE, String.format(
                                    "Retransmission publish message for pending PUBREC publish message (QoS2). %s from %s. Message id: %s",
                                    index, pendingPubRec.size(),
                                    message.variableHeader().packetId()
                            ));
                        }
                        index++;
                    }
                }

                index = 1;
                synchronized (pendingPubAck) {
                    System.out.println(String.format("Retransmission publish messages for pending PUBACK publish messages (QoS1). Amount publish messages is %s", pendingPubAck.size()));
                    logger.log(Level.FINE, String.format("Retransmission publish messages for pending PUBACK publish messages (QoS1). Amount publish messages is %s", pendingPubAck.size()));
                    for (Map.Entry<Integer, MqttPublishMessage> pair : pendingPubAck.entrySet()) {
                        if (channel.isActive()) {
                            MqttPublishMessage originalMessage = (MqttPublishMessage) pair.getValue();
                            MqttFixedHeader fixedHeader = new MqttFixedHeader(
                                    originalMessage.fixedHeader().messageType(),
                                    true, //change Dup on true
                                    originalMessage.fixedHeader().qosLevel(),
                                    originalMessage.fixedHeader().isRetain(),
                                    originalMessage.fixedHeader().remainingLength()
                            );
                            MqttPublishMessage message = new MqttPublishMessage(fixedHeader, originalMessage.variableHeader(), originalMessage.payload());

                            writeAndFlush(message);
                            System.out.println(String.format(
                                    "Retransmission publish message for pending PUBACK publish message (QoS1). %s from %s. Message id: %s",
                                    index,
                                    pendingPubAck.size(),
                                    message.variableHeader().packetId()
                            ));
                            logger.log(Level.FINE, String.format(
                                    "Retransmission publish message for pending PUBACK publish message (QoS1). %s from %s. Message id: %s",
                                    index,
                                    pendingPubAck.size(),
                                    message.variableHeader().packetId()
                            ));
                        }
                        index++;
                    }
                }
                System.out.println(String.format("End retransmission in MqttClientImpl"));
                logger.log(Level.FINE, String.format("End retransmission in MqttClientImpl"));
            }
        }
    }
}
