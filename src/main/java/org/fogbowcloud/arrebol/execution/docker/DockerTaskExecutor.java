package org.fogbowcloud.arrebol.execution.docker;

import static org.fogbowcloud.arrebol.execution.docker.constants.DockerConstants.ADDRESS_METADATA_KEY;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToOne;
import javax.persistence.Transient;
import org.apache.log4j.Logger;
import org.fogbowcloud.arrebol.execution.TaskExecutionResult;
import org.fogbowcloud.arrebol.execution.TaskExecutionResult.RESULT;
import org.fogbowcloud.arrebol.execution.TaskExecutor;
import org.fogbowcloud.arrebol.execution.docker.resource.ContainerSpecification;
import org.fogbowcloud.arrebol.execution.docker.resource.DefaultDockerContainerResource;
import org.fogbowcloud.arrebol.execution.docker.resource.DockerContainerResource;
import org.fogbowcloud.arrebol.execution.docker.tasklet.Tasklet;
import org.fogbowcloud.arrebol.models.command.Command;
import org.fogbowcloud.arrebol.models.command.CommandState;
import org.fogbowcloud.arrebol.models.task.RequirementsContants;
import org.fogbowcloud.arrebol.models.task.Task;
import org.fogbowcloud.arrebol.resource.StaticPool;

/**
 * This implementation of {@link TaskExecutor} manages the execution of a {@link Task} in a,
 * possible remote, DockerContainer. A new container is created to execute every {@link Task} and
 * destroyed after the execution has finished. A task representation is sent to the container, which
 * drives the execution of the commands itself. This objects monitors the execution until it ends on
 * success or failure. If any container initialization error occurs, the return {@link
 * TaskExecutionResult} indicates the Failure.
 */
@Entity
public class DockerTaskExecutor implements TaskExecutor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Transient
    private final Logger LOGGER = Logger.getLogger(DockerTaskExecutor.class);
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, targetEntity = DefaultDockerContainerResource.class)
    private DockerContainerResource dockerContainerResource;
    @Transient
    private Tasklet tasklet;
    private String defaultImageId;

    /**
     * @param defaultImageId Image docker used as default if no one is specified in the task.
     */
    public DockerTaskExecutor(String defaultImageId,
            DockerContainerResource dockerContainerResource, Tasklet tasklet) {
        this.defaultImageId = defaultImageId;
        this.dockerContainerResource = dockerContainerResource;
        this.tasklet = tasklet;
    }

    public DockerTaskExecutor(){}

    /** {@inheritDoc} */
    @Override
    public TaskExecutionResult execute(Task task) {
        TaskExecutionResult taskExecutionResult;
        try {
            ContainerSpecification containerSpecification = createContainerSpecification(task);
            LOGGER.info("Starting the Docker Task Executor [" + this.dockerContainerResource.getId() + "] to execute task [" + task.getId() + "]");
            this.dockerContainerResource.start(containerSpecification);
        } catch (Throwable e) {
            LOGGER.error("Error while start resource: [" + e.getMessage() + "]", e);
            failTask(task);
            taskExecutionResult = getFailResultInstance(task.getTaskSpec().getCommands().size());
            return taskExecutionResult;
        }
        LOGGER.debug("Starting to execute task [" + task.getId() + "] in resource[" + this.dockerContainerResource.getId() + "]");
        taskExecutionResult = this.tasklet.execute(task);
        try {
            LOGGER.info("Stopping DockerTaskExecutor [" + this.dockerContainerResource.getId() + "]");
            this.dockerContainerResource.stop();
        } catch (Throwable e) {
            LOGGER.error("Error while stop Docker Task Executor [" + this.dockerContainerResource.getId() + "]: [" + e.getMessage() + "]", e);
        }

        return taskExecutionResult;
    }

    @Override
    public Map<String, String> getMetadata() {
        Map<String, String> metadata = new HashMap<>();
        String address = this.dockerContainerResource.getApiAddress();
        metadata.put(ADDRESS_METADATA_KEY, address);
        return metadata;
    }

    private ContainerSpecification createContainerSpecification(Task task) {
        Map<String, String> requirements = task.getTaskSpec().getRequirements();
        Map<String, String> envVars = task.getTaskSpec().getEnvVars();
        ContainerSpecification containerSpecification = new ContainerSpecification(defaultImageId, requirements, envVars);
        if(Objects.nonNull(requirements)){
            String imageId = requirements.get(RequirementsContants.IMAGE_KEY);
            if(Objects.nonNull(imageId)){
                containerSpecification =
                    new ContainerSpecification(imageId, requirements, envVars);
            }
        }
        return containerSpecification;
    }

    private void failTask(Task task) {
        for (Command c : task.getTaskSpec().getCommands()) {
            c.setState(CommandState.FAILED);
            c.setExitcode(TaskExecutionResult.UNDETERMINED_RESULT);
        }
    }

    private TaskExecutionResult getFailResultInstance(int size) {
        int[] exitCodes = new int[size];
        Arrays.fill(exitCodes, TaskExecutionResult.UNDETERMINED_RESULT);
        TaskExecutionResult taskExecutionResult =
                new TaskExecutionResult(RESULT.FAILURE, exitCodes);
        return taskExecutionResult;
    }
}
