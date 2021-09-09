package ru.maxeltr.mqttClient;

import ru.maxeltr.mqttClient.Mqtt.MqttClientImpl;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import io.netty.util.concurrent.Promise;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import ru.maxeltr.mqttClient.Config.Config;

@SpringBootApplication
public class MqttClientApplication {

    private static final Logger logger = Logger.getLogger(MqttClientApplication.class.getName());

    public static void main(String[] args) throws InterruptedException, ExecutionException {

        ConfigurableApplicationContext applicationContext = SpringApplication.run(MqttClientApplication.class, args);
//        MqttClientImpl mqttClientImpl = (MqttClientImpl) applicationContext.getBean("mqttClientImpl");
//        Config config = (Config) applicationContext.getBean("config");
//        Promise<MqttConnAckMessage> connectResult = mqttClientImpl.connect("test.mosquitto.org", 1883);
//        Promise<MqttConnAckMessage> connectResult = mqttClientImpl.connect(config.getProperty("host", ""), 1883);

//        System.out.println("main " + connectResult.get().variableHeader().connectReturnCode());


//        String commandTopic = config.getProperty("receivingCommandsTopic", "");
//        if (commandTopic.trim().isEmpty()) {
//            throw new IllegalStateException("Invalid receivingCommandsTopic property");
//        }
//
//        String commandRepliesTopic = config.getProperty("receivingCommandRepliesTopic", "");
//        if (commandRepliesTopic.trim().isEmpty()) {
//            throw new IllegalStateException("Invalid receivingCommandRepliesTopic property");
//        }
//
//        String commandQos = config.getProperty("commandQos", "");
//        if (commandQos.trim().isEmpty()) {
//            throw new IllegalStateException("Invalid commandQos property");
//        }


//        StringBuilder cmdTopic = new StringBuilder();
//        cmdTopic.append(location);
//        cmdTopic.append("/");
//        cmdTopic.append(clientId);
//        cmdTopic.append("/");
//        cmdTopic.append("cmd");
//        cmdTopic.append("/");
//        cmdTopic.append("req");
//        Map<String, MqttQoS> m = new HashMap();
//        m.put("#", MqttQoS.AT_MOST_ONCE.value());
//        m.put("test", MqttQoS.AT_LEAST_ONCE);
//        m.put(cmdTopic.toString(), MqttQoS.AT_MOST_ONCE);
//        m.put("hm/dsktpClient/cmd/resp", MqttQoS.AT_MOST_ONCE);
//        m.put("test/qw", MqttQoS.AT_MOST_ONCE.value());
//        m.put("$SYS/broker/clients/connected", MqttQoS.AT_MOST_ONCE.value());
//        Promise<MqttSubAckMessage> subResult = mqttClientImpl.subscribe(m);
//        Map<String, MqttQoS> subTopics = new HashMap();             //TODO move to client method connectAndsubscribe?
//
//        List<String> subQos0Topics = Arrays.asList(config.getProperty("subQos0Topics", "").split("\\s*,\\s*"));
//        for (String topic : subQos0Topics) {
//            if (!topic.trim().isEmpty()) {
//                subTopics.put(topic, MqttQoS.AT_MOST_ONCE);
//            }
//        }
//
//        List<String> subQos1Topics = Arrays.asList(config.getProperty("subQos1Topics", "").split("\\s*,\\s*"));
//        for (String topic : subQos1Topics) {
//            if (!topic.trim().isEmpty()) {
//                subTopics.put(topic, MqttQoS.AT_LEAST_ONCE);
//            }
//        }
//
//        List<String> subQos2Topics = Arrays.asList(config.getProperty("subQos2Topics", "").split("\\s*,\\s*"));
//        for (String topic : subQos2Topics) {
//            if (!topic.trim().isEmpty()) {
//                subTopics.put(topic, MqttQoS.EXACTLY_ONCE);
//            }
//        }
//
//        subTopics.put(commandTopic, MqttQoS.valueOf(commandQos));
//        subTopics.put(commandRepliesTopic, MqttQoS.valueOf(commandQos));
//
//        Promise<MqttSubAckMessage> subResult = mqttClientImpl.subscribe(subTopics);

//        Promise<MqttSubAckMessage> subResult = mqttClientImpl.subscribeFromConfig();

//
//        MqttSubAckMessage res = subResult.get();
//        System.out.println(String.format("main " + "MqttSubscriptionResult %s.%n", res.variableHeader().messageId()));
//        Thread.sleep(7000);
//        String com = "{" + "\"id\"" + ":4," + "\"name\"" + ":" + "\"takeScreenshot\"" + "}";
//        String com = "{" + "\"id\"" + ":4," + "\"name\"" + ":" + "\"takeScreenshot\"," + "\"replyTo\"" + ":" + "\"hm/dsktpClient/cmd/replies\"" + "}";
//        mqttClientImpl.publish("hm/dsktpClient/cmd", Unpooled.wrappedBuffer(com.getBytes(Charset.forName("UTF-8"))), MqttQoS.EXACTLY_ONCE, false);
//        Thread.sleep(2000);
//        for( IntObjectMap.PrimitiveEntry<MqttSubscribeMessage> v: mqttClientImpl.waitingSubscriptions.entries()) {
//            System.out.println(String.format("method main. waitingSubscriptions. key %s value %s", v.key(), v.value()));
//        }
//        Iterator it = mqttClientImpl.activeTopics.entrySet().iterator();
//        while (it.hasNext()) {
//            Map.Entry pair = (Map.Entry) it.next();
//            System.out.println("activeSubscriptions " + pair.getKey() + " = " + pair.getValue());
//            it.remove(); // avoids a ConcurrentModificationException
//        }

//        Thread.sleep(5000);
//        mqttClientImpl.reconnect();

//        mqttClientImpl.shutdown();
//        System.out.println(String.format("End.%n"));
    }


}
