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
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import ru.maxeltr.mqttClient.Mqtt.MqttChannelInitializer;
import ru.maxeltr.mqttClient.Mqtt.MqttClientImpl;
import ru.maxeltr.mqttClient.Mqtt.MqttConnectHandler;
import ru.maxeltr.mqttClient.Mqtt.MqttPingHandler;
import ru.maxeltr.mqttClient.Mqtt.MqttPublishHandler;
import ru.maxeltr.mqttClient.Mqtt.MqttSubscriptionHandler;
import ru.maxeltr.mqttClient.Mqtt.PromiseBroker;
import ru.maxeltr.mqttClient.Service.MessageHandler;

/**
 *
 * @author Maxim Eltratov <<Maxim.Eltratov@ya.ru>>
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AppAnnotationConfig {

    public static final String CONFIG_PATHNAME = "Configuration.xml";

    public AppAnnotationConfig() throws IOException {
        try {
            LogManager.getLogManager().readConfiguration(AppAnnotationConfig.class.getResourceAsStream("/logging.properties")
            );
        } catch (IOException | SecurityException ex) {
            System.err.println("Could not setup logger configuration: " + ex.toString());
        }
    }

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

    @Bean
    public Config config() {
        return new Config(CONFIG_PATHNAME);
    }

    @Bean
    public PromiseBroker promiseBroker() {
        return new PromiseBroker();
    }

    @Bean
    public MessageHandler messageHandler() {
        return new MessageHandler();
    }

    @Bean
    public MqttDecoder mqttDecoder() {
        return new MqttDecoder();
    }

    @Bean
    public MqttEncoder mqttEncoder() {
        return MqttEncoder.INSTANCE;
    }

    @Bean
    public IdleStateHandler idleStateHandler() {
        return new IdleStateHandler(0, 20, 0, TimeUnit.SECONDS);
    }

    @Bean
    public MqttPingHandler mqttPingHandler(Config config) {
        return new MqttPingHandler(config);
    }

    @Bean
    public MqttPublishHandler mqttPublishHandler(PromiseBroker promiseBroker, MessageHandler messageHandler, Config config) {
        return new MqttPublishHandler(promiseBroker, messageHandler, config);
    }

    @Bean
    public MqttConnectHandler mqttConnectHandler(PromiseBroker promiseBroker, Config config) {
        return new MqttConnectHandler(promiseBroker, config);
    }

    @Bean
    public MqttSubscriptionHandler mqttSubscriptionHandler(PromiseBroker promiseBroker, Config config) {
        return new MqttSubscriptionHandler(promiseBroker, config);
    }

    @Bean
    public MqttChannelInitializer mqttChannelInitializer(
            MqttDecoder mqttDecoder,
            MqttEncoder mqttEncoder,
            ChannelHandler idleStateHandler,
            ChannelHandler mqttPingHandler,
            MqttConnectHandler mqttConnectHandler,
            MqttSubscriptionHandler mqttSubscriptionHandler,
            ChannelHandler mqttPublishHandler
    ) {
        return new MqttChannelInitializer(mqttDecoder, mqttEncoder, idleStateHandler, mqttPingHandler, mqttConnectHandler, mqttSubscriptionHandler, mqttPublishHandler);
    }

    @Bean
    public MqttClientImpl mqttClientImpl(MqttChannelInitializer mqttChannelInitializer, Config config, PromiseBroker promiseBroker) {
        return new MqttClientImpl(mqttChannelInitializer, config, promiseBroker);
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
