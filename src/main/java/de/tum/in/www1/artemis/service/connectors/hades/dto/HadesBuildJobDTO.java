package de.tum.in.www1.artemis.service.connectors.hades.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * DTO for a Hades build job.
 *
 * Example Json
 * {
 * "name": "Example Job",
 * "metadata": {},
 * "timestamp": "2021-01-01T00:00:00.000Z",
 * "priority": 1, // optional, default 3
 * "steps": [
 * {
 * "id": 1, // mandatory to declare the order of execution
 * "name": "Clone",
 * "image": "ghcr.io/mtze/hades/hades-clone-container:pr-28", // mandatory
 * "metadata": {
 * "REPOSITORY_DIR": "/shared",
 * "HADES_TEST_USERNAME": "artemis_test_user_11",
 * "HADES_TEST_PASSWORD": "pw",
 * "HADES_TEST_URL": "https://bitbucket-prelive.ase.in.tum.de/scm/staginglinhubertcadsfae3wafsd/staginglinhubertcadsfae3wafsd-tests.git",
 * "HADES_TEST_PATH": "./example",
 * "HADES_ASSIGNMENT_USERNAME": "artemis_test_user_11",
 * "HADES_ASSIGNMENT_PASSWORD": "pw",
 * "HADES_ASSIGNMENT_URL": "https://bitbucket-prelive.ase.in.tum.de/scm/staginglinhubertcadsfae3wafsd/staginglinhubertcadsfae3wafsd-solution.git",
 * "HADES_ASSIGNMENT_PATH": "./example/assignment"
 * }
 * },
 * {
 * "id": 2, // mandatory to declare the order of execution
 * "name": "Execute",
 * "image": "ls1tum/artemis-maven-template:java17-18", // mandatory
 * "script": "cd ./example && ls -lah && ./gradlew clean test"
 * }
 * ]
 * }
 *
 */
public class HadesBuildJobDTO implements Serializable {

    private String name;

    private HashMap<String, String> metadata;

    private String timestamp;

    private Integer priority;

    private ArrayList<HadesBuildStepDTO> steps;

    public HadesBuildJobDTO() {
        // empty constructor for jackson
    }

    public HadesBuildJobDTO(String name, HashMap<String, String> metadata, String timestamp, Integer priority, ArrayList<HadesBuildStepDTO> steps) {
        this.name = name;
        this.metadata = metadata;
        this.timestamp = timestamp;
        this.priority = priority;
        this.steps = steps;
    }

    public String getName() {
        return name;
    }

    public Object getMetadata() {
        return metadata;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public Integer getPriority() {
        return priority;
    }

    public Object getSteps() {
        return steps;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setMetadata(HashMap<String, String> metadata) {
        this.metadata = metadata;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public void setSteps(ArrayList<HadesBuildStepDTO> steps) {
        this.steps = steps;
    }

    @Override
    public String toString() {
        return "HadesBuildJobDTO{" + "name='" + name + '\'' + ", metadata=" + metadata + ", timestamp='" + timestamp + '\'' + ", priority=" + priority + ", steps=" + steps + '}';
    }
}
