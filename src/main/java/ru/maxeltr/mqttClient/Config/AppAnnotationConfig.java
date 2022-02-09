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

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.logging.LogManager;
import org.apache.tika.Tika;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;
import ru.maxeltr.mqttClient.Service.MessageDispatcher;
import ru.maxeltr.mqttClient.Mqtt.MqttChannelInitializer;
import ru.maxeltr.mqttClient.Mqtt.MqttClientImpl;
import ru.maxeltr.mqttClient.Mqtt.PromiseBroker;
import ru.maxeltr.mqttClient.Service.CommandService;
import ru.maxeltr.mqttClient.Service.DisplayController;
import ru.maxeltr.mqttClient.Service.MessageHandler;
import ru.maxeltr.mqttClient.Service.SensorManager;

/**
 *
 * @author Maxim Eltratov <<Maxim.Eltratov@ya.ru>>
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AppAnnotationConfig {

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
     * For scheduling tasks
     */
    @Bean
    public ThreadPoolTaskScheduler threadPoolTaskScheduler() {
        ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
        threadPoolTaskScheduler.setPoolSize(10);
        threadPoolTaskScheduler.setThreadNamePrefix("pingThreadPoolTaskScheduler");
        return threadPoolTaskScheduler;
    }

    /*
     * For scheduling measurements
     */
    @Bean(name = "measurementPeriodicTrigger")
    public PeriodicTrigger measurementPeriodicTrigger(Config config) {
        String measurementPeriod = config.getProperty("measurementPeriodicTrigger", "10");
        if (measurementPeriod.trim().isEmpty()) {
            throw new IllegalStateException("Invalid measurementPeriodicTrigger property");
        }
        PeriodicTrigger periodicTrigger = new PeriodicTrigger(Long.parseLong(measurementPeriod, 10), TimeUnit.SECONDS);
        periodicTrigger.setInitialDelay(Long.parseLong(measurementPeriod, 10));
        return periodicTrigger;
    }

    /*
     * For scheduling a ping request with a fixed delay which is defined by config (MqttPingScheduleHandler)
     */
    @Bean
    public PeriodicTrigger pingPeriodicTrigger(Config config) {
        String keepAliveTimer = config.getProperty("keepAliveTimer", "20");
        if (keepAliveTimer.trim().isEmpty()) {
            throw new IllegalStateException("Invalid keepAliveTimer property");
        }
        PeriodicTrigger periodicTrigger = new PeriodicTrigger(Long.parseLong(keepAliveTimer, 10), TimeUnit.SECONDS);
        periodicTrigger.setInitialDelay(Long.parseLong(keepAliveTimer, 10));
        return periodicTrigger;
    }

    /*
     * For scheduling retransmit MqttMessages with a fixed delay which is defined by config
     */
    @Bean(name = "retransmitPeriodicTrigger")
    public PeriodicTrigger retransmitPeriodicTrigger(Config config) {
        String retransmitMqttMessageTimer = config.getProperty("retransmitMqttMessageTimer", "40");
        if (retransmitMqttMessageTimer.trim().isEmpty()) {
            throw new IllegalStateException("Invalid retransmitMqttMessageTimer property");
        }
        PeriodicTrigger periodicTrigger = new PeriodicTrigger(Long.parseLong(retransmitMqttMessageTimer, 10), TimeUnit.SECONDS);
        periodicTrigger.setInitialDelay(Long.parseLong(retransmitMqttMessageTimer, 10));
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
    public MqttChannelInitializer mqttChannelInitializer(
            Config config,
            PromiseBroker promiseBroker,
            MessageHandler messageHandler,
            ThreadPoolTaskScheduler threadPoolTaskScheduler,
            PeriodicTrigger retransmitPeriodicTrigger,
            ApplicationEventPublisher applicationEventPublisher,
            PeriodicTrigger pingPeriodicTrigger
    ) {
        return new MqttChannelInitializer(
                config,
                promiseBroker,
                messageHandler,
                threadPoolTaskScheduler,
                retransmitPeriodicTrigger,
                applicationEventPublisher,
                pingPeriodicTrigger
        );
    }

    @Bean
    public MqttClientImpl mqttClientImpl(
            Config config,
            PromiseBroker promiseBroker,
            ThreadPoolTaskScheduler threadPoolTaskScheduler,
            PeriodicTrigger retransmitPeriodicTrigger,
            MqttChannelInitializer mqttChannelInitializer,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        return new MqttClientImpl(
                config,
                promiseBroker,
                threadPoolTaskScheduler,
                retransmitPeriodicTrigger,
                mqttChannelInitializer,
                applicationEventPublisher
        );
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
    public MessageDispatcher messageDispatcher(Config config, MqttClientImpl mqttClientImpl, CommandService commandService, MessageHandler messageHandler, DisplayController displayController) {
        return new MessageDispatcher(config, mqttClientImpl, commandService, messageHandler, displayController);
    }

    @Bean
    public Tika tika() {
        return new Tika();
    }

    @Bean
    public SensorManager sensorManager(Config config, ThreadPoolTaskScheduler taskScheduler, PeriodicTrigger measurementPeriodicTrigger, CommandService commandService) {
        return new SensorManager(config, taskScheduler, measurementPeriodicTrigger, commandService);
    }

//    @Bean
//    @Controller
//    public RemoteCommandController remoteCommandController(Config config, CommandService commandService) {
//        return new RemoteCommandController(config, commandService);
//    }
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
