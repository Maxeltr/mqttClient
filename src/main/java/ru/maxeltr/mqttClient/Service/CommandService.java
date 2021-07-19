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
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.mqtt.MqttQoS;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.scheduling.annotation.Async;
import ru.maxeltr.mqttClient.Config.Config;
import ru.maxeltr.mqttClient.Mqtt.MqttClientImpl;

/**
 * Executes processes on the host it is running on. processes runs by command
 * receives in the message payload.
 */
public class CommandService {

    private static final Logger logger = Logger.getLogger(CommandService.class.getName());

    private MqttClientImpl mqttClient;

    private Config config;

    private List<String> allowedCommands;

    private String commandRepliesTopic;

    private final Map<String, Command> pendingReplyCommands = Collections.synchronizedMap(new LinkedHashMap());

    public CommandService(MqttClientImpl mqttClient, Config config) {
        this.mqttClient = mqttClient;
        this.config = config;
        this.allowedCommands = Arrays.asList(this.config.getProperty("allowedCommands", "").split("\\s*,\\s*"));

        this.commandRepliesTopic = config.getProperty("receivingCommandRepliesTopic", "");
        if (this.commandRepliesTopic.trim().isEmpty()) {
            throw new IllegalStateException("Invalid receivingCommandRepliesTopic property");
        }
    }

    @Async
    public void execute(Command command) {
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        String replyTopic = command.getReplyTo();

        HashMap<String, String> isValid = this.validate(command);
        if (!Boolean.parseBoolean(isValid.get("isValid"))) {
            this.sendResponse(replyTopic, new Reply(command.getId(), command.getName(), timestamp, isValid.get("message"), "fail"));
            return;
        }

        String result = this.launch(command, command.getArguments() != null ? command.getArguments() : "");	//add
        if (result.isEmpty()) {
            logger.log(Level.INFO, String.format("Error with executing command %s. Empty result was returned. Arguments %s", command.getName(), command.getArguments()));
            System.out.println(String.format("Error with executing command %s. Empty result was returned. Arguments %s", command.getName(), command.getArguments()));	//add
            this.sendResponse(replyTopic, new Reply(command.getId(), command.getName(), timestamp, "Error with executing command", "fail"));
            return;
        }

        this.sendResponse(replyTopic, new Reply(command.getId(), command.getName(), timestamp, result, "ok"));
    }

    public void sendResponse(String topic, Reply reply) {
        Gson gson = new Gson();
        try {
            String jsonCommand = gson.toJson(reply);
            this.mqttClient.publish(topic, Unpooled.wrappedBuffer(jsonCommand.getBytes()), MqttQoS.AT_MOST_ONCE, false);
        } catch (JsonIOException ex) {
            logger.log(Level.SEVERE, "JsonIOException was thrown. Reply was not sent.", ex);
            System.out.println(String.format("JsonIOException was thrown. Reply was not sent."));
        }
    }

    @Async
    public void sendCommand(Command command, String topic) {
        command.setReplyTo(this.commandRepliesTopic);
        long timestamp = Instant.now().toEpochMilli();
        command.setTimestamp(String.valueOf(timestamp));

//        this.pendingReplyCommands.put(command.getId(), Consumer<String> callback);

        Gson gson = new Gson();
        try {
            String jsonCommand = gson.toJson(command);
            this.mqttClient.publish(topic, Unpooled.wrappedBuffer(jsonCommand.getBytes()), MqttQoS.AT_MOST_ONCE, false);
        } catch (JsonIOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Async
    public void handleReply(Reply reply) {

        File file = new File("c:\\java\\mqttClient\\test.jpg");
        try ( FileOutputStream fileOutputStream = new FileOutputStream(file)) {
            fileOutputStream.write(Base64.getDecoder().decode(reply.getPayload()));
        } catch (IOException ex) {
            Logger.getLogger(MessageHandler.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private String launch(Command command, String arguments) {
        logger.log(Level.INFO, String.format("CommandService. Start execute command %s.", command.getName()));

        String result = "";
        String commandPath = config.getProperty(command.getName() + "CommandPath", "");
        if (commandPath.trim().isEmpty()) {
            logger.log(Level.WARNING, String.format("%s command path is empty", command.getName()));
            System.out.println(String.format("%s command path is empty", command.getName()));
            return result;
        }

        String line;
        ProcessBuilder pb = new ProcessBuilder(commandPath, arguments);
        pb.redirectErrorStream(true);
        Process p;
        try {
            p = pb.start();
        } catch (IOException ex) {
            System.out.println(String.format("Exception %s.", ex.getMessage()));
            Logger.getLogger(CommandService.class.getName()).log(Level.SEVERE, null, ex);
            return result;
        }

        try ( BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            while (true) {
                line = br.readLine();
                if (line == null) {
                    break;
                }
                result += line;// + "\n";
            }
        } catch (IOException ex) {
            System.out.println(String.format("Exception %s.", ex.getMessage()));
            logger.log(Level.SEVERE, null, ex);
            return "";
        }

        logger.log(Level.INFO, String.format("CommandService. End execute command %s.", command.getName()));

        return result;

    }

    private HashMap<String, String> validate(Command command) {
        HashMap<String, String> result = new HashMap<>();

        if (!this.allowedCommands.contains(command.getName())) {
            logger.log(Level.INFO, String.format("Command not allowed %s.", command.getName()));
            System.out.println(String.format("Command not allowed %s.", command.getName()));
            result.put("isValid", "false");
            result.put("message", String.format("Command not allowed %s.", command.getName()));
            return result;
        }

        if (command.getId() == null || command.getId().trim().isEmpty()) {
            logger.log(Level.INFO, String.format("Command %s has empty id", command.getName()));
            System.out.println(String.format("Command %s has empty id", command.getName()));
            result.put("isValid", "false");
            result.put("message", String.format("Command %s has empty id", command.getName()));
            return result;
        }

        if (command.getReplyTo() == null || command.getReplyTo().trim().isEmpty()) {
            logger.log(Level.INFO, String.format("Command %s has empty replyTo", command.getName()));
            System.out.println(String.format("Command %s has empty replyTo", command.getName()));
            result.put("isValid", "false");
            result.put("message", String.format("Command %s has empty replyTo", command.getName()));
            return result;
        }

        if (command.getName() == null || command.getName().trim().isEmpty()) {
            logger.log(Level.INFO, String.format("Command %s has empty name", command.getName()));
            System.out.println(String.format("Command %s has empty name", command.getName()));
            result.put("isValid", "false");
            result.put("message", String.format("Command %s has empty name", command.getName()));
            return result;
        }

        result.put("isValid", "true");
        result.put("message", String.format("Command %s is valid", command.getName()));
        return result;
    }
}
