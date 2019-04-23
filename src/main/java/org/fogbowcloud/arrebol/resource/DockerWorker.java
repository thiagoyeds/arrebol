package org.fogbowcloud.arrebol.resource;

import org.apache.log4j.Logger;
import org.fogbowcloud.arrebol.core.models.command.Command;
import org.fogbowcloud.arrebol.core.models.task.Task;
import org.fogbowcloud.arrebol.resource.exceptions.DockerStartException;

public class DockerWorker implements Worker {

    private String imageId;
    private String containerName;

    private final String BASH = "/bin/bash";
    private final String DOCKER_EXEC = "sudo docker exec";
    private final String DOCKER_RUN = "sudo docker run --rm -idt --name";
    private final String DOCKER_STOP = "sudo docker stop";

    private final Logger LOGGER = Logger.getLogger(DockerWorker.class);

    public DockerWorker(String imageId, String containerName) {
        this.imageId = imageId;
        this.containerName = containerName;
    }

    @Override
    public TaskExecutionResult execute(Task task) {
        TaskExecutionResult taskExecutionResult;
        Integer startStatus = this.start();

        if(startStatus != 0){
            LOGGER.error("Exit code from container start: " + startStatus);
            throw new DockerStartException("Could not start container " + this.containerName);
        }

        LOGGER.info("Successful started container " + this.containerName);

        int commandsSize = task.getCommands().size();
        Command[] commands = task.getCommands().toArray(new Command[commandsSize]);
        int[] commandsResults = new int[commandsSize];

        LOGGER.info("Starting to execute commands of task " + task.getId());
        for (int i = 0; i < commandsSize; i++) {
            Command c = commands[i];
            Integer exitCode = executeCommand(c);
            commandsResults[i] = exitCode;
        }

        Integer stopStatus = this.stop();
        if(stopStatus != 0){
            LOGGER.error("Exit code from container stop: " + stopStatus);
        }

        TaskExecutionResult.RESULT result = TaskExecutionResult.RESULT.SUCCESS;
        for (int i : commandsResults) {
            if (i != 0) {
                result = TaskExecutionResult.RESULT.FAILURE;
                break;
            }
        }

        LOGGER.info("Result of task" + task.getId() + ": " + result.toString());

        taskExecutionResult = new TaskExecutionResult(result, commandsResults, commands);
        return taskExecutionResult;
    }

    private Integer start() {
        try {
            String[] cmd = {
                    BASH,
                    "-c",
                    DOCKER_RUN + " " + this.containerName + " " + this.imageId
            };
            Process p = Runtime.getRuntime().exec(cmd);

            Integer exitCode = p.waitFor();
            return exitCode;
        } catch (Exception e){
            throw new RuntimeException("Error while trying execute commands to start container " + this.containerName);
        }
    }

    private Integer stop() {
        try {
            String[] cmd = {
                    BASH,
                    "-c",
                    DOCKER_STOP + " " + this.containerName
            };
            Process p = Runtime.getRuntime().exec(cmd);

            Integer exitCode = p.waitFor();
            return exitCode;

        } catch (Exception e){
            throw new RuntimeException("Error while trying execute commands to stop container " + this.containerName);
        }
    }

    private Integer executeCommand(Command command) {
        try {
            String commandStr = command.getCommand();
            String[] cmd = {
                    BASH,
                    "-c",
                    DOCKER_EXEC + " " + this.containerName + " " + commandStr
            };

            Process p = Runtime.getRuntime().exec(cmd);

            Integer exitCode = p.waitFor();
            return exitCode;

        } catch(Exception e){
            throw new RuntimeException("Error while truing execute commands to container " + this.containerName);
        }
    }

    public String getImageId(){
        return this.imageId;
    }

    public String getContainerName() {
        return this.containerName;
    }
}
