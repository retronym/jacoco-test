/*******************************************************************************
 * Copyright (c) 2009, 2025 Mountainminds GmbH & Co. KG and Contributors
 * This program and the accompanying materials are made available under
 * the terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Marc R. Hoffmann - initial API and implementation
 *
 *******************************************************************************/
package jacocodemo;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

import org.jacoco.core.analysis.*;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfoStore;
import org.jacoco.core.instr.Instrumenter;
import org.jacoco.core.runtime.IRuntime;
import org.jacoco.core.runtime.LoggerRuntime;
import org.jacoco.core.runtime.RuntimeData;

/**
 * Example usage of the JaCoCo core API. In this tutorial a single target class
 * will be instrumented and executed. Finally the coverage information will be
 * dumped.
 */
public final class JacocoFacade {

    /**
     * The test target we want to see code coverage for.
     */
    public static class TestTarget implements Runnable {

        public void run() {
            isPrime(7);
        }

        private boolean isPrime(final int n) {
            for (int i = 2; i * i <= n; i++) {
                if ((n ^ i) == 0) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Creates a new example instance printing to the given stream.
     *
     */
    public JacocoFacade() {
    }
    class InstrumentingClassLoader extends ClassLoader{
        private final ClassLoader delegate;
        private final Instrumenter instrumenter;

        public InstrumentingClassLoader(File directory, Instrumenter instrumenter) {
            try {
                URL[] urls = {directory.toURI().toURL()};
                this.delegate = new URLClassLoader(urls, getClass().getClassLoader());
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
            this.instrumenter = instrumenter;
        }
        protected Class<?> loadClass(final String name, final boolean resolve)
                throws ClassNotFoundException {
            Class<?> aClass = delegate.loadClass(name);
            if (aClass.getClassLoader() == delegate) {
                InputStream resourceAsStream = delegate.getResourceAsStream(name.replace(".", "/") + ".class");
                try {
                    byte[] bytes = instrumenter.instrument(resourceAsStream, name);
                    return defineClass(name, bytes, 0, bytes.length);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                return aClass;
            }
        }
    }


    public Result execute(File directory, String targetName) throws Exception {
        // For instrumentation and runtime we need a IRuntime instance
        // to collect execution data:
        final IRuntime runtime = new LoggerRuntime();

        // The Instrumenter creates a modified version of our test target class
        // that contains additional probes for execution data recording:
        final Instrumenter instr = new Instrumenter(runtime);

        InstrumentingClassLoader instrumentingLoader = new InstrumentingClassLoader(directory, instr);


        // Now we're ready to run our instrumented class and need to startup the
        // runtime first:
        final RuntimeData data = new RuntimeData();
        runtime.startup(data);

        final Class<?> targetClass = instrumentingLoader.loadClass(targetName);

        // Here we execute our test target class through its Runnable interface:
        final Runnable targetInstance = (Runnable) targetClass.getConstructor().newInstance();
        targetInstance.run();

        // At the end of test execution we collect execution data and shutdown
        // the runtime:
        final ExecutionDataStore executionData = new ExecutionDataStore();
        final SessionInfoStore sessionInfos = new SessionInfoStore();
        data.collect(executionData, sessionInfos, false);
        runtime.shutdown();

        // Together with the original class definition we can calculate coverage
        // information:
        final CoverageBuilder coverageBuilder = new CoverageBuilder();
        final Analyzer analyzer = new Analyzer(executionData, coverageBuilder);
        int count = analyzer.analyzeAll(directory);
        return new Result(coverageBuilder.getBundle("all"), sessionInfos, executionData);
    }

    public record Result(IBundleCoverage coverage, SessionInfoStore sessionInfos, ExecutionDataStore executionData) {}


}
