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

    public CommandService(MqttClientImpl mqttClient, Config config) {
        this.mqttClient = mqttClient;
        this.config = config;
    }

    @Async
    public void execute(Command command) {
        List<String> allowedCommands = Arrays.asList(this.config.getProperty("allowedCommands", "").split("\\s*,\\s*"));
        if (!allowedCommands.contains(command.getName())) {
            logger.log(Level.INFO, String.format("Command not allowed %s.", command.getName()));
            command.setStatus("response");
            command.setResult("fail");
            command.setValue("Command is not allowed");
            this.response(command);
            return;
        }
        command.setPayload(this.launch(command, ""));
        this.response(command);

    }

    @Async
    public void response(Command command) {
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

        command.setStatus("response");

        Gson gson = new Gson();
        try {
            String jsonCommand = gson.toJson(command);
            this.mqttClient.publish(cmdTopic.toString(), Unpooled.wrappedBuffer(jsonCommand.getBytes()), MqttQoS.AT_MOST_ONCE, false);
        } catch (JsonIOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Async
    public String launch(Command command, String arguments) {
        logger.log(Level.INFO, String.format("CommandService. Start execute command %s.", command.getName()));
        String location = config.getProperty("commandFolder", "");
        if (location.trim().isEmpty()) {
            logger.log(Level.WARNING, String.format("Invalid commandFolder property"));
            System.out.println(String.format("Invalid commandFolder property"));
            return "Invalid commandFolder property";
        }

        String line;
        String result = "";
        ProcessBuilder pb;
        try {
            pb = new ProcessBuilder(location + "\\takeScreenshot\\build\\distributions\\takeScreenshot\\bin\\takeScreenshot.bat", arguments);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
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
        }

        System.out.println(String.format(result));
        logger.log(Level.INFO, String.format("CommandService. End execute."));

        return result;

    }
}
