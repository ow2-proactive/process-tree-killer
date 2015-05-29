package org.ow2.proactive.process_tree_killer;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
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
        Map<String, String> processTreeKillerCookie =
                Collections.singletonMap("PROCESS_TREE_KILLER_COOKIE", "42");

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

        Process process = createScriptProcess("create_process_tree").start();

        List<ProcessTree.OSProcess> children = waitChildProcessRunning(process, 2);

        ProcessTree.get().get(process).killRecursively();

        int exitCode = process.waitFor();
        assertNotEquals(0, exitCode);

        assertFalse(isProcessRunning(children.get(0)));
        assertFalse(isProcessRunning(children.get(1)));
    }

    @Test
    public void killProcessTree_EnvironmentVariables_Windows() throws Exception {
        assumeTrue(isWindowsOS());

        Map<String, String> processTreeKillerCookie =
                Collections.singletonMap("PROCESS_TREE_KILLER_COOKIE", "42");

        ProcessBuilder createProcessTree = createScriptProcess("create_process_tree");
        createProcessTree.environment().putAll(processTreeKillerCookie);
        Process process = createProcessTree.start();

        // Process tree expected:
        //  cmd.exe
        //  | conhost.exe (started on Windows 8)
        //  | +-- start /B cmd.exe /C
        //        | +-- ping.exe
        //  | ping.exe
        List<ProcessTree.OSProcess> children = waitAtLeastNChildProcessRunning(processTreeKillerCookie, 4);

        ProcessTree.get().killAll(processTreeKillerCookie);

        int exitCode = process.waitFor();
        assertNotEquals(0, exitCode);

        for (ProcessTree.OSProcess child : children) {
            assertFalse(isProcessRunning(child));
        }
    }

    private ProcessBuilder createScriptProcess(String scriptName) throws URISyntaxException {
        if (isWindowsOS()) {
            return createScriptProcess(scriptName + ".bat", "cmd.exe", "/C");
        } else {
            return createScriptProcess(scriptName + ".sh", "bash");
        }
    }

    private ProcessBuilder createScriptProcess(String scriptName, String... shell) throws URISyntaxException {
        ProcessBuilder processBuilder = new ProcessBuilder(shell);
        processBuilder.command().add(new File(getClass().getResource("/" + scriptName).toURI()).getAbsolutePath());
        return processBuilder;
    }

    @Test
    public void killProcessTree_Detached() throws Exception {
        assumeTrue(!isWindowsOS());

        Process process = createScriptProcess("create_detached_process_tree").start();

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

    private List<ProcessTree.OSProcess> waitAtLeastNChildProcessRunning(
      final Map<String, String> environmentVariables, final int atLeastNProcess) {

        await().until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                int nbProcessFound = 0;
                for (ProcessTree.OSProcess osProcess : ProcessTree.get().processes.values()) {
                    if (osProcess.hasMatchingEnvVars(environmentVariables)) {
                        nbProcessFound++;
                    }
                }
                return nbProcessFound >= atLeastNProcess;
            }
        });

        List<ProcessTree.OSProcess> childProcesses = new ArrayList<>();
        for (ProcessTree.OSProcess osProcess : ProcessTree.get().processes.values()) {
            if (osProcess.hasMatchingEnvVars(environmentVariables)) {
                childProcesses.add(osProcess);
            }
        }
        return childProcesses;
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