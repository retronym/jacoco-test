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
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfoStore;
import org.jacoco.core.instr.Instrumenter;
import org.jacoco.core.runtime.IRuntime;
import org.jacoco.core.runtime.LoggerRuntime;
import org.jacoco.core.runtime.RuntimeData;
import scala.tools.nsc.backend.jvm.AsmUtils;

/**
 * Example usage of the JaCoCo core API. In this tutorial a single target class
 * will be instrumented and executed. Finally the coverage information will be
 * dumped.
 */
public final class CoreTutorial {

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
     * A class loader that loads classes from in-memory data.
     */
    public static class MemoryClassLoader extends ClassLoader {

        private final Map<String, byte[]> definitions = new HashMap<String, byte[]>();

        /**
         * Add an in-memory representation of a class.
         *
         * @param name
         *            name of the class
         * @param bytes
         *            class definition
         */
        public void addDefinition(final String name, final byte[] bytes) {
            definitions.put(name, bytes);
        }

        @Override
        protected Class<?> loadClass(final String name, final boolean resolve)
                throws ClassNotFoundException {
            final byte[] bytes = definitions.get(name);
            if (bytes != null) {
                return defineClass(name, bytes, 0, bytes.length);
            }
            return super.loadClass(name, resolve);
        }

    }

    private final PrintStream out;

    /**
     * Creates a new example instance printing to the given stream.
     *
     * @param out
     *            stream for outputs
     */
    public CoreTutorial(final PrintStream out) {
        this.out = out;
    }
    class InstrumentingClassLoader extends ClassLoader{
        private final ClassLoader delegate;
        private final Instrumenter instrumenter;
        private final Set<Class<?>> instrumented = new HashSet<>();

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
                    Class<?> instrumentedClass = defineClass(name, bytes, 0, bytes.length);
                    instrumented.add(instrumentedClass);
                    String text = AsmUtils.textify(AsmUtils.classFromBytes(bytes));
                    System.out.println(text);
                    return instrumentedClass;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                return aClass;
            }
        }
    }


    public void execute(File directory, String targetName) throws Exception {
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


        // Let's dump some metrics and line coverage information:
        for (final IClassCoverage cc : coverageBuilder.getClasses()) {
            out.printf("Coverage of class %s%n", cc.getName());

            printCounter("instructions", cc.getInstructionCounter());
            printCounter("branches", cc.getBranchCounter());
            printCounter("lines", cc.getLineCounter());
            printCounter("methods", cc.getMethodCounter());
            printCounter("complexity", cc.getComplexityCounter());

            for (int i = cc.getFirstLine(); i <= cc.getLastLine(); i++) {
                out.printf("Line %s: %s%n", Integer.valueOf(i),
                        getColor(cc.getLine(i).getStatus()));
            }
        }
    }

    private InputStream getTargetClass(String name, ClassLoader classLoader) {
        final String resource = name.replace('.', '/') + ".class";
        return classLoader.getResourceAsStream(resource);
    }

    private void printCounter(final String unit, final ICounter counter) {
        final Integer missed = Integer.valueOf(counter.getMissedCount());
        final Integer total = Integer.valueOf(counter.getTotalCount());
        out.printf("%s of %s %s missed%n", missed, total, unit);
    }

    private String getColor(final int status) {
        switch (status) {
            case ICounter.NOT_COVERED:
                return "red";
            case ICounter.PARTLY_COVERED:
                return "yellow";
            case ICounter.FULLY_COVERED:
                return "green";
        }
        return "";
    }


}
