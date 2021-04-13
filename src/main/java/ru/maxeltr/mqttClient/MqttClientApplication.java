package ru.maxeltr.mqttClient;

import ru.maxeltr.mqttClient.Mqtt.MqttClientImpl;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import io.netty.util.concurrent.Promise;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class MqttClientApplication {

    private static final Logger logger = Logger.getLogger(MqttClientApplication.class.getName());

    public static void main(String[] args) throws InterruptedException, ExecutionException {

        ConfigurableApplicationContext applicationContext = SpringApplication.run(MqttClientApplication.class, args);
        MqttClientImpl mqttClientImpl = (MqttClientImpl) applicationContext.getBean("mqttClientImpl");
//        Promise<MqttConnAckMessage> connectResult = mqttClientImpl.connect("test.mosquitto.org", 1883);
        Promise<MqttConnAckMessage> connectResult = mqttClientImpl.connect("176.113.82.112", 1883);

        System.out.println("main " + connectResult.get().variableHeader().connectReturnCode());

        Map<String, MqttQoS> m = new HashMap();
//        m.put("#", MqttQoS.AT_MOST_ONCE.value());
        m.put("test", MqttQoS.EXACTLY_ONCE);
//        m.put("test/qw", MqttQoS.AT_MOST_ONCE.value());
//        m.put("$SYS/broker/clients/connected", MqttQoS.AT_MOST_ONCE.value());
        Promise<MqttSubAckMessage> subResult = mqttClientImpl.subscribe(m);
//
//        MqttSubAckMessage res = subResult.get();
//        System.out.println(String.format("main " + "MqttSubscriptionResult %s.%n", res.variableHeader().messageId()));

        Thread.sleep(2000);
        mqttClientImpl.publish("test", Unpooled.wrappedBuffer("test11april".getBytes()), MqttQoS.EXACTLY_ONCE, false);
        Thread.sleep(2000);
//        for( IntObjectMap.PrimitiveEntry<MqttSubscribeMessage> v: mqttClientImpl.waitingSubscriptions.entries()) {
//            System.out.println(String.format("method main. waitingSubscriptions. key %s value %s", v.key(), v.value()));
//        }
//        Iterator it = mqttClientImpl.activeTopics.entrySet().iterator();
//        while (it.hasNext()) {
//            Map.Entry pair = (Map.Entry) it.next();
//            System.out.println("activeSubscriptions " + pair.getKey() + " = " + pair.getValue());
//            it.remove(); // avoids a ConcurrentModificationException
//        }

//        mqttClientImpl.shutdown();
        System.out.println(String.format("End.%n"));
    }

}
