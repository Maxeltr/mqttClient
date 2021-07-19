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
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.scheduling.annotation.Async;
import ru.maxeltr.mqttClient.Config.Config;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;

/**
 *
 * @author Maxim Eltratov <<Maxim.Eltratov@ya.ru>>
 */
public class MessageHandler {

    private static final Logger logger = Logger.getLogger(MessageHandler.class.getName());

    Config config;

    CommandService commandService;

    String commandTopic;

    String commandRepliesTopic;

    public MessageHandler(CommandService commandService, Config config) {
        this.config = config;
        this.commandService = commandService;

        this.commandTopic = config.getProperty("receivingCommandsTopic", "");
        if (this.commandTopic.trim().isEmpty()) {
            throw new IllegalStateException("Invalid receivingCommandsTopic property");
        }

        this.commandRepliesTopic = config.getProperty("receivingCommandRepliesTopic", "");
        if (this.commandRepliesTopic.trim().isEmpty()) {
            throw new IllegalStateException("Invalid receivingCommandRepliesTopic property");
        }
    }

    @Async
    public void handleMessage(MqttPublishMessage message) throws InterruptedException {
        logger.log(Level.INFO, String.format("MessageHandler. Start handle publish message id: %s.", message.variableHeader().packetId()));

        MqttPublishVariableHeader variableHeader = (MqttPublishVariableHeader) message.variableHeader();
        String topic = variableHeader.topicName();
        String payload = message.payload().toString(Charset.forName("UTF-8"));

        Command command;
        GsonBuilder gb = new GsonBuilder();
        Gson gson = gb.create();

        if (topic.equalsIgnoreCase(this.commandTopic)) {
            try {														//add
                command = gson.fromJson(payload, Command.class);
                this.commandService.execute(command);
            } catch (JsonSyntaxException ex) {
                logger.log(Level.SEVERE, "Malformed Json.", ex);
                System.out.println(String.format("Malformed Json."));
                return;
            }

        } else if (topic.equalsIgnoreCase(this.commandRepliesTopic)) {
            try {														//add
                Reply reply = gson.fromJson(payload, Reply.class);
                this.commandService.handleReply(reply);
            } catch (JsonSyntaxException ex) {
                logger.log(Level.SEVERE, "Malformed Json.", ex);
                System.out.println(String.format("Malformed Json."));
                return;
            }

        } else {
            System.out.println(payload);
        }

    }
}
