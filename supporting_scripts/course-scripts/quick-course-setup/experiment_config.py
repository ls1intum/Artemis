from experiment_consts import BUBBLE_SORT_JAVA_ALLOCATE_MEMORY, BUBBLE_SORT_JAVA_CORRECT, BUBBLE_SORT_JAVA_INFINITE_LOOP, C_CORRECT, C_FACT_BUILD_SCRIPT, GRADLE_BUILD_SCRIPT, INFINITE_BUILD_SCRIPT, FAILING_BUILD_SCRIPT, MEMORY_ALLOCATE_BUILD_SCRIPT, SORT_STRATEGY_JAVA, SPAMMY_BUILD_GRADLE, START_ARTEMIS_COMMAND, START_DOCKER_COMMAND, STOP_ARTEMIS_COMMAND, STOP_DOCKER_SERVICE_COMMAND, STOP_DOCKER_SOCKET_COMMAND, WRITE_FILE_JAVA

class ExperimentConfig:
    programming_language: str
    project_type: str
    package_name: str
    build_script: str
    commit_files: dict[str, str]
    identifier: str
    remote_command: str | None = None
    execute_after_seconds: int
    timeout_experiment: int
    final_command: str | None = None

    def __init__(self, programming_language: str, project_type: str, package_name:str, build_script: str, commit_files: dict[str, str], identifier: str, remote_command: str | None = None, execute_after_seconds: int = 0, timeout_experiment: int = 60 * 15, final_command: str | None = None):
        self.programming_language = programming_language
        self.project_type = project_type
        self.package_name = package_name
        self.build_script = build_script
        self.commit_files = commit_files
        self.identifier = identifier
        self.remote_command = remote_command
        self.execute_after_seconds = execute_after_seconds
        self.timeout_experiment = timeout_experiment
        self.final_command = final_command

JAVA_HAPPY_PATH = ExperimentConfig(
    programming_language="JAVA",
    project_type="PLAIN_GRADLE",
    build_script=GRADLE_BUILD_SCRIPT,
    package_name="experiment",
    commit_files={
        "src/experiment/BubbleSort.java": BUBBLE_SORT_JAVA_CORRECT.format("experiment"),
        "src/experiment/SortStrategy.java": SORT_STRATEGY_JAVA.format("experiment")
    },
    identifier="java_happy_path"
)

JAVA_SPAMMY_BUILD = ExperimentConfig(
    programming_language="JAVA",
    project_type="PLAIN_GRADLE",
    build_script=SPAMMY_BUILD_GRADLE,
    package_name="experiment",
    commit_files={
        "src/experiment/BubbleSort.java": BUBBLE_SORT_JAVA_CORRECT.format("experiment")
    },
    identifier="java_spammy_build"
)

JAVA_TIMEOUT_BUILD = ExperimentConfig(
    programming_language="JAVA",
    project_type="PLAIN_GRADLE",
    build_script=INFINITE_BUILD_SCRIPT,
    package_name="experiment",
    commit_files={
        "src/experiment/BubbleSort.java": BUBBLE_SORT_JAVA_CORRECT.format("experiment")
    },
    identifier="java_timeout_build"
)

JAVA_FAILING_BUILD = ExperimentConfig(
    programming_language="JAVA",
    project_type="PLAIN_GRADLE",
    build_script=FAILING_BUILD_SCRIPT,
    package_name="experiment",
    commit_files={
        "src/experiment/BubbleSort.java": BUBBLE_SORT_JAVA_CORRECT.format("experiment")
    },
    identifier="java_failing_build"
)

JAVA_TIMEOUT_CODE = ExperimentConfig(
    programming_language="JAVA",
    project_type="PLAIN_GRADLE",
    build_script=GRADLE_BUILD_SCRIPT,
    package_name="experiment",
    commit_files={
        "src/experiment/BubbleSort.java": BUBBLE_SORT_JAVA_INFINITE_LOOP.format("experiment")
    },
    identifier="java_timeout_code"
)

JAVA_ALLOCATE_MEMORY = ExperimentConfig(
    programming_language="JAVA",
    project_type="PLAIN_GRADLE",
    build_script=GRADLE_BUILD_SCRIPT,
    package_name="experiment",
    commit_files={
        "src/experiment/BubbleSort.java": BUBBLE_SORT_JAVA_ALLOCATE_MEMORY.format("experiment")
    }
    , identifier="java_allocate_memory"
)

ALLOCATE_MEMORY_IN_BUILD_PLAN = ExperimentConfig(
    programming_language="JAVA",
    project_type="PLAIN_GRADLE",
    build_script=MEMORY_ALLOCATE_BUILD_SCRIPT,
    package_name="experiment",
    commit_files={
        "src/experiment/BubbleSort.java": BUBBLE_SORT_JAVA_CORRECT.format("experiment")
    }
    , identifier="java_allocate_memory_build_plan"
)

WRITE_TO_FILE_FROM_EXERCISE = ExperimentConfig(
    programming_language="JAVA",
    project_type="PLAIN_GRADLE",
    build_script=GRADLE_BUILD_SCRIPT,
    package_name="experiment",
    commit_files={
        "src/experiment/BubbleSort.java": WRITE_FILE_JAVA.format("experiment")
    }
    , identifier="java_write_to_file"
)


C_HAPPY_PATH = ExperimentConfig(
    programming_language="C",
    project_type="FACT",
    build_script=C_FACT_BUILD_SCRIPT,
    package_name="experiment",
    commit_files={
        "exercise.c": C_CORRECT
    }
    , identifier="c_happy_path"
)

C_FAILING_CODE = ExperimentConfig(
    programming_language="C",
    project_type="FACT",
    build_script=C_FACT_BUILD_SCRIPT,
    package_name="experiment",
    commit_files={
        "exercise.c": """
        int main(void) {
        """
    }
    , identifier="c_failing_code"
)

C_HOG_MEMORY = ExperimentConfig(
    programming_language="C",
    project_type="FACT",
    build_script=C_FACT_BUILD_SCRIPT,
    package_name="experiment",
    commit_files={
        "exercise.c": """
        #include <stdlib.h>
        #include <stdio.h>
        int main(void) {
            while (1) {
                void *p = malloc(1024 * 1024); // allocate 1 MB
                if (!p) {
                    perror("malloc failed");
                    return 1;
                }
                *((char *)p) = 0;
            }
            return 0;
        }
    """
    }
    , identifier="c_hog_memory"
)

C_WITH_NODE_FAILURE = ExperimentConfig(
    programming_language="C",
    project_type="FACT",
    build_script=C_FACT_BUILD_SCRIPT,
    package_name="experiment",
    commit_files={
        "exercise.c": C_CORRECT
    }
    , identifier="c_node_failure",
    remote_command=STOP_ARTEMIS_COMMAND,
    execute_after_seconds=30,
    timeout_experiment=60 * 6,
    final_command=START_ARTEMIS_COMMAND
)

DOCKER_CLIENT_FAILURE = ExperimentConfig(
    programming_language="C",
    project_type="FACT",
    build_script=C_FACT_BUILD_SCRIPT,
    package_name="experiment",
    commit_files={
        "exercise.c": C_CORRECT
    }
    , identifier="docker_client_failure_C",
    remote_command=f"{STOP_DOCKER_SERVICE_COMMAND} && {STOP_DOCKER_SOCKET_COMMAND}",
    execute_after_seconds=30,
    timeout_experiment=60 * 5,
    final_command=START_DOCKER_COMMAND
)

DOCKER_CLIENT_FAILURE_JAVA = ExperimentConfig(
    programming_language="JAVA",
    project_type="PLAIN_GRADLE",
    build_script=GRADLE_BUILD_SCRIPT,
    package_name="experiment",
    commit_files={
        "src/experiment/BubbleSort.java": BUBBLE_SORT_JAVA_CORRECT.format("experiment")
    },
    identifier="docker_client_failure_java",
    remote_command=f"{STOP_DOCKER_SERVICE_COMMAND} && {STOP_DOCKER_SOCKET_COMMAND}",
    execute_after_seconds=30,
    timeout_experiment=60 * 15,
    final_command=START_DOCKER_COMMAND
)