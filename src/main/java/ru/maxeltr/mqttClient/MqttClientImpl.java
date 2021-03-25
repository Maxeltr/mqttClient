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
package ru.maxeltr.mqttClient;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Promise;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.context.ApplicationListener;
import ru.maxeltr.mqttClient.Config.Config;

/**
 *
 * @author Maxim Eltratov <<Maxim.Eltratov@ya.ru>>
 */
public class MqttClientImpl implements ApplicationListener<ConnAckEvent>{

    private static final Logger logger = Logger.getLogger(MqttClientImpl.class.getName());

    private Channel channel;

    private final ChannelInitializer mqttChannelInitializer;

    private EventLoopGroup workerGroup;

    private final Config config;

    public MqttClientImpl(ChannelInitializer mqttChannelInitializer, Config config) {
        this.mqttChannelInitializer = mqttChannelInitializer;
        this.config = config;
    }

    @Override
    public void onApplicationEvent(ConnAckEvent event) {

    }

    /**
     * Connect to the specified hostname/ip using the specified port
     *
     * @param host The ip address or host to connect to
     * @param port The tcp port to connect to
     * @return
     */
    public Future connect(String host, int port) {
        logger.log(Level.INFO, String.format("Connect to %s via port %s", host, port));
        System.out.println(String.format("Connect to %s via port %s.", host, port));

        this.workerGroup = new NioEventLoopGroup();
        Promise<?> connectResult = new DefaultPromise<>(this.workerGroup.next());

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(this.workerGroup);
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.remoteAddress(host, port);
        bootstrap.handler(this.mqttChannelInitializer);

        ChannelFuture future = bootstrap.connect();
        future.addListener((ChannelFutureListener) f -> MqttClientImpl.this.channel = f.channel());

        logger.log(Level.INFO, String.format("Client connected."));
        System.out.println(String.format("Client connected."));

        return connectResult;
    }

    public void shutdown() {

        this.workerGroup.shutdownGracefully();

    }
}
