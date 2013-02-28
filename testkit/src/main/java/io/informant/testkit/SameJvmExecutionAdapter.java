/**
 * Copyright 2011-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.informant.testkit;

import io.informant.MainEntryPoint;
import io.informant.config.PluginInfoCache;
import io.informant.testkit.InformantContainer.ExecutionAdapter;
import io.informant.util.ThreadSafe;
import io.informant.util.Threads;
import io.informant.weaving.IsolatedWeavingClassLoader;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
@ThreadSafe
class SameJvmExecutionAdapter implements ExecutionAdapter {

    private final Collection<Thread> preExistingThreads;
    private final IsolatedWeavingClassLoader isolatedWeavingClassLoader;
    private final SameJvmInformant informant;
    private final List<Thread> executingAppThreads = Lists.newCopyOnWriteArrayList();

    SameJvmExecutionAdapter(final Map<String, String> properties) throws Exception {
        preExistingThreads = Threads.currentThreads();
        MainEntryPoint.start(properties);
        IsolatedWeavingClassLoader.Builder loader = IsolatedWeavingClassLoader.builder();
        PluginInfoCache pluginInfoCache = new PluginInfoCache();
        loader.setMixins(pluginInfoCache.getMixins());
        loader.setAdvisors(pluginInfoCache.getAdvisors());
        loader.addBridgeClasses(AppUnderTest.class);
        loader.addExcludePackages("io.informant.api", "io.informant.core", "io.informant.local",
                "io.informant.shaded");
        loader.weavingMetric(MainEntryPoint.getCoreModule().getWeavingMetric());
        isolatedWeavingClassLoader = loader.build();
        informant = new SameJvmInformant();
    }

    public Informant getInformant() {
        return informant;
    }

    public void executeAppUnderTest(final Class<? extends AppUnderTest> appUnderTestClass)
            throws Exception {
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(isolatedWeavingClassLoader);
        executingAppThreads.add(Thread.currentThread());
        try {
            isolatedWeavingClassLoader.newInstance(appUnderTestClass, AppUnderTest.class)
                    .executeApp();
        } finally {
            executingAppThreads.remove(Thread.currentThread());
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
        }
    }

    public void interruptAppUnderTest() throws IOException, InterruptedException {
        for (Thread thread : executingAppThreads) {
            thread.interrupt();
        }
    }

    public void close() throws Exception {
        Threads.preShutdownCheck(preExistingThreads);
        MainEntryPoint.shutdown();
        Threads.postShutdownCheck(preExistingThreads);
    }

    public int getUiPort() {
        return MainEntryPoint.getUiModule().getHttpServer().getPort();
    }
}