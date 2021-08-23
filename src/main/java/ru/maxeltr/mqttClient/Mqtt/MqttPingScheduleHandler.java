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
package ru.maxeltr.mqttClient.Mqtt;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttQoS;
import java.util.concurrent.ScheduledFuture;
//import io.netty.util.concurrent.ScheduledFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;
import ru.maxeltr.mqttClient.Config.Config;

/**
 *
 * @author Maxim Eltratov <<Maxim.Eltratov@ya.ru>>
 */
public class MqttPingScheduleHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = Logger.getLogger(MqttPingHandler.class.getName());

    private final Config config;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;

    @Autowired
    private PeriodicTrigger periodicTrigger;

    private boolean pingRequestWasSent;

    private ChannelHandlerContext ctx;

    private ScheduledFuture<?> future;

    public MqttPingScheduleHandler(Config config) {
        this.config = config;
    }

    @PostConstruct
    public void scheduleRunnableWithCronTrigger() {
        this.future = taskScheduler.schedule(new RunnableTask(), periodicTrigger);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    public void cancelPing() {
        this.future.cancel(false);
        System.out.println(String.format("Ping was canceled."));
        logger.log(Level.INFO, String.format("Ping was canceled."));
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof MqttMessage)) {
            ctx.fireChannelRead(msg);
            return;
        }

        MqttMessage message = (MqttMessage) msg;
        if (null == message.fixedHeader().messageType()) {
            ctx.fireChannelRead(msg);
        } else {
            switch (message.fixedHeader().messageType()) {
                case PINGREQ:
                    MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PINGRESP, false, MqttQoS.AT_MOST_ONCE, false, 0);
                    ctx.channel().writeAndFlush(new MqttMessage(fixedHeader));
                    logger.log(Level.FINE, String.format("Received ping request. Sent ping response. %s.", msg));
                    System.out.println(String.format("Received ping request. Sent ping response. %s.", msg));
                    break;
                case PINGRESP:
                    logger.log(Level.FINE, String.format("Received ping response %s.", msg));
                    System.out.println(String.format("Received ping response %s.", msg));
                    this.pingRequestWasSent = false;
                    break;
                default:
                    ctx.fireChannelRead(msg);
                    break;
            }
        }
    }

    private void publishPingTimeoutEvent() {
        applicationEventPublisher.publishEvent(new PingTimeoutEvent(this, "Ping response was not received for keepAlive time."));
        System.out.println(String.format("Publish PingTimeoutEvent."));
        logger.log(Level.WARNING, String.format("Publish PingTimeoutEvent."));

    }

    class RunnableTask implements Runnable {

        @Override
        public void run() {
            if (MqttPingScheduleHandler.this.pingRequestWasSent) {
                System.out.println(String.format("Ping response was not received for keepAlive time."));
                logger.log(Level.WARNING, String.format("Ping response was not received for keepAlive time."));
//                MqttPingScheduleHandler.this.future.cancel(false);
                MqttPingScheduleHandler.this.publishPingTimeoutEvent();
                return;
            }

            MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PINGREQ, false, MqttQoS.AT_MOST_ONCE, false, 0);
            MqttMessage msg = new MqttMessage(fixedHeader);
            MqttPingScheduleHandler.this.ctx.writeAndFlush(msg);
            MqttPingScheduleHandler.this.pingRequestWasSent = true;
            logger.log(Level.FINE, String.format("Sent ping request %s.", msg));
            System.out.println(String.format("Sent ping request %s.", msg));

        }
    }
}
