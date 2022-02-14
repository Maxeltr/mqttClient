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
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import org.apache.commons.lang3.ClassUtils;
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
    public void scheduleRunnableWithCronTrigger() throws ClassNotFoundException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException, MalformedURLException, IOException {

        Set<Object> components = this.loadComponents();

        for (Object component : components) {
            System.out.println(String.format("SENSORMANAGER. %s", component));
        }

        this.future = taskScheduler.schedule(new RunnableTask(), periodicTrigger);
    }

    class RunnableTask implements Runnable {

        @Override
        public void run() {

        }
    }

    public Set<Object> loadComponents() throws IOException, ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        String pathToJar = "c:\\java\\mqttClient\\Components\\app.jar";

        Set<Class> classes = getClassesFromJarFile(new File(pathToJar));
        Set<Object> components = new HashSet<>();
        for (Class clazz : classes) {
            if (clazz.isInterface()) {
                continue;
            }
            for (Class i : ClassUtils.getAllInterfaces(clazz)) {
                System.out.println(String.format("SENSORMANAGER. class: %s. interface: %s", clazz, i));
                if (i.getSimpleName().equals(Component.class.getSimpleName())) {
                    components.add(instantiateClass(clazz));
                    break;
                }
            }

        }

        return components;
    }

    public static Set<String> getClassNamesFromJarFile(File givenFile) throws IOException {
        Set<String> classNames = new HashSet<>();
        try ( JarFile jarFile = new JarFile(givenFile)) {
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
        try ( URLClassLoader cl = URLClassLoader.newInstance(
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
