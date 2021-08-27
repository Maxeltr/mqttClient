/*
 * The MIT License
 *
 * Copyright 2021 Maxim Eltratov <Maxim.Eltratov@yandex.ru>.
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
package ru.maxeltr.mqttClient.Config;

import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.logging.LogManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;
import ru.maxeltr.mqttClient.Mqtt.MessageDispatcher;
import ru.maxeltr.mqttClient.Mqtt.MqttExceptionHandler;
import ru.maxeltr.mqttClient.Mqtt.MqttChannelInitializer;
import ru.maxeltr.mqttClient.Mqtt.MqttClientImpl;
import ru.maxeltr.mqttClient.Mqtt.MqttConnectHandler;
import ru.maxeltr.mqttClient.Mqtt.MqttPingScheduleHandler;
import ru.maxeltr.mqttClient.Mqtt.MqttPublishHandler;
import ru.maxeltr.mqttClient.Mqtt.MqttSubscriptionHandler;
import ru.maxeltr.mqttClient.Mqtt.PromiseBroker;
import ru.maxeltr.mqttClient.Service.CommandService;
import ru.maxeltr.mqttClient.Service.MessageHandler;

/**
 *
 * @author Maxim Eltratov <<Maxim.Eltratov@ya.ru>>
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AppAnnotationConfig  {

    public static final String CONFIG_PATHNAME = "Configuration.xml";

    public static final String UNIC_BINDING_NAME = "server.mqtt";

    public AppAnnotationConfig() {
        try {
            LogManager.getLogManager().readConfiguration(AppAnnotationConfig.class.getResourceAsStream("/logging.properties")
            );
        } catch (IOException | SecurityException ex) {
            System.err.println("Could not setup logger configuration: " + ex.toString());
        }
    }

    /*
     * For async calls of methods with @async annotations
     */
    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
        threadPoolTaskExecutor.setThreadNamePrefix("Async-");
        threadPoolTaskExecutor.setCorePoolSize(10);
        threadPoolTaskExecutor.setMaxPoolSize(10);
        threadPoolTaskExecutor.setQueueCapacity(1000);
//		threadPoolTaskExecutor.afterPropertiesSet();
        threadPoolTaskExecutor.initialize();

        return threadPoolTaskExecutor;
    }

    /*
     * For scheduling a ping request with a fixed delay which is defined by config (MqttPingScheduleHandler)
     */
    @Bean
    public ThreadPoolTaskScheduler threadPoolTaskScheduler() {
        ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
        threadPoolTaskScheduler.setPoolSize(5);
        threadPoolTaskScheduler.setThreadNamePrefix("ThreadPoolTaskScheduler");
        return threadPoolTaskScheduler;
    }

    /*
     * For scheduling a ping request with a fixed delay which is defined by config (MqttPingScheduleHandler)
     */
    @Bean
    public PeriodicTrigger periodicTrigger(Config config) {
        String keepAliveTimer = config.getProperty("keepAliveTimer", "");
        if (keepAliveTimer.trim().isEmpty()) {
            throw new IllegalStateException("Invalid keepAliveTimer property");
        }
        PeriodicTrigger periodicTrigger = new PeriodicTrigger(Long.parseLong(keepAliveTimer, 10), TimeUnit.SECONDS);
        periodicTrigger.setFixedRate(true);
        periodicTrigger.setInitialDelay(Long.parseLong(keepAliveTimer, 10));
        return periodicTrigger;
    }

    @Bean
    public Config config() {
        return new Config(CONFIG_PATHNAME);
    }

    @Bean
    public PromiseBroker promiseBroker() {
        return new PromiseBroker();
    }

    @Bean
    @Scope("prototype")
    public MqttDecoder mqttDecoder(Config config) {
        int maxBytesInMessage = Integer.parseInt(config.getProperty("maxBytesInMessage", "8092"));
        return new MqttDecoder(maxBytesInMessage);
    }

    @Bean
    public MqttEncoder mqttEncoder() {
        return MqttEncoder.INSTANCE;
    }

    @Bean
    @Scope("prototype")
    public IdleStateHandler idleStateHandler(Config config) {
        int keepAliveTimer = Integer.parseInt(config.getProperty("keepAliveTimer", "20"));
        return new IdleStateHandler(0, keepAliveTimer, 0, TimeUnit.SECONDS);
    }

    @Bean
    @Scope("prototype")
    public ChannelHandler mqttPingHandler(Config config, ThreadPoolTaskScheduler threadPoolTaskScheduler) {
        return new MqttPingScheduleHandler(config, threadPoolTaskScheduler);   //MqttPingHandler(config);
    }

    @Bean
    @Scope("prototype")
    public MqttPublishHandler mqttPublishHandler(PromiseBroker promiseBroker, @Lazy MessageHandler messageHandler, Config config) {
        return new MqttPublishHandler(promiseBroker, messageHandler, config);
    }

    @Bean
    @Scope("prototype")
    public MqttConnectHandler mqttConnectHandler(PromiseBroker promiseBroker, Config config) {
        return new MqttConnectHandler(promiseBroker, config);
    }

    @Bean
    @Scope("prototype")
    public MqttSubscriptionHandler mqttSubscriptionHandler(PromiseBroker promiseBroker, Config config) {
        return new MqttSubscriptionHandler(promiseBroker, config);
    }

    @Bean
    @Scope("prototype")
    public MqttExceptionHandler exceptionHandler() {
        return new MqttExceptionHandler();
    }

    @Bean
    @Scope("prototype")
    public MqttChannelInitializer mqttChannelInitializer(
            MqttDecoder mqttDecoder,
            MqttEncoder mqttEncoder,
            ChannelHandler idleStateHandler,
            ChannelHandler mqttPingHandler,
            ChannelHandler mqttConnectHandler,
            ChannelHandler mqttSubscriptionHandler,
            ChannelHandler mqttPublishHandler,
            ChannelHandler exceptionHandler
    ) {
        return new MqttChannelInitializer(mqttDecoder, mqttEncoder, idleStateHandler, mqttPingHandler, mqttConnectHandler, mqttSubscriptionHandler, mqttPublishHandler, exceptionHandler);
    }

    @Bean
    public MqttClientImpl mqttClientImpl(Config config, PromiseBroker promiseBroker) {
        return new MqttClientImpl(config, promiseBroker);
    }

    @Bean
    public CommandService commandService(Config config) {
        return new CommandService(config);
    }

    @Bean
    public MessageHandler messageHandler(Config config) {
        return new MessageHandler(config);
    }

    @Bean
    public MessageDispatcher messageDispatcher(Config config, MqttClientImpl mqttClientImpl, CommandService commandService, MessageHandler messageHandler) {
        return new MessageDispatcher(config, mqttClientImpl, commandService, messageHandler);
    }

    /**
     * For creating Asynchronous Events
     *
     * @return
     */
    @Bean(name = "applicationEventMulticaster")
    public ApplicationEventMulticaster simpleApplicationEventMulticaster() {
        SimpleApplicationEventMulticaster eventMulticaster
                = new SimpleApplicationEventMulticaster();

        eventMulticaster.setTaskExecutor(new SimpleAsyncTaskExecutor());
        return eventMulticaster;
    }
}
