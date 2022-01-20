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

import io.netty.util.internal.StringUtil;
import java.util.logging.Logger;

/**
 *
 * @author Maxim Eltratov <<Maxim.Eltratov@ya.ru>>
 */
public class CommandBuilder {

    private static final Logger logger = Logger.getLogger(CommandBuilder.class.getName());

    private String commandNumber;

    private String id;

    private String name;

    private String replyTo;

    private String arguments;

    private String timestamp;

    public String getCommandNumber() {
        return commandNumber;
    }

    public CommandBuilder setCommandNumber(String commandNumber) {
        this.commandNumber = commandNumber;
        return this;
    }

    public String getId() {
        return id;
    }

    public CommandBuilder setId(String id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public CommandBuilder setName(String name) {
        this.name = name;
        return this;
    }

    public String getReplyTo() {
        return replyTo;
    }

    public CommandBuilder setReplyTo(String replyTo) {
        this.replyTo = replyTo;
        return this;
    }

    public String getArguments() {
        return arguments;
    }

    public CommandBuilder setArguments(String arguments) {
        this.arguments = arguments;
        return this;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public CommandBuilder setTimestamp(String timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public Command build() {
        if (this.id == null || this.id.trim().isEmpty()) {
            throw new IllegalStateException("Invalid id property");
        }
        if (this.name == null || this.name.trim().isEmpty()) {
            throw new IllegalStateException("Invalid name property");
        }
        if (this.replyTo == null || this.replyTo.trim().isEmpty()) {
            throw new IllegalStateException("Invalid replyTo property");
        }
        if (this.timestamp == null || this.timestamp.trim().isEmpty()) {
            throw new IllegalStateException("Invalid timestamp property");
        }

        return new Command(this.id, this.name, this.replyTo, this.arguments, this.timestamp);
    }

    @Override
    public String toString() {
        return new StringBuilder(StringUtil.simpleClassName(this))
                .append('[')
                .append(String.format("commandNumber=%s, id=%s, name=%s, replyTo=%s, timestamp=%s, arguments=%s", this.commandNumber, this.id, this.name, this.replyTo, this.timestamp, this.arguments))
                .append(']')
                .toString();
    }
}
