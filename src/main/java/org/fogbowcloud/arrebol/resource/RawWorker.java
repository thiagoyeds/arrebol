package org.fogbowcloud.arrebol.resource;

import org.apache.log4j.Logger;
import org.fogbowcloud.arrebol.models.command.Command;
import org.fogbowcloud.arrebol.models.task.Task;
import org.fogbowcloud.arrebol.scheduler.SchedulerPolicy;

import java.io.IOException;
import java.util.Arrays;

public class RawWorker implements Worker {

    private final Logger logger = Logger.getLogger(SchedulerPolicy.class);

    @Override
    public TaskExecutionResult execute(Task task) {

        logger.info("taskId={" + task.getId() + "}");

        TaskExecutionResult.RESULT result = TaskExecutionResult.RESULT.SUCCESS;

        //converting to array to make bellow code simple?
        Command[] cmds = new Command[task.getCommands().size()];
        task.getCommands().toArray(cmds);

        int [] exitValues = new int[cmds.length];
        Arrays.fill(exitValues, TaskExecutionResult.UNDETERMINED_RESULT);

        for(int i = 0; i < cmds.length; i++) {
            try {
                exitValues[i] = executeCommand(cmds[i]);
                logger.debug("taskId={" + task.getId() + "} cmd_index={" + i + "}" +
                        " cmd={" + cmds[i] + "} result={" + exitValues[i] + "}");
            } catch (Throwable t) {
                logger.error("taskId={" + task.getId() + "} cmd_index={" + i + "}" +
                        " cmd={" + cmds[i], t);
                result = TaskExecutionResult.RESULT.FAILURE;
            }
        }

        return new TaskExecutionResult(result, exitValues, cmds);
    }

    private int executeCommand(Command command) throws IOException, InterruptedException {

        //TODO: there are plenty of room to improve this code: working directories, stdout/stderr gathering, env vars
        String cmdStr = command.getCommand();
        Process process = Runtime.getRuntime().exec(cmdStr);
        int exitValue = process.waitFor();
        return exitValue;
    }
}