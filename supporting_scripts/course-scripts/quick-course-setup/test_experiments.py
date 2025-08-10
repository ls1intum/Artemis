import configparser
import pytest
from logging_config import logging

from experiment_config import C_WITH_NODE_FAILURE, DOCKER_CLIENT_FAILURE_JAVA, JAVA_HAPPY_PATH, JAVA_SPAMMY_BUILD, JAVA_TIMEOUT_BUILD, JAVA_FAILING_BUILD, C_HAPPY_PATH, JAVA_ALLOCATE_MEMORY, ALLOCATE_MEMORY_IN_BUILD_PLAN, DOCKER_CLIENT_FAILURE, ExperimentConfig
from experiment_runner import ExperimentRunner
import urllib3

config = configparser.ConfigParser()
config.read("config.ini")

EXPERIMENT_TARGET_NODE = config.get("Settings", "experiment_target_node")
USER_NAME_SSH = config.get("Settings", "user_name_ssh")

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

def assertStatsForPassingJobs(stats, number_of_students, number_of_commits):
    assert stats is not None, "Should have build statistics"
    assert stats.get('totalBuilds', 0) == number_of_students * number_of_commits + 2, "Number of build jobs should match the number of student commits +2 for solution and template"
    assert stats.get('successfulBuilds', 0) == number_of_students * number_of_commits + 2, "All build jobs should be successful"
    assert stats.get('missingBuilds', 0) == 0, "There should be no missing builds"

@pytest.fixture
def experiment_runner():
    """Fixture to provide an ExperimentRunner instance with automatic cleanup."""
    with ExperimentRunner() as runner:
        yield runner

def test_java_failing_builds_medium_load(experiment_runner: ExperimentRunner):
    number_of_students = 100
    number_of_commits = 1
    
    logging.info(f"Running Java failing build test with {number_of_students} students")

    experiment_runner.setup_experiment(JAVA_FAILING_BUILD, number_of_students)
    results, stats = experiment_runner.run_experiment(JAVA_FAILING_BUILD, number_of_commits)
    
    assert len(results) == number_of_students * number_of_commits, "All submissions should have results"
    for result in results:
        assert result.get('rated') is True, "All results should be rated"
        assert result.get('successful') is False, "All results should be unsuccessful"
        assert result.get('score') == 0, "All results should have a score of 0"
    # a failure in build script does not count failed
    # assert stats.get('failedBuilds', 0) == number_of_students * number_of_commits, "All build jobs should have failed"

def test_timeout_builds(experiment_runner: ExperimentRunner):
    number_of_students = 30
    number_of_commits = 1
    experiment_runner.setup_experiment(JAVA_TIMEOUT_BUILD, number_of_students)
    stats = experiment_runner.run_experiment(JAVA_TIMEOUT_BUILD, number_of_commits)
    assert stats.get('timeOutBuilds', 0) == number_of_students * number_of_commits, "All build jobs should have timed out"


def test_java_happy_path_medium_load(experiment_runner: ExperimentRunner):
    """Test Java happy path with medium load."""
    number_of_students = 250
    number_of_commits = 1
    course = experiment_runner.setup_experiment(JAVA_HAPPY_PATH, number_of_students)
    results, stats = experiment_runner.run_experiment(JAVA_HAPPY_PATH, number_of_commits)
    
    assert len(results) == number_of_students * number_of_commits, "All submissions should have results"
    for result in results:
        assert result.get('rated') is True, "All results should be rated"
        assert result.get('score') > 0, "All results should have a positive partial score"

    assertStatsForPassingJobs(stats, number_of_students, number_of_commits)

def test_c_happy_path_high_load(experiment_runner: ExperimentRunner):
    """Test C happy path with medium load."""
    number_of_students = 500
    number_of_commits = 1
    experiment_runner.setup_experiment(C_HAPPY_PATH, number_of_students)
    results, stats = experiment_runner.run_experiment(C_HAPPY_PATH, number_of_commits)

    assert len(results) == number_of_students * number_of_commits, "All submissions should have results"
    for result in results:
        assert result.get('rated') is True, "All results should be rated"
        assert result.get('score') > 0, "All results should have a positive partial score"
        assert result.get('successful') is True, "All result have full score"
    assertStatsForPassingJobs(stats, number_of_students, number_of_commits)

def test_memory_hog(experiment_runner: ExperimentRunner):
    number_of_students = 500
    number_of_commits = 1
    experiment_runner.setup_experiment(JAVA_ALLOCATE_MEMORY, number_of_students)
    results, stats = experiment_runner.run_experiment(JAVA_ALLOCATE_MEMORY, number_of_commits)

    assert len(results) == number_of_students * number_of_commits, "All submissions should have results"
    assertStatsForPassingJobs(stats, number_of_students, number_of_commits)

def test_memory_hog_build_plan(experiment_runner: ExperimentRunner):
    number_of_students = 100
    number_of_commits = 1
    experiment_runner.setup_experiment(ALLOCATE_MEMORY_IN_BUILD_PLAN, number_of_students)
    results, stats = experiment_runner.run_experiment(ALLOCATE_MEMORY_IN_BUILD_PLAN, number_of_commits)

    assert len(results) == number_of_students * number_of_commits, "All submissions should have results"
    assertStatsForPassingJobs(stats, number_of_students, number_of_commits)

def test_build_spam_log(experiment_runner: ExperimentRunner):
    number_of_students = 500
    number_of_commits = 1
    logging.info(f"Running Java spammy log test with {number_of_students} students")
    
    experiment_runner.setup_experiment(JAVA_SPAMMY_BUILD, number_of_students)
    results, stats = experiment_runner.run_experiment(JAVA_SPAMMY_BUILD, number_of_commits)
    assertStatsForPassingJobs(stats, number_of_students, number_of_commits)


def test_agent_node_failure(experiment_runner: ExperimentRunner):
    number_of_students = 500
    number_of_commits = 1
    logging.info(f"Running Node failure experiment with {number_of_students} students. Will fail {EXPERIMENT_TARGET_NODE}")

    experiment_runner.setup_experiment(C_WITH_NODE_FAILURE, number_of_students)
    results, stats = experiment_runner.run_experiment(C_WITH_NODE_FAILURE, number_of_commits, f"{USER_NAME_SSH}@{EXPERIMENT_TARGET_NODE}")
    assert stats is not None, "Should have build statistics"
    assert stats.get('totalBuilds', 0) == number_of_students * number_of_commits + 2, "Number of build jobs should match the number of student commits +2 for solution and template"

def test_docker_client_agent_failure(experiment_runner: ExperimentRunner):
    number_of_students = 500
    number_of_commits = 1
    logging.info(f"Running Docker client failure experiment with {number_of_students} students. Will fail docker on {EXPERIMENT_TARGET_NODE}")

    experiment_runner.setup_experiment(DOCKER_CLIENT_FAILURE, number_of_students)
    results, stats = experiment_runner.run_experiment(DOCKER_CLIENT_FAILURE, number_of_commits, f"{USER_NAME_SSH}@{EXPERIMENT_TARGET_NODE}")
    assert stats is not None, "Should have build statistics"
    assert stats.get('totalBuilds', 0) == number_of_students * number_of_commits + 2, "Number of build jobs should match the number of student commits +2 for solution and template"


def test_docker_client_agent_failure_java(experiment_runner: ExperimentRunner):
    number_of_students = 250
    number_of_commits = 1
    logging.info(f"Running Docker client failure experiment with {number_of_students} students. Will fail docker on {EXPERIMENT_TARGET_NODE}")

    experiment_runner.setup_experiment(DOCKER_CLIENT_FAILURE_JAVA, number_of_students)
    results, stats = experiment_runner.run_experiment(DOCKER_CLIENT_FAILURE_JAVA, number_of_commits, f"{USER_NAME_SSH}@{EXPERIMENT_TARGET_NODE}")
    assert stats is not None, "Should have build statistics"
    assert stats.get('totalBuilds', 0) == number_of_students * number_of_commits + 2, "Number of build jobs should match the number of student commits +2 for solution and template"
