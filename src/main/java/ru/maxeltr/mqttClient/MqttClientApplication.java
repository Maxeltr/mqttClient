package ru.maxeltr.mqttClient;

import io.netty.util.concurrent.Promise;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import ru.maxeltr.mqttClient.Config.AppAnnotationConfig;

@SpringBootApplication
public class MqttClientApplication {

    private static final Logger logger = Logger.getLogger(MqttClientApplication.class.getName());

    public static void main(String[] args) throws InterruptedException {
        logger.log(Level.INFO, String.format("Start."));

        ConfigurableApplicationContext applicationContext = SpringApplication.run(MqttClientApplication.class, args);
        MqttClientImpl mqttClientImpl = (MqttClientImpl) applicationContext.getBean("mqttClientImpl");
        Future connectResult = mqttClientImpl.connect("176.113.82.112", 1883);
        Thread.sleep(3000);

        if(connectResult.isDone()) logger.log(Level.INFO, String.format("Connected."));


        MqttChannelHandler mqttChannelHandler = (MqttChannelHandler) applicationContext.getBean("mqttChannelHandler");


//        mqttClientImpl.shutdown();

        logger.log(Level.INFO, String.format("End.%n"));
        System.out.println(String.format("End.%n"));
    }

}
