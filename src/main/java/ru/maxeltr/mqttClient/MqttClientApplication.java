package ru.maxeltr.mqttClient;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.util.collection.IntObjectMap;
import io.netty.util.concurrent.Promise;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class MqttClientApplication {

    private static final Logger logger = Logger.getLogger(MqttClientApplication.class.getName());

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        logger.log(Level.INFO, String.format("Start."));

        ConfigurableApplicationContext applicationContext = SpringApplication.run(MqttClientApplication.class, args);
        MqttClientImpl mqttClientImpl = (MqttClientImpl) applicationContext.getBean("mqttClientImpl");
        Promise<MqttConnectResult> connectResult = mqttClientImpl.connect("176.113.82.112", 1883);

        System.out.println(connectResult.get().getReturnCode());

        Promise<MqttSubscriptionResult> subResult = mqttClientImpl.subscribe("#", MqttQoS.AT_MOST_ONCE);

        MqttSubscriptionResult res = subResult.get();
        System.out.println(String.format("MqttSubscriptionResult %s.%n", res.getMessageId()));

        Thread.sleep(2000);
        for( IntObjectMap.PrimitiveEntry<MqttSubscribeMessage> v: mqttClientImpl.waitingSubscriptions.entries()) {
            System.out.println(String.format("method main. waitingSubscriptions. key %s value %s", v.key(), v.value()));
        }
        Iterator it = mqttClientImpl.activeSubscriptions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            System.out.println("activeSubscriptions " + pair.getKey() + " = " + pair.getValue());
            it.remove(); // avoids a ConcurrentModificationException
        }

//        mqttClientImpl.shutdown();
        System.out.println(String.format("End.%n"));
    }

}
