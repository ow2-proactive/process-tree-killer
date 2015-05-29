package org.ow2.proactive.process_tree_killer;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.junit.Test;

import static com.jayway.awaitility.Awaitility.await;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;


public class ProcessTreeTest {

    @Test
    public void killSingleProcess() throws Exception {
        Process process = createSleepyProcess().start();

        ProcessTree.get().get(process).kill();

        int exitCode = process.waitFor();
        assertNotEquals(0, exitCode);
    }

    @Test
    public void killSingleProcess_WrongInitialization() throws Exception {
        Process process = null;
        try {
            ProcessTree osProcesses = ProcessTree.get();

            process = createSleepyProcess().start();
            osProcesses.get(process).kill();

            fail("Should throw NullPointerException");
        } catch (NullPointerException e) {
            ProcessTree.get().get(process).kill();
        }
    }

    @Test
    public void killSingleProcess_UsingEnvironmentVariable() throws Exception {
        Map<String, String> processTreeKillerCookie = Collections.singletonMap("PROCESS_TREE_KILLER_COOKIE",
                "42");

        ProcessBuilder processBuilder = createSleepyProcess();
        processBuilder.environment().putAll(processTreeKillerCookie);
        Process process = processBuilder.start();

        ProcessTree.get().killAll(processTreeKillerCookie);

        int exitCode = process.waitFor();
        assertNotEquals(0, exitCode);
    }

    @Test
    public void killNohupProcess() throws Exception {
        assumeTrue(!isWindowsOS());

        Process process = new ProcessBuilder("nohup", "sleep", "10000").start();

        ProcessTree.get().get(process).kill();

        int exitCode = process.waitFor();
        assertNotEquals(0, exitCode);
    }

    @Test
    public void killProcessTree() throws Exception {
        assumeTrue(!isWindowsOS());

        Process process = new ProcessBuilder("bash", new File(getClass().getResource(
                "/create_process_tree.sh").toURI()).getAbsolutePath()).start();

        List<ProcessTree.OSProcess> children = waitChildProcessRunning(process, 2);

        ProcessTree.get().get(process).killRecursively();

        int exitCode = process.waitFor();
        assertNotEquals(0, exitCode);

        assertFalse(isProcessRunning(children.get(0)));
        assertFalse(isProcessRunning(children.get(1)));
    }

    @Test
    public void killProcessTree_Detached() throws Exception {
        assumeTrue(!isWindowsOS());

        Process process = new ProcessBuilder("bash", new File(getClass().getResource(
                "/create_detached_process_tree.sh").toURI()).getAbsolutePath()).start();

        List<ProcessTree.OSProcess> children = waitChildProcessRunning(process, 2);

        ProcessTree.get().get(process).killRecursively();

        int exitCode = process.waitFor();
        assertNotEquals(0, exitCode);

        assertFalse(isProcessRunning(children.get(0)));
        assertFalse(isProcessRunning(children.get(1)));
    }

    private boolean isProcessRunning(ProcessTree.OSProcess process) {
        return ProcessTree.get().get(process.getPid()) != null;
    }

    private List<ProcessTree.OSProcess> waitChildProcessRunning(final Process process,
            final int expectedNumberOfChildProcesses) {

        await().until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                ProcessTree.OSProcess osProcess = ProcessTree.get().get(process);
                return osProcess != null && osProcess.getChildren().size() == expectedNumberOfChildProcesses;
            }
        });
        return ProcessTree.get().get(process).getChildren();
    }

    private ProcessBuilder createSleepyProcess() {
        if (isWindowsOS()) {
            return new ProcessBuilder("ping", "127.0.0.1", "-n", "10000");
        } else {
            return new ProcessBuilder("sleep", "10000");
        }
    }

    private boolean isWindowsOS() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }
}