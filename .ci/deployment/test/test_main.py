import unittest
from unittest.mock import patch

from src import main


class TestMain(unittest.TestCase):

    @patch('src.github_api.create_comment')
    @patch('sys.exit')
    def test_fail(self, mock_exit, mock_create_comment):
        pr = "your_pr_number"
        exit_code = 1
        message = "Your failure message"

        main.fail(pr, exit_code, message)

        mock_create_comment.assert_called_with(pr, f"#### ⚠️ Unable to deploy to environment ⚠️\n{message}")
        mock_exit.assert_called_once_with(exit_code)

    @patch('os.system')
    @patch.dict('os.environ',
                {"SSH_AUTH_SOCK": "sock", "GATEWAY_SSH_KEY": "gateway-key", "DEPLOYMENT_SSH_KEY": "deployment-key",
                 "GATEWAY_HOST_PUBLIC_KEY": "gateway-public-key",
                 "DEPLOYMENT_HOST_PUBLIC_KEYS": "deployment-public-key1\ndeployment-public-key2\ndeployment-public-key3"})
    def test_setup_ssh(self, mock_system):
        main.setup_ssh()

        mock_system.assert_called_with(
            "mkdir -p ~/.ssh && ssh-agent -a sock > /dev/null && ssh-add - <<< gateway-key && ssh-add - <<< deployment-key && cat - <<< gateway-public-key >> ~/.ssh/known_hosts && cat - <<< $(sed 's/\\n/\n/g' <<< \"deployment-public-key1\ndeployment-public-key2\ndeployment-public-key3\") >> ~/.ssh/known_hosts")

    @patch('src.main.setup_ssh')
    @patch('os.system')
    @patch.dict('os.environ', {"DEPLOYMENT_HOSTS": "host1", "DEPLOYMENT_USER": "user", "GATEWAY_USER": "gateway_user",
                               "GATEWAY_HOST": "gateway_host", "DEPLOYMENT_FOLDER": "/opt/artemis",
                               "SCRIPT_PATH": "./artemis-server-cli docker-deploy", "TAG": "latest"})
    def test_deploy(self, mock_system, mock_setup_ssh):
        ref = "develop"
        mock_system.return_value = 0

        main.deploy(ref)

        mock_setup_ssh.assert_called_once()
        mock_system.assert_called_with(
            f"./artemis-server-cli docker-deploy \"user@host1\" -g \"gateway_user@gateway_host\" -t latest -b develop -d /opt/artemis -y")

    @patch('src.main.fail')
    @patch('src.github_api.get_issues_with_label')
    def test_check_lock_status_success(self, mock_get_issues_with_label, mock_fail):
        pr = "1"
        label = "server1"
        mock_get_issues_with_label.return_value = [{"number": pr}]

        main.check_lock_status(pr, label)

        mock_get_issues_with_label.assert_called_with(f"lock:{label}")
        mock_fail.assert_not_called()

    @patch('src.main.fail')
    @patch('src.github_api.get_issues_with_label')
    def test_check_lock_status_other_pr_locked(self, mock_get_issues_with_label, mock_fail):
        label = "server1"
        mock_get_issues_with_label.return_value = [{"number": "2"}]

        main.check_lock_status("1", label)

        mock_get_issues_with_label.assert_called_with(f"lock:{label}")
        mock_fail.assert_called_with("1", 400, f"Environment {label} is already in use by PR #2.")

    @patch('src.main.fail')
    @patch('src.github_api.get_issues_with_label')
    def test_check_lock_status_multiple_prs_locked(self, mock_get_issues_with_label, mock_fail):
        label = "server1"
        mock_get_issues_with_label.return_value = [{"number": "2"}, {"number": "3"}, {"number": "4"}]

        main.check_lock_status("1", label)

        mock_get_issues_with_label.assert_called_with(f"lock:{label}")
        mock_fail.assert_called_with("1", 400,
                                     f'Environment {label} is already in use by multiple PRs. Check PRs with label "lock:{label}"!')

    @patch.dict('os.environ', {"GITHUB_TOKEN": "token", "GITHUB_USER": "user", "REF": "feature/branch", "PR": "1",
                               "LABEL": "server1"})
    @patch('src.github_api.remove_label')
    @patch('src.github_api.is_user_in_github_group')
    @patch('src.github_api.get_sha')
    @patch('src.github_api.check_build_job')
    @patch('src.main.check_lock_status')
    @patch('src.main.get_badge_status')
    @patch('src.main.deploy')
    @patch('src.github_api.add_label')
    @patch('src.main.set_badge')
    def test_main_success(self, mock_set_badge, mock_add_label, mock_deploy, mock_get_badge_status,
                         mock_check_lock_status, mock_check_build_job, mock_get_sha, mock_is_user_in_github_group,
                         mock_remove_label):
        mock_is_user_in_github_group.return_value = True
        mock_get_sha.return_value = "sha123"
        mock_check_build_job.return_value = True
        mock_get_badge_status.return_value = True, ""

        main.__main__()

        mock_remove_label.assert_called_once_with("1", "deploy:server1")
        mock_is_user_in_github_group.assert_called_once_with("user", "artemis-developers")
        mock_get_sha.assert_called_once_with("feature/branch")
        mock_check_build_job.assert_called_once_with("sha123")
        mock_check_lock_status.assert_called_once_with("1", "server1")
        mock_get_badge_status.assert_called_once_with("token", "ls1intum", "Artemis", "server1")
        mock_deploy.assert_called_once_with("feature/branch")
        mock_add_label.assert_called_once_with("1", "lock:server1")
        mock_set_badge.assert_called_once_with("token", "ls1intum", "Artemis", "server1", "feature/branch", "red")

    @patch.dict('os.environ', {"GITHUB_TOKEN": "token", "GITHUB_USER": "user", "REF": "feature/branch", "PR": "1",
                               "LABEL": "server1"})
    @patch('src.github_api.remove_label')
    @patch('src.github_api.is_user_in_github_group')
    @patch('src.main.fail')
    @patch('src.main.deploy')
    def test_main_user_not_in_group(self, mock_deploy, mock_fail, mock_is_user_in_github_group, mock_remove_label):
        mock_is_user_in_github_group.return_value = False

        main.__main__()

        mock_remove_label.assert_called_once_with("1", "deploy:server1")
        mock_is_user_in_github_group.assert_called_once_with("user", "artemis-developers")
        mock_fail.assert_called_once_with("1", 403, "User user does not have access to deploy to server1.")
        mock_deploy.assert_not_called()

    @patch.dict('os.environ', {"GITHUB_TOKEN": "token", "GITHUB_USER": "user", "REF": "feature/branch", "PR": "1",
                               "LABEL": "server1"})
    @patch('src.github_api.remove_label')
    @patch('src.github_api.is_user_in_github_group')
    @patch('src.github_api.get_sha')
    @patch('src.github_api.check_build_job')
    @patch('src.main.fail')
    @patch('src.main.deploy')
    def test_main_build_not_finished(self, mock_deploy, mock_fail, mock_check_build_job, mock_get_sha, mock_is_user_in_github_group,
                          mock_remove_label):
        mock_is_user_in_github_group.return_value = True
        mock_get_sha.return_value = "sha123"
        mock_check_build_job.return_value = False

        main.__main__()

        mock_remove_label.assert_called_once_with("1", "deploy:server1")
        mock_is_user_in_github_group.assert_called_once_with("user", "artemis-developers")
        mock_get_sha.assert_called_once_with("feature/branch")
        mock_check_build_job.assert_called_once_with("sha123")
        mock_fail.assert_called_once_with("1", 400, "The docker build needs to run through before deploying.")
        mock_deploy.assert_not_called()

    # TODO: Test code after l. 117


if __name__ == '__main__':
    unittest.main()
