/*
 * The MIT License
 *
 * Copyright 2022 Maxim Eltratov <<Maxim.Eltratov@ya.ru>>.
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

import java.util.concurrent.ScheduledFuture;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;
import ru.maxeltr.mqttClient.Config.Config;

/**
 *
 * @author Maxim Eltratov <<Maxim.Eltratov@ya.ru>>
 */
public class SensorManager {

    private static final Logger logger = Logger.getLogger(SensorManager.class.getName());

    private CommandService commandService;

    private Config config;

    private ThreadPoolTaskScheduler taskScheduler;

    private PeriodicTrigger periodicTrigger;
    
    private ScheduledFuture<?> future;

    public SensorManager(Config config, ThreadPoolTaskScheduler taskScheduler, PeriodicTrigger periodicTrigger, CommandService commandService) {
        this.config = config;
        this.taskScheduler = taskScheduler;
        this.periodicTrigger = periodicTrigger;
        this.commandService = commandService;
    }

    @PostConstruct
    public void scheduleRunnableWithCronTrigger() {
        this.future = taskScheduler.schedule(new RunnableTask(), periodicTrigger);
    }

    class RunnableTask implements Runnable {

        @Override
        public void run() {

        }
    }
}
