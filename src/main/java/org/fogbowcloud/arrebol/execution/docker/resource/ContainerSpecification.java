package org.fogbowcloud.arrebol.execution.docker.resource;

import java.util.Map;
import java.util.Objects;

public class ContainerSpecification {
    private String imageId;
    private Map<String, String> requirements;
    private Map<String, String> envVars;

    public ContainerSpecification(){}

    public ContainerSpecification(String imageId, Map<String, String> requirements,
        Map<String, String> envVars) {

        this.imageId = imageId;
        this.requirements = requirements;
        this.envVars = envVars;
    }

    public String getImageId() {
        return imageId;
    }

    public Map<String, String> getRequirements() {
        return requirements;
    }

    public Map<String, String> getEnvVars() {
        return envVars;
    }

    @Override
    public String toString() {
        return "{" + "imageId='" + imageId + '\'' + ", requirements="
            + mapToString(requirements) + ", envVars=" + mapToString(envVars) + '}';
    }

    private String mapToString(Map<String, String> map) {
        StringBuilder mapAsString = new StringBuilder("{");
        if(!Objects.isNull(map)){
            for (String key : map.keySet()) {
                mapAsString.append(key + "=" + map.get(key) + ", ");
            }
            if(!map.isEmpty()){
                mapAsString.delete(mapAsString.length()-2, mapAsString.length());
            }
        }
        mapAsString.append("}");
        return mapAsString.toString();
    }
}
