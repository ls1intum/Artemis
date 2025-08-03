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

SPAMMY_BUILD_SCRIPT = """#!/usr/bin/env bash
        set -e
        main () {
            while true; do
            for i in {1..1000}; do
                echo "📣 Log line $i: This is a test log message meant to spam the output."
            done
            echo "🔁 Completed 1000 log lines. Restarting..."
            sleep 0.1
            done
        }
        main "${@}"
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
                System.out.println("Allocated another 10MB");
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
