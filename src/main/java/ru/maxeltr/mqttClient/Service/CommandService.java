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
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
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

    MqttClientImpl mqttClient;

    Config config;

    String cmdTopic;

    public CommandService(MqttClientImpl mqttClient, Config config) {
        this.mqttClient = mqttClient;
        this.config = config;

        String location = this.config.getProperty("location", "");
        String clientId = this.config.getProperty("clientId", "");
        StringBuilder cmdTopic = new StringBuilder();
        cmdTopic.append(location);
        cmdTopic.append("/");
        cmdTopic.append(clientId);
        cmdTopic.append("/");
        cmdTopic.append("cmd");
        cmdTopic.append("/");
        cmdTopic.append("resp");
        this.cmdTopic = cmdTopic.toString();
    }

    @Async
    public void execute(Command command) {
        List<String> allowedCommands = Arrays.asList(this.config.getProperty("allowedCommands", "").split("\\s*,\\s*"));
        if (!allowedCommands.contains(command.getName())) {
            logger.log(Level.INFO, String.format("Command not allowed %s.", command.getName()));
            command.setStatus("response");
            command.setResult("fail");
            command.setPayload("Command is not allowed");
            this.sendResponse(command);
            return;
        }
        command.setResult("ok");
        command.setValue("");
        command.setPayload(this.launch(command, ""));
        this.sendResponse(command);

    }

    @Async
    public void sendResponse(Command command) {
        command.setStatus("response");

        Gson gson = new Gson();
        try {
            String jsonCommand = gson.toJson(command);
            this.mqttClient.publish(this.cmdTopic, Unpooled.wrappedBuffer(jsonCommand.getBytes()), MqttQoS.AT_MOST_ONCE, false);
        } catch (JsonIOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    public String launch(Command command, String arguments) {
        logger.log(Level.INFO, String.format("CommandService. Start execute command %s.", command.getName()));

        String commandPath = config.getProperty(command.getName() + "CommandPath", "");
        if (commandPath.trim().isEmpty()) {
            logger.log(Level.WARNING, String.format("%s command path is empty", command.getName()));
            System.out.println(String.format("%s command path is empty", command.getName()));
            return command.getName() + " command path is empty";
        }

        String line;
        String result = "";
        ProcessBuilder pb = new ProcessBuilder(commandPath, arguments);
        pb.redirectErrorStream(true);
        Process p;
        try {
            p = pb.start();
        } catch (IOException ex) {
            System.out.println(String.format("Exception %s.", ex.getMessage()));
            Logger.getLogger(CommandService.class.getName()).log(Level.SEVERE, null, ex);
            return "An exception occured during execution of the command.";
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
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
            result = "An exception occured during execution of the command.";
        }

        logger.log(Level.INFO, String.format("CommandService. End execute command %s.", command.getName()));

        return result;

    }
}
