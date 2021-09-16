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

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;
import ru.maxeltr.mqttClient.Config.Config;
import ru.maxeltr.mqttClient.Service.MessageHandler;

/**
 *
 * @author Maxim Eltratov <<Maxim.Eltratov@ya.ru>>
 */
public class MqttChannelInitializer extends ChannelInitializer<SocketChannel> implements ApplicationContextAware {

    private static final Logger logger = Logger.getLogger(MqttChannelInitializer.class.getName());

//    private final MqttDecoder mqttDecoder;
//    private final MqttEncoder mqttEncoder;
//    private final ChannelHandler idleStateHandler;
//    private final ChannelHandler mqttPingHandler;
//    private final ChannelHandler mqttPublishHandler;
//    private final ChannelHandler mqttConnectHandler;
//    private final ChannelHandler mqttSubscriptionHandler;
//    private final ChannelHandler exceptionHandler;
    private final Config config;
    private final PromiseBroker promiseBroker;
    private final MessageHandler messageHandler;
    private final ThreadPoolTaskScheduler threadPoolTaskScheduler;
    private final PeriodicTrigger retransmitPeriodicTrigger;
    private final PeriodicTrigger pingPeriodicTrigger;
    private final ApplicationEventPublisher applicationEventPublisher;
    private ApplicationContext appContext;

    public MqttChannelInitializer(
            //            MqttDecoder mqttDecoder,
            //            MqttEncoder mqttEncoder,
            //            ChannelHandler idleStateHandler,
            //            ChannelHandler mqttPingHandler,
            //            ChannelHandler mqttConnectHandler,
            //            ChannelHandler mqttSubscriptionHandler,
            //            ChannelHandler mqttPublishHandler,
            //            ChannelHandler exceptionHandler
            Config config,
            PromiseBroker promiseBroker,
            MessageHandler messageHandler,
            ThreadPoolTaskScheduler threadPoolTaskScheduler,
            PeriodicTrigger retransmitPeriodicTrigger,
            ApplicationEventPublisher applicationEventPublisher,
            PeriodicTrigger pingPeriodicTrigger
    ) {
//        this.mqttDecoder = mqttDecoder;
//        this.mqttEncoder = mqttEncoder;
//        this.idleStateHandler = idleStateHandler;
//        this.mqttPingHandler = mqttPingHandler;
//        this.mqttConnectHandler = mqttConnectHandler;
//        this.mqttSubscriptionHandler = mqttSubscriptionHandler;
//        this.mqttPublishHandler = mqttPublishHandler;
//        this.exceptionHandler = exceptionHandler;
        this.config = config;
        this.promiseBroker = promiseBroker;
        this.messageHandler = messageHandler;
        this.threadPoolTaskScheduler = threadPoolTaskScheduler;
        this.retransmitPeriodicTrigger = retransmitPeriodicTrigger;
        this.applicationEventPublisher = applicationEventPublisher;
        this.pingPeriodicTrigger = pingPeriodicTrigger;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
//        ch.pipeline().addLast("mqttDecoder", this.mqttDecoder);
//        ch.pipeline().addLast("mqttEncoder", this.mqttEncoder);
//        ch.pipeline().addLast("idleStateHandler", this.idleStateHandler);
//        ch.pipeline().addLast("mqttPingHandler", this.mqttPingHandler);
//        ch.pipeline().addLast("mqttConnectHandler", this.mqttConnectHandler);
//        ch.pipeline().addLast("mqttSubscriptionHandler", this.mqttSubscriptionHandler);
//        ch.pipeline().addLast("mqttPublishHandler", this.mqttPublishHandler);
////        ch.pipeline().addLast(new LoggingHandler(LogLevel.WARN));
//        ch.pipeline().addLast("exceptionHandler", this.exceptionHandler);

        ch.pipeline().addLast("mqttDecoder", this.createMqttDecoder());
        ch.pipeline().addLast("mqttEncoder", this.createMqttEncoder());
        ch.pipeline().addLast("idleStateHandler", this.createIdleStateHandler());
        ch.pipeline().addLast("mqttPingHandler", this.createMqttPingHandler());
        ch.pipeline().addLast("mqttConnectHandler", this.createMqttConnectHandler());
        ch.pipeline().addLast("mqttSubscriptionHandler", this.createMqttSubscriptionHandler());
        ch.pipeline().addLast("mqttPublishHandler", this.createMqttPublishHandler());
//        ch.pipeline().addLast(new LoggingHandler(LogLevel.WARN));
        ch.pipeline().addLast("exceptionHandler", this.createExceptionHandler());

    }

    private MqttDecoder createMqttDecoder() {
        int maxBytesInMessage = Integer.parseInt(this.config.getProperty("maxBytesInMessage", "8092"));
        return new MqttDecoder(maxBytesInMessage);
    }

    private MqttEncoder createMqttEncoder() {
        return MqttEncoder.INSTANCE;
    }

    private IdleStateHandler createIdleStateHandler() {
        int keepAliveTimer = Integer.parseInt(this.config.getProperty("keepAliveTimer", "20"));
        return new IdleStateHandler(0, keepAliveTimer, 0, TimeUnit.SECONDS);
    }

    private MqttPingScheduleHandler createMqttPingHandler() {
        MqttPingScheduleHandler mqttPingHandler = new MqttPingScheduleHandler(this.config, this.threadPoolTaskScheduler, this.pingPeriodicTrigger);
        AutowireCapableBeanFactory autowireCapableBeanFactory = this.appContext.getAutowireCapableBeanFactory();
        autowireCapableBeanFactory.autowireBean(mqttPingHandler);
        autowireCapableBeanFactory.initializeBean(mqttPingHandler, "mqttPingHandler");

        return mqttPingHandler;
    }

    private MqttPublishHandler createMqttPublishHandler() {
        MqttPublishHandler mqttPublishHandler = new MqttPublishHandler(
                this.promiseBroker,
                this.messageHandler,
                this.config,
                this.threadPoolTaskScheduler,
                this.retransmitPeriodicTrigger,
                this.applicationEventPublisher
        );
//        this.appContext.addApplicationListener(mqttPublishHandler);
        AutowireCapableBeanFactory autowireCapableBeanFactory = this.appContext.getAutowireCapableBeanFactory();
        autowireCapableBeanFactory.autowireBean(mqttPublishHandler);
        autowireCapableBeanFactory.initializeBean(mqttPublishHandler, "mqttPublishHandler");

        return mqttPublishHandler;
    }

    private MqttConnectHandler createMqttConnectHandler() {
        return new MqttConnectHandler(this.promiseBroker, this.config, this.applicationEventPublisher);
    }

    private MqttSubscriptionHandler createMqttSubscriptionHandler() {
        return new MqttSubscriptionHandler(this.promiseBroker, this.config);
    }

    private MqttExceptionHandler createExceptionHandler() {
        return new MqttExceptionHandler();
    }

    @Override
    public void setApplicationContext(ApplicationContext appContext) throws BeansException {
        this.appContext = appContext;
    }
}
