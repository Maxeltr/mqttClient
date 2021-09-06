package ru.maxeltr.mqttClient.Service;

import com.google.gson.JsonObject;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.util.HtmlUtils;
import ru.maxeltr.mqttClient.Config.Config;

@Controller
public class DisplayController {

    private static final Logger logger = Logger.getLogger(DisplayController.class.getName());

    @Autowired
    private Config config;

    @Autowired
    private SimpMessagingTemplate simpMessagingTemplate;

    private MessageDispatcher messageDispatcher;

    public void setMessageDispatcher(MessageDispatcher messageDispatcher) {
        this.messageDispatcher = messageDispatcher;
    }

    //@SendTo("/topic/screenshots")
    public void display(Reply reply) {
        simpMessagingTemplate.convertAndSend("/topic/replies", reply, Map.of("card", config.getProperty(reply.getName() + ".Display", "")));
        logger.log(Level.INFO, String.format("Reply %s was displayed. id=%s, timestamp=%s, result=%s.", reply.getName(), reply.getCommandId(), reply.getTimestamp(), reply.getResult()));
        System.out.println(String.format("Reply %s was displayed. id=%s, timestamp=%s, result=%s.", reply.getName(), reply.getCommandId(), reply.getTimestamp(), reply.getResult()));
    }

    public void display(String topic, JsonObject data) {
        simpMessagingTemplate.convertAndSend("/topic/data", data, Map.of("card", config.getProperty(topic + ".Display", "")));
        logger.log(Level.INFO, String.format("Message was displayed. %s", data));
        System.out.println(String.format("Message was displayed. %s", data));
    }
}
