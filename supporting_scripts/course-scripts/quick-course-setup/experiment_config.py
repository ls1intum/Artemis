from experiment_consts import BUBBLE_SORT_JAVA_ALLOCATE_MEMORY, BUBBLE_SORT_JAVA_CORRECT, BUBBLE_SORT_JAVA_INFINITE_LOOP, C_FACT_BUILD_SCRIPT, GRADLE_BUILD_SCRIPT, INFINITE_BUILD_SCRIPT, FAILING_BUILD_SCRIPT, SORT_STRATEGY_JAVA, SPAMMY_BUILD_SCRIPT

class ExperimentConfig:
    programming_language: str
    project_type: str
    package_name: str
    build_script: str
    commit_files: dict[str, str]

    def __init__(self, programming_language: str, project_type: str, package_name:str, build_script: str, commit_files: dict[str, str]):
        self.programming_language = programming_language
        self.project_type = project_type
        self.package_name = package_name
        self.build_script = build_script
        self.commit_files = commit_files

JAVA_HAPPY_PATH = ExperimentConfig(
    programming_language="JAVA",
    project_type="PLAIN_GRADLE",
    build_script=GRADLE_BUILD_SCRIPT,
    package_name="experiment",
    commit_files={
        "src/experiment/BubbleSort.java": BUBBLE_SORT_JAVA_CORRECT.format("experiment"),
        "src/experiment/SortStrategy.java": SORT_STRATEGY_JAVA.format("experiment")
    }
)

JAVA_SPAMMY_BUILD = ExperimentConfig(
    programming_language="JAVA",
    project_type="PLAIN_GRADLE",
    build_script=SPAMMY_BUILD_SCRIPT,
    package_name="experiment",
    commit_files={
        "src/experiment/BubbleSort.java": BUBBLE_SORT_JAVA_CORRECT.format("experiment")
    }
)

JAVA_TIMEOUT_BUILD = ExperimentConfig(
    programming_language="JAVA",
    project_type="PLAIN_GRADLE",
    build_script=INFINITE_BUILD_SCRIPT,
    package_name="experiment",
    commit_files={
        "src/experiment/BubbleSort.java": BUBBLE_SORT_JAVA_CORRECT.format("experiment")
    }
)

JAVA_FAILING_BUILD = ExperimentConfig(
    programming_language="JAVA",
    project_type="PLAIN_GRADLE",
    build_script=FAILING_BUILD_SCRIPT,
    package_name="experiment",
    commit_files={
        "src/experiment/BubbleSort.java": BUBBLE_SORT_JAVA_CORRECT.format("experiment")
    }
)

JAVA_TIMEOUT_CODE = ExperimentConfig(
    programming_language="JAVA",
    project_type="PLAIN_GRADLE",
    build_script=GRADLE_BUILD_SCRIPT,
    package_name="experiment",
    commit_files={
        "src/experiment/BubbleSort.java": BUBBLE_SORT_JAVA_INFINITE_LOOP.format("experiment")
    }
)

JAVA_ALLOCATE_MEMORY = ExperimentConfig(
    programming_language="JAVA",
    project_type="PLAIN_GRADLE",
    build_script=GRADLE_BUILD_SCRIPT,
    package_name="experiment",
    commit_files={
        "src/experiment/BubbleSort.java": BUBBLE_SORT_JAVA_ALLOCATE_MEMORY.format("experiment")
    }
)

C_HAPPY_PATH = ExperimentConfig(
    programming_language="C",
    project_type="FACT",
    build_script=C_FACT_BUILD_SCRIPT,
    package_name="experiment",
    commit_files={
        "exercise.c": """#include <stdio.h>
        #include <stdlib.h>

        int main(void) {
            int x = 6;
            int y = 10;
            printf("%d\\n", x * x + 5 * y - 4);
            return EXIT_SUCCESS;
        }
        """
    }
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
)
