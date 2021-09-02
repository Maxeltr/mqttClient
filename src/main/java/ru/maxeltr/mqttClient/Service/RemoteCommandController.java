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

import java.time.Instant;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.util.HtmlUtils;
import ru.maxeltr.mqttClient.Config.Config;

/**
 *
 * @author Maxim Eltratov <<Maxim.Eltratov@ya.ru>>
 */
@Controller
public class RemoteCommandController {

    private static final Logger logger = Logger.getLogger(RemoteCommandController.class.getName());

    private final CommandService commandService;

    private final Config config;

    @Autowired
    private SimpMessagingTemplate simpMessagingTemplate;

    public RemoteCommandController(Config config, CommandService commandService) {
        this.config = config;
        this.commandService = commandService;
    }

    @MessageMapping("/createCommand")
    public void createCommand(CommandBuilder command) {
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        String numberCommand = command.getCommandNumber();

        command.setId(UUID.randomUUID().toString())
                .setName(config.getProperty(numberCommand + ".Name", ""))
                .setReplyTo(config.getProperty("receivingCommandRepliesTopic", ""))
                .setTarget(config.getProperty(numberCommand + ".Target", ""))
                .setArguments(config.getProperty(numberCommand + ".Arguments", ""))
                .setTimestamp(timestamp);

        logger.log(Level.INFO, String.format("CommandBuilder was created. %s", command));
        System.out.println(String.format("CommandBuilder was created. %s", command));

        this.commandService.send(
                config.getProperty(numberCommand + ".SendTo", ""),
                command.build()
        );
    }
}
