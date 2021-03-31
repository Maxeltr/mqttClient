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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttSubscribePayload;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private final Config config;

    private final AtomicInteger nextMessageId = new AtomicInteger(1);

    protected final ConcurrentHashMap<Integer, MqttSubscribeMessage> waitingSubscriptions = new ConcurrentHashMap<>();

    protected final ConcurrentHashMap<String, MqttSubscribeMessage> activeSubscriptions = new ConcurrentHashMap<>();

    protected final Set<String> activeTopics = new HashSet<>();

    public MqttClientImpl(MqttChannelInitializer mqttChannelInitializer, Config config) {
        this.mqttChannelInitializer = mqttChannelInitializer;
        this.config = config;
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
    public Promise<MqttConnectResult> connect(String host, int port) {
        this.workerGroup = new NioEventLoopGroup();
        Promise<MqttConnectResult> connectFuture = new DefaultPromise<>(this.workerGroup.next());
        this.mqttChannelInitializer.getConnectHandler().setConnectFuture(connectFuture);
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(this.workerGroup);
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.remoteAddress(host, port);
        bootstrap.handler(this.mqttChannelInitializer);

        ChannelFuture future = bootstrap.connect();
        future.addListener((ChannelFutureListener) f -> MqttClientImpl.this.channel = f.channel());

        logger.log(Level.INFO, String.format("Connecting to %s via port %s.", host, port));
        System.out.println(String.format("Connecting to %s via port %s.", host, port));

        return connectFuture;
    }

    public Promise<MqttSubAckMessage> subscribe(String topic, MqttQoS qos) {
        Promise<MqttSubAckMessage> subscribeFuture = new DefaultPromise<>(this.workerGroup.next());
        this.mqttChannelInitializer.getSubscriptionHandler().setSubscriptionFuture(subscribeFuture);
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.SUBSCRIBE, false, MqttQoS.AT_LEAST_ONCE, false, 0);
        MqttTopicSubscription subscription = new MqttTopicSubscription(topic, qos);
        int id = getNewMessageId();
        MqttMessageIdVariableHeader variableHeader = MqttMessageIdVariableHeader.from(id);
        MqttSubscribePayload payload = new MqttSubscribePayload(Arrays.asList(subscription));
//        MqttSubscribePayload payload = new MqttSubscribePayload(Collections.singletonList(subscription));
        MqttSubscribeMessage message = new MqttSubscribeMessage(fixedHeader, variableHeader, payload);
//        GenericFutureListener<? extends io.netty.util.concurrent.Future<? super MqttSubscriptionResult>> gl;
        subscribeFuture.addListener((FutureListener) (Future f) -> {
            try {
                MqttSubAckMessage subAckMessage = (MqttSubAckMessage) f.get();
                MqttSubscribeMessage subscribeMessage = MqttClientImpl.this.waitingSubscriptions.get(subAckMessage.variableHeader().messageId());
                if (subscribeMessage == null) {
                    return;
                }

                List<MqttTopicSubscription> topics = subscribeMessage.payload().topicSubscriptions();

                List<Integer> grantedQoSLevels = subAckMessage.payload().grantedQoSLevels();

                if (grantedQoSLevels.size() != topics.size()) {

                } else {
                    for (int i = 0; i < grantedQoSLevels.size(); i++) {

                    }
                }

                for (int qoSLevel : grantedQoSLevels) {	//add
//                    String topic = topics.get(i);

                }

                MqttClientImpl.this.activeSubscriptions.put(topic, subscribeMessage);
                MqttClientImpl.this.waitingSubscriptions.remove(subAckMessage.variableHeader().messageId());
            } catch (InterruptedException ex) {
                logger.log(Level.SEVERE, null, ex);
            } catch (ExecutionException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        });

        this.waitingSubscriptions.put(id, message);

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

        return subscribeFuture;
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
