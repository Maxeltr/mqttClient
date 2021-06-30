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
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.scheduling.annotation.Async;
import ru.maxeltr.mqttClient.Config.Config;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

/**
 *
 * @author Maxim Eltratov <<Maxim.Eltratov@ya.ru>>
 */
public class MessageHandler {

    private static final Logger logger = Logger.getLogger(MessageHandler.class.getName());

    Config config;

    CommandService commandService;

    public MessageHandler(CommandService commandService, Config config) {
        this.config = config;
        this.commandService = commandService;
    }

    @Async
    public void handleMessage(MqttPublishMessage message) throws InterruptedException {
        logger.log(Level.INFO, String.format("MessageHandler. Start handle publish message id: %s.", message.variableHeader().packetId()));
        String location = config.getProperty("location", "");
        String clientId = config.getProperty("clientId", "");
        MqttPublishVariableHeader variableHeader = (MqttPublishVariableHeader) message.variableHeader();
        String topic = variableHeader.topicName();
        ArrayList<String> topicLevels = new ArrayList<>(Arrays.asList(topic.split("/")));

        if (!topicLevels.get(0).equalsIgnoreCase(location)) {
            logger.log(Level.WARNING, String.format("Topic level location \"%s\" is not match configuration \"%s\".", topicLevels.get(0), location));
            return;
        }

        if (!topicLevels.get(1).equalsIgnoreCase(clientId)) {
            logger.log(Level.WARNING, String.format("Topic level clientId \"%s\" is not match configuration \"%s\".", topicLevels.get(1), clientId));
            return;
        }

        if (topicLevels.get(2).equalsIgnoreCase("cmd")) {

            String payload = message.payload().toString(Charset.forName("UTF-8"));
            System.out.println(String.format(payload));
            GsonBuilder gb = new GsonBuilder();
            Gson gson = gb.create();
            try {
                Command command = gson.fromJson(payload, Command.class);
                this.commandService.execute(command);
            } catch (JsonSyntaxException ex) {
                logger.log(Level.SEVERE, null, ex);
            }


        }

    }
}
