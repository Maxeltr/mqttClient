/*
 * The MIT License
 *
 * Copyright 2021 Maxim Eltratov <<Maxim.Eltratov@ya.ru>>.
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
package ru.maxeltr.mqttClient.Service;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.mqtt.MqttQoS;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;
import ru.maxeltr.mqttClient.Config.Config;
import ru.maxeltr.mqttClient.Mqtt.MqttClientImpl;

/**
 *
 * @author Maxim Eltratov <<Maxim.Eltratov@ya.ru>>
 */
public class MessageDispatcher {

    private static final Logger logger = Logger.getLogger(MessageDispatcher.class.getName());

    private Config config;

    private final MqttClientImpl mqttClient;

    private final CommandService commandService;

    private final MessageHandler messageHandler;

    private final DisplayController displayController;

    public MessageDispatcher(Config config, MqttClientImpl mqttClientImpl, CommandService commandService, MessageHandler messageHandler, DisplayController displayController) {
        this.config = config;
        this.mqttClient = mqttClientImpl;
        this.commandService = commandService;
        this.messageHandler = messageHandler;
        this.displayController = displayController;

        this.mqttClient.setMessageDispatcher(this);
        this.commandService.setMessageDispatcher(this);
        this.messageHandler.setMessageDispatcher(this);
        this.displayController.setMessageDispatcher(this);
    }

    public void send(String topic, String Qos, Reply reply) {
        if (topic.trim().isEmpty() || Qos.trim().isEmpty()) {
            logger.log(Level.WARNING, String.format("Topic or Qos is empty for reply %s.", reply.getName()));
            System.out.println(String.format("Topic or Qos is empty for reply %s.", reply.getName()));
            return;
        }

        Gson gson = new Gson();
        try {
            String jsonReply = gson.toJson(reply);
            this.mqttClient.publish(topic, Unpooled.wrappedBuffer(jsonReply.getBytes(Charset.forName("UTF-8"))), MqttQoS.valueOf(Qos), false);
        } catch (JsonIOException ex) {
            logger.log(Level.SEVERE, "JsonIOException was thrown. Reply was not sent.", ex);
            System.out.println(String.format("JsonIOException was thrown. Reply was not sent."));
            return;
        }
        logger.log(Level.INFO, String.format("Reply %s was sent to topic %s with Qos %s. id=%s, timestamp=%s, result=%s.",
                reply.getName(), topic, Qos, reply.getCommandId(), reply.getTimestamp(), reply.getResult()
        ));
        System.out.println(String.format("Reply %s was sent to topic %s with Qos %s. id=%s, timestamp=%s, result=%s.",
                reply.getName(), topic, Qos, reply.getCommandId(), reply.getTimestamp(), reply.getResult()
        ));
    }

    public void send(String topic, String Qos, Command command) {
        if (topic.trim().isEmpty() || Qos.trim().isEmpty()) {
            logger.log(Level.WARNING, String.format("Topic or Qos is empty for command %s.", command));
            System.out.println(String.format("Topic or Qos is empty for command %s.", command));
            return;
        }

        Gson gson = new Gson();
        try {
            String jsonCommand = gson.toJson(command);
            this.mqttClient.publish(topic, Unpooled.wrappedBuffer(jsonCommand.getBytes(Charset.forName("UTF-8"))), MqttQoS.valueOf(Qos), false);
        } catch (JsonIOException ex) {
            logger.log(Level.SEVERE, "JsonIOException was thrown. Command was not sent.", ex);
            System.out.println(String.format("JsonIOException was thrown. Command was not sent."));
            return;
        }
        logger.log(Level.INFO, String.format("Command %s was sent to topic %s with Qos %s. id=%s, target=%s, replyTo=%s, arguments=%s, timestamp=%s.",
                command.getName(), topic, Qos, command.getId(), command.getTarget(), command.getReplyTo(), command.getArguments(), command.getTimestamp()
        ));
        System.out.println(String.format("Command %s was sent to topic %s with Qos %s. id=%s, target=%s, replyTo=%s, arguments=%s, timestamp=%s.",
                command.getName(), topic, Qos, command.getId(), command.getTarget(), command.getReplyTo(), command.getArguments(), command.getTimestamp()
        ));
    }

    public void send(String topic, String Qos, JsonObject jsonObject, Boolean retain) {
        if (topic.trim().isEmpty() || Qos.trim().isEmpty()) {
            logger.log(Level.WARNING, String.format("Topic or Qos is empty for %s.", jsonObject));
            System.out.println(String.format("Topic or Qos is empty for %s.", jsonObject));
            return;
        }

        String json;
        Gson gson = new Gson();
        try {
            json = gson.toJson(jsonObject);
            this.mqttClient.publish(topic, Unpooled.wrappedBuffer(json.getBytes(Charset.forName("UTF-8"))), MqttQoS.valueOf(Qos), retain);
        } catch (JsonIOException ex) {
            logger.log(Level.SEVERE, "JsonIOException was thrown. Data was not sent.", ex);
            System.out.println(String.format("JsonIOException was thrown. Data was not sent."));
            return;
        }
        logger.log(Level.INFO, String.format("Data was sent to topic %s with Qos %s, retain=%s. Data=%s, length=%s.",
                topic, Qos, retain, json.substring(0, Math.min(json.length(), 64)), json.length()
        ));
        System.out.println(String.format("Data was sent to topic %s with Qos %s, retain=%s. Data=%s, length=%s.",
                topic, Qos, retain, json.substring(0, Math.min(json.length(), 64)), json.length()
        ));
    }

    public void handleReply(Reply reply) {
        this.commandService.handleReply(reply);
    }

    public void execute(Command command) {
        this.commandService.execute(command);
    }

    public void display(String topic, JsonObject data) {
        if (topic.trim().isEmpty()) {
            logger.log(Level.WARNING, String.format("Topic is empty for JsonObject %s.", data));
            System.out.println(String.format("Topic is empty for JsonObject %s.", data));
            return;
        }

        this.displayController.display(topic, data);
    }

    public void display(Reply reply) {
        this.displayController.display(reply);

    }
}
