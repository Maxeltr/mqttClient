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

import com.google.gson.JsonObject;
import io.netty.handler.codec.mqtt.MqttQoS;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;
import ru.maxeltr.mqttClient.Config.Config;
import ru.maxeltr.mqttClient.Mqtt.ConnAckEvent;
import ru.maxeltr.mqttClient.Mqtt.MqttPingScheduleHandler;
import ru.maxeltr.mqttClient.Mqtt.PingTimeoutEvent;
import ru.maxeltr.mqttClient.Mqtt.ShutdownEvent;

/**
 *
 * @author Maxim Eltratov <<Maxim.Eltratov@ya.ru>>
 */
public class SensorManager implements ApplicationListener<ApplicationEvent> {

    private static final Logger logger = Logger.getLogger(SensorManager.class.getName());

    private CommandService commandService;

    private Config config;

    private ThreadPoolTaskScheduler taskScheduler;

    private PeriodicTrigger periodicTrigger;

    private ScheduledFuture<?> future;

    private MessageDispatcher messageDispatcher;

    private Set<Object> components;

    public SensorManager(Config config, ThreadPoolTaskScheduler taskScheduler, PeriodicTrigger periodicTrigger, MessageDispatcher messageDispatcher) {
        this.config = config;
        this.taskScheduler = taskScheduler;
        this.periodicTrigger = periodicTrigger;
        this.messageDispatcher = messageDispatcher;

    }

    @PostConstruct
    public void scheduleRunnableWithCronTrigger() {

        this.components = this.loadComponents();

        for (Object component : this.components) {
            System.out.println(String.format("SENSORMANAGER. %s", component));
        }

        this.future = taskScheduler.schedule(new RunnableTask(), periodicTrigger);
    }

    class RunnableTask implements Runnable {

        @Override
        public void run() {
            logger.log(Level.INFO, String.format("SensorManager RunnableTask start."));
            System.out.println(String.format("SensorManager RunnableTask start."));
        }
    }

    public Set<Object> loadComponents() {
//        String pathToJar = "c:\\java\\mqttClient\\Components\\app.jar";
        String pathJar = config.getProperty("componentPath", "");
        if (pathJar.trim().isEmpty()) {
            logger.log(Level.WARNING, String.format("ComponentPath is empty."));
            System.out.println(String.format("ComponentPath is empty."));
            return new HashSet<>();
        }

        Set<Path> paths = listFiles(pathJar);

        Set<Object> components = new HashSet<>();
        for (Path path : paths) {
            try {
                this.loadClassesFromJar(path, components);
            } catch (IOException ex) {
                Logger.getLogger(SensorManager.class.getName()).log(Level.SEVERE, null, ex);
            } catch (ClassNotFoundException ex) {
                Logger.getLogger(SensorManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        return components;

//        Set<Class> classes = getClassesFromJarFile(new File(pathToJar));
//        Set<Object> components = new HashSet<>();
//        Set<Class> classesToInstantiate = new HashSet<>();
//        Set<Class> classesToSetCallback = new HashSet<>();
//
//        for (Class clazz : classes) {
//            if (clazz.isInterface()) {
//                continue;
//            }
//
//            for (Class i : ClassUtils.getAllInterfaces(clazz)) {
//                if (i.getSimpleName().equals(Component.class.getSimpleName())) {
//                    classesToInstantiate.add(clazz);
//
//                }
//                if (i.getSimpleName().equals(CallbackComponent.class.getSimpleName())) {
//                    classesToSetCallback.add(clazz);
//
//                }
//            }
//        }
//
//        Object instance;
//        for (Class i : classesToInstantiate) {
//            instance = instantiateClass(i);
//            components.add(instance);
//
//            if (classesToSetCallback.contains(i)) {
//                Method method = i.getMethod("setCallback", Consumer.class);
//                method.invoke(instance, (Consumer<JsonObject>) (JsonObject val) -> {
//                    System.out.println(String.format("SENSORMANAGER. LAMBDA: %s", val));
//                    logger.log(Level.INFO, String.format("SENSORMANAGER. LAMBDA. "));
//                    this.messageDispatcher.send("test", "AT_MOST_ONCE", val, Boolean.FALSE);
//                });
//            }
//        }
//
//        return components;
    }

    private Set listFiles(String dir) {
        Set<Path> pathSet = new HashSet();

        try (Stream<Path> paths = Files.walk(Paths.get(dir))) {
            pathSet = paths
                    .filter(Files::isRegularFile)
                    .peek((file) -> {
                        System.out.println(String.format("There is [%s] in component directory %s.", file.getFileName(), dir));
                        logger.log(Level.INFO, String.format("There is [%s] in component directory %s.", file.getFileName(), dir));
                    })
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            System.out.println(String.format("Error to list files in component directory %s. %s", dir, e));
            logger.log(Level.INFO, String.format("Error to list files in component directory %s. %s", dir, e));
        }

//        try ( Stream stream = Files.list(Paths.get(dir))) {
//            paths = stream.filter(file -> !Files.isDirectory(file))
//                    .peek((file) -> {
//                        System.out.println(String.format("There is %s in component directory %s.", file.getFileName(), dir));
//                        logger.log(Level.INFO, String.format("There is %s in component directory %s.", file.getFileName(), dir));
//                    })
//                    .collect(Collectors.toSet());
//        } catch (IOException e) {
//            System.out.println(String.format("Error to list files in component directory %s. %s", dir, e));
//            logger.log(Level.INFO, String.format("Error to list files in component directory %s. %s", dir, e));
//        }
        return pathSet;
    }

    private void loadClassesFromJar(Path path, Set<Object> components) throws IOException, ClassNotFoundException {
        Set<Class> classes = getClassesFromJarFile(path.toFile());

        Set<Class> classesToInstantiate = new HashSet<>();
        Set<Class> classesToSetCallback = new HashSet<>();

        for (Class clazz : classes) {
            if (clazz.isInterface()) {
                continue;
            }

            for (Class i : ClassUtils.getAllInterfaces(clazz)) {
                if (i.getSimpleName().equals(Component.class.getSimpleName())) {
                    classesToInstantiate.add(clazz);

                }
                if (i.getSimpleName().equals(CallbackComponent.class.getSimpleName())) {
                    classesToSetCallback.add(clazz);
                }
            }
        }

        Object instance;
        for (Class i : classesToInstantiate) {
            try {
                instance = instantiateClass(i);
                if (classesToSetCallback.contains(i)) {
                    Method method = i.getMethod("setCallback", Consumer.class);
                    method.invoke(instance, (Consumer<JsonObject>) (JsonObject val) -> {
                        SensorManager.this.messageDispatcher.send(
                                val.get("topic").getAsString(),    //TODO move to config
                                MqttQoS.AT_LEAST_ONCE.name(), //val.get("qos").getAsString(),
                                val,
                                val.get("retain").getAsBoolean()
                        );
                    });
                }
                components.add(instance);
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                logger.log(Level.SEVERE, null, ex);
            }

        }
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ConnAckEvent) {
            System.out.println(String.format("ConnAck event was received."));
            logger.log(Level.INFO, String.format("ConnAck event was received."));

            for (Object component : this.components) {
                System.out.println(String.format("SENSORMANAGER. %s", component));
                try {
                    Method method = component.getClass().getMethod("start");
                    method.invoke(component);
                } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                    Logger.getLogger(SensorManager.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

        }
    }

    public static Set<String> getClassNamesFromJarFile(File givenFile) throws IOException {
        Set<String> classNames = new HashSet<>();
        try (JarFile jarFile = new JarFile(givenFile)) {
            Enumeration<JarEntry> e = jarFile.entries();
            while (e.hasMoreElements()) {
                JarEntry jarEntry = e.nextElement();
                if (jarEntry.getName().endsWith(".class")) {
                    String className = jarEntry.getName()
                            .replace("/", ".")
                            .replace(".class", "");
                    classNames.add(className);
                }
            }
            return classNames;
        }
    }

    public static Set<Class> getClassesFromJarFile(File jarFile) throws IOException, ClassNotFoundException {
        Set<String> classNames = getClassNamesFromJarFile(jarFile);
        Set<Class> classes = new HashSet<>(classNames.size());
        try (URLClassLoader cl = URLClassLoader.newInstance(
                new URL[]{new URL("jar:file:" + jarFile + "!/")})) {
            for (String name : classNames) {
                Class clazz = cl.loadClass(name); // Load the class by its name
                classes.add(clazz);
            }
        }
        return classes;
    }

    public Object instantiateClass(Class<?> clazz) throws NoSuchMethodException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        Constructor<?> constructor = clazz.getConstructor();
        Object result = constructor.newInstance();

        return result;
    }
}
