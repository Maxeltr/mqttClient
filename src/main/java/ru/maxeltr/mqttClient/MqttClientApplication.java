package ru.maxeltr.mqttClient;

import io.netty.channel.ChannelFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class MqttClientApplication {

    private static final Logger logger = Logger.getLogger(MqttClientApplication.class.getName());

    public static void main(String[] args) throws InterruptedException {
        logger.log(Level.INFO, String.format("Start."));

        ConfigurableApplicationContext applicationContext = SpringApplication.run(MqttClientApplication.class, args);
        MqttClientImpl mqttClientImpl = (MqttClientImpl) applicationContext.getBean("mqttClientImpl");
        ChannelFuture connectResult = mqttClientImpl.connect("176.113.82.112", 1883);

        Thread.sleep(10000);
// connectResult = mqttClientImpl.connect("176.113.82.112", 1883);


//        mqttClientImpl.shutdown();


        System.out.println(String.format("End.%n"));
    }

}
