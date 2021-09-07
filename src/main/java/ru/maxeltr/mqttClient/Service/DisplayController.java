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
        String card = config.getProperty(reply.getName() + ".Display", "");
        if (card.trim().isEmpty()) {
            logger.log(Level.WARNING, String.format("Invalid %s.Display property.", reply.getName()));
            System.out.println(String.format("Invalid %s.Display property.", reply.getName()));
            return;
        }

        simpMessagingTemplate.convertAndSend("/topic/replies", reply, Map.of("card", card));

        logger.log(Level.INFO, String.format("Reply %s was sent to display %s. id=%s, timestamp=%s, result=%s.", reply.getName(), card, reply.getCommandId(), reply.getTimestamp(), reply.getResult()));
        System.out.println(String.format("Reply %s was sent to display %s. id=%s, timestamp=%s, result=%s.", reply.getName(), card, reply.getCommandId(), reply.getTimestamp(), reply.getResult()));
    }

    public void display(String topic, JsonObject data) {
        String card = config.getProperty(topic + ".Display", "");
        if (card.trim().isEmpty()) {
            logger.log(Level.WARNING, String.format("Invalid %s.Display property.", topic));
            System.out.println(String.format("Invalid %s.Display property.", topic));
            return;
        }

        simpMessagingTemplate.convertAndSend("/topic/data", data, Map.of("card", card));

        logger.log(Level.INFO, String.format("Message was sent to display %s. %s", card, data));
        System.out.println(String.format("Message was sent to display %s. %s", card, data));
    }
}
