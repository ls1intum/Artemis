GRADLE_BUILD_SCRIPT = "#!/usr/bin/env bash\nset -e\n\ngradle () {\n  echo '⚙️ executing gradle'\n  chmod +x ./gradlew\n  ./gradlew clean test\n}\n\nmain () {\n  gradle\n}\n\nmain \"${@}\"\n"

INFINITE_BUILD_SCRIPT = """#!/usr/bin/env bash
        set -e
        main () {
        while true; do
            echo '✅ Loop cycle complete, restarting...'
            sleep 10
        done
        }
        main "${@}"
    """

FAILING_BUILD_SCRIPT = """#!/usr/bin/env bash
        set -e
        echo "❌ This script is designed to fail immediately."
        exit 1
    """

SPAMMY_BUILD_GRADLE = """
    #!/usr/bin/env bash
    set -e

    spam_logs() {
        log_line="This is a test log message meant to spam the output."
        for ((i = 1; i <= 100000; i++)); do
            printf "Log line %d: %s\n" "$i" "$log_line"
        done
    echo "✅ Finished printing log lines."
    }

    gradle_build() {
        echo '⚙️ Executing Gradle build...'
        chmod +x ./gradlew
        ./gradlew clean test
    }

    main() {
        spam_logs
        gradle_build
    }

    main "$@"
"""

BUBBLE_SORT_JAVA_CORRECT = """
    package {0};
    import java.util.*;
    public class BubbleSort {{
        public void performSort(List<Date> input) {{
            for (int i = input.size() - 1; i >= 0; i--) {{
                for (int j = 0; j < i; j++) {{
                    if (input.get(j).compareTo(input.get(j + 1)) > 0) {{
                        Date temp = input.get(j);
                        input.set(j, input.get(j + 1));
                        input.set(j + 1, temp);
                    }}
                }}
            }}
        }}
    }}
    """


WRITE_FILE_JAVA = """
    package {0};
    import java.util.*;
    public class BubbleSort {{
        public void performSort(List<Date> input) {{
            File dir = new File("ci_io_test");
            if (!dir.exists()) {{
                dir.mkdirs();
            }}
            for (int i = 0; i < 1000; i++) {{
                File file = new File(dir, "file_" + i + ".txt");
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {{
                    for (int j = 0; j < 1000; j++) {{
                        writer.write("This is line " + j + " of file " + i + "\\n");
                    }}
                }} catch (IOException e) {{
                    e.printStackTrace();
                }}
            }}
        }}
    }}
    """

SORT_STRATEGY_JAVA = """
    package {0};

    import java.util.Date;
    import java.util.List;
    
    public interface SortStrategy {{
        void performSort(List<Date> input);
    }}
"""

BUBBLE_SORT_JAVA_INCORRECT = """
    package {0};
    import java.util.*;
    public class BubbleSort {{
        public void performSort(final List<Date> input) {{
            return;
        }}
    }}
    """

BUBBLE_SORT_JAVA_INFINITE_LOOP = """
    package {0};
    import java.util.*;
    public class BubbleSort {{
        public void performSort(final List<Date> input) {{
            while (true) {{
                // Infinite loop
            }}
        }}
    }}
    """

BUBBLE_SORT_JAVA_ALLOCATE_MEMORY = """
    package {0};
    import java.util.*;
    public class BubbleSort {{
        private List<byte[]> memoryHog = new ArrayList<>();

        public void performSort(final List<Date> input) {{
            while (true) {{
                memoryHog.add(new byte[10 * 1024 * 1024]); // 10 MB chunks
            }}
        }}
    }}
    """

C_FACT_BUILD_SCRIPT = """#!/usr/bin/env bash
    set -e
    export AEOLUS_INITIAL_DIRECTORY=${PWD}
    setup_the_build_environment () {
    echo '⚙️ executing setup_the_build_environment'
    #!/usr/bin/env bash
    # ------------------------------
    # Task Description:
    # Build and run all tests
    # ------------------------------
    # Update ownership to avoid permission issues
    sudo chown artemis_user:artemis_user .
    # Update ownership in assignment and test-reports
    sudo chown artemis_user:artemis_user assignment/ -R || true
    sudo mkdir test-reports
    sudo chown artemis_user:artemis_user test-reports/ -R || true
    }

    build_and_run_all_tests () {
    echo '⚙️ executing build_and_run_all_tests'
    #!/usr/bin/env bash
    # ------------------------------
    # Task Description:
    # Build and run all tests
    # ------------------------------

    rm -f assignment/GNUmakefile
    rm -f assignment/Makefile
    cp -f tests/Makefile assignment/Makefile || exit 2
    cd tests
    python3 Tests.py
    rm Tests.py
    rm -rf ./tests || true
    }

    main () {
    if [[ "${1}" == "aeolus_sourcing" ]]; then
        return 0 # just source to use the methods in the subshell, no execution
    fi
    local _script_name
    _script_name=${BASH_SOURCE[0]:-$0}
    cd "${AEOLUS_INITIAL_DIRECTORY}"
    bash -c "source ${_script_name} aeolus_sourcing; setup_the_build_environment"
    cd "${AEOLUS_INITIAL_DIRECTORY}"
    bash -c "source ${_script_name} aeolus_sourcing; build_and_run_all_tests"
    }

    main "${@}"
    """

MEMORY_ALLOCATE_BUILD_SCRIPT = """#!/usr/bin/env bash
    set -e
    main () {
        while true; do
            echo "📣 Allocating memory..."
            allocate_memory
            sleep 1
        done
    }

    allocate_memory () {
        local allocated=0
        while [[ $allocated -lt 100 ]]; do
            echo "📦 Allocating 1MB..."
            dd if=/dev/zero of=/dev/null bs=1M count=1 &
            allocated=$((allocated + 1))
        done
    }

    main "${@}"
    """

C_CORRECT = """
    #include <stdio.h>
    #include <stdlib.h>
    int main(void) {
        int x = 6;
        int y = 10;
        printf("%d\\n", x * x + 5 * y - 4);
        return EXIT_SUCCESS;
    }
""" 

START_ARTEMIS_COMMAND = "sudo systemctl start artemis.service"
STOP_ARTEMIS_COMMAND = "sudo systemctl stop artemis.service"

START_DOCKER_COMMAND = "sudo systemctl start docker.service"
STOP_DOCKER_SOCKET_COMMAND = "sudo systemctl stop docker.socket"
STOP_DOCKER_SERVICE_COMMAND = "sudo systemctl stop docker.service"