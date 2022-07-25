package org.fogbowcloud.arrebol.execution.docker.resource;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Transient;
import org.apache.log4j.Logger;
import org.fogbowcloud.arrebol.execution.docker.constants.DockerConstants;
import org.fogbowcloud.arrebol.execution.docker.exceptions.DockerCreateContainerException;
import org.fogbowcloud.arrebol.execution.docker.exceptions.DockerImageNotFoundException;
import org.fogbowcloud.arrebol.execution.docker.exceptions.DockerRemoveContainerException;
import org.fogbowcloud.arrebol.execution.docker.exceptions.DockerStartException;
import org.fogbowcloud.arrebol.execution.docker.helpers.DockerContainerRequestHelper;
import org.fogbowcloud.arrebol.execution.docker.helpers.DockerImageRequestHelper;

@Entity
public class DefaultDockerContainerResource implements DockerContainerResource {
    @Transient
    private static final Logger LOGGER = Logger.getLogger(DefaultDockerContainerResource.class);
    private boolean started;
    @Id
    private String resourceId;
    private String apiAddress;
    @Transient
    private DockerContainerRequestHelper dockerContainerRequestHelper;
    @Transient
    private DockerImageRequestHelper dockerImageRequestHelper;

    /**
     * @param resourceId Sets the name of the container, is an identifier.
     */
    public DefaultDockerContainerResource(String resourceId, DockerContainerRequestHelper dockerContainerRequestHelper,
        DockerImageRequestHelper dockerImageRequestHelper) {
        this.resourceId = resourceId;
        this.dockerContainerRequestHelper = dockerContainerRequestHelper;
        this.dockerImageRequestHelper = dockerImageRequestHelper;
        this.started = false;
        this.apiAddress = dockerContainerRequestHelper.getAddress();
    }

    public DefaultDockerContainerResource(){}

    @Override
    public void start(ContainerSpecification containerSpecification)
            throws DockerStartException, DockerCreateContainerException,
                    UnsupportedEncodingException {
        if (isStarted()) {
            throw new DockerStartException("Container[" + this.resourceId + "] was already started");
        }
        if (Objects.isNull(containerSpecification)) {
            throw new IllegalArgumentException("ContainerSpecification may be not null.");
        }
        LOGGER.info(
                "Container specification ["
                        + containerSpecification.toString()
                        + "] to container ["
                        + this.resourceId
                        + "]");
        String image = this.setUpImage(containerSpecification.getImageId());
        Map<String, String> containerRequirements =
                this.getDockerContainerRequirements(containerSpecification.getRequirements());
        Map<String, String> containerVarEnvs = containerSpecification.getEnvVars();
        this.dockerContainerRequestHelper.createContainer(image, containerRequirements, containerVarEnvs);
        this.dockerContainerRequestHelper.startContainer();
        this.started = true;
        LOGGER.info("Started the container " + this.resourceId);
    }

    private String setUpImage(String image) {
        try {
            if (image != null && !image.trim().isEmpty()) {
                LOGGER.info("Using image [" + image + "] to start " + resourceId);
            } else {
                throw new IllegalArgumentException("Image ID may be not null or empty");
            }
            dockerImageRequestHelper.pullImage(image);
        } catch (Exception e) {
            throw new DockerImageNotFoundException(
                    "Error to pull docker image: " + image + " with error " + e.getMessage());
        }
        return image;
    }

    /**
     * This method map Docker requirements from task to requirements keys from Docker API
     */
    private Map<String, String> getDockerContainerRequirements(
            Map<String, String> taskRequirements) {
        Map<String, String> mapRequirements = new HashMap<>();
        if (Objects.nonNull(taskRequirements)) {
            String dockerRequirements =
                    taskRequirements.get(DockerConstants.DOCKER_REQUIREMENTS_KEY);
            if (dockerRequirements != null) {
                String[] requirements = dockerRequirements.split("&&");
                for (String requirement : requirements) {
                    String[] req = requirement.split("==");
                    String key = req[0].trim();
                    String value = req[1].trim();
                    switch (key) {
                        case DockerConstants.DOCKER_MEMORY_KEY:
                            mapRequirements.put(DockerConstants.JSON_KEY_MEMORY, value);
                            LOGGER.info("Added requirement [" + DockerConstants.JSON_KEY_MEMORY +
                                "] with value [" + value + "] to container [" + this.resourceId + "]");
                            break;
                        case DockerConstants.DOCKER_CPU_WEIGHT_KEY:
                            mapRequirements.put(DockerConstants.JSON_KEY_CPU_SHARES, value);
                            LOGGER.info("Added requirement [" + DockerConstants.JSON_KEY_CPU_SHARES +
                                "] with value [" + value + "] to container [" + this.resourceId + "]");
                            break;
                    }
                }
            }
        }
        return mapRequirements;
    }

    @Override
    public void stop() throws DockerRemoveContainerException {
        if(!isStarted()){
            throw new DockerRemoveContainerException("Container[" + this.resourceId + "] was already stopped");
        }
        this.dockerContainerRequestHelper.removeContainer();
        this.started = false;
    }

    @Override
    public String getId() {
        return this.resourceId;
    }

    @Override
    public String getApiAddress() {
        return this.apiAddress;
    }

    @Override
    public boolean isStarted() {
        return started;
    }
}
