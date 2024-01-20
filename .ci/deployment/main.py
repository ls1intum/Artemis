import os
import sys

from badges import set_badge, get_badge_status
import github_api


def fail(pr, exit_code, message):
    print(f"::error::{message}", file=sys.stderr)
    if pr:
        print(f"Creating comment on PR {pr}")
        github_api.create_comment(pr, f"#### ⚠️ Unable to deploy to environment ⚠️\n{message}")
    sys.exit(exit_code)


def setup_ssh():
    ssh_auth_sock = os.getenv("SSH_AUTH_SOCK")
    gateway_ssh_key = os.getenv("GATEWAY_SSH_KEY")
    deployment_ssh_key = os.getenv("DEPLOYMENT_SSH_KEY")
    gateway_host_public_key = os.getenv("GATEWAY_HOST_PUBLIC_KEY")
    deployment_host_public_keys = os.getenv("DEPLOYMENT_HOST_PUBLIC_KEYS")

    os.system(
        f"mkdir -p ~/.ssh && "
        f"ssh-agent -a {ssh_auth_sock} > /dev/null && "
        f"ssh-add - <<< {gateway_ssh_key} && "
        f"ssh-add - <<< {deployment_ssh_key} && "
        f"cat - <<< {gateway_host_public_key} >> ~/.ssh/known_hosts && "
        f"cat - <<< $(sed 's/\\n/\n/g' <<< \"{deployment_host_public_keys}\") >> ~/.ssh/known_hosts"
    )


def check_lock_status(pr, label):
    issues = github_api.get_issues_with_label(f"lock:{label}")
    if issues.__len__() == 1 and issues[0]["number"] != pr:
        fail(pr, 400, f"Environment {label} is already in use by PR #{issues[0].number}.")
    elif issues.__len__() > 1:
        fail(pr, 400, f'Environment {label} is already in use by multiple PRs. Check PRs with label "lock:{label}"!')


def deploy(ref):
    setup_ssh()
    deployment_hosts = os.getenv("DEPLOYMENT_HOSTS").split(" ")
    deployment_user = os.getenv("DEPLOYMENT_USER")
    gateway_user = os.getenv("GATEWAY_USER")
    gateway_host = os.getenv("GATEWAY_HOST")
    deployment_folder = os.getenv("DEPLOYMENT_FOLDER")
    script_base = os.getenv("SCRIPT_PATH", "./artemis-server-cli docker-deploy")

    tag = os.getenv("TAG")

    for host in deployment_hosts:
        code = os.system(f"{script_base} \"{deployment_user}@{host}\" -g \"{gateway_user}@{gateway_host}\" -t {tag} -b {ref} -d {deployment_folder} -y")
        if code != 0:
            fail(os.getenv("PR"), 500, f"Deployment to {host} failed during ssh script execution. Check the logs for more information.")


def __main__():
    print("Reading environment variables")
    # Get github token from environment variable
    github_token = os.getenv("GITHUB_TOKEN")
    # Get org github token from environment variable
    github_org_token = os.getenv("GITHUB_ORG_TOKEN", github_token)
    # Get user from environment variable
    user = os.getenv("GITHUB_USER")
    # Get org from environment variable
    org = os.getenv("ORG", "ls1intum")
    # Get repo from environment variable
    repo = os.getenv("REPO", "Artemis")
    # Get group from environment variable
    group = os.getenv("GROUP", "artemis-developers")
    # Get ref from environment variable
    ref = os.getenv("REF", "develop")
    # Get PR from environment variable
    pr = os.getenv("PR", None)
    # Get test server from environment variable
    label = os.getenv("LABEL")
    # Get badge from environment variable
    badge = os.getenv("BADGE", label)

    # Get environment url from env
    environment_url = os.getenv("ENVIRONMENT_URL", f"https://{label}.artemis.cit.tum.de")
    # Get environment name from env
    environment_name = os.getenv("ENVIRONMENT_NAME", environment_url)
    # Get environment management from env
    manage_environment = os.getenv("MANAGE_ENVIRONMENT", "false").lower() == "true"

    github_api.GITHUB_TOKEN = github_token
    github_api.GITHUB_ORG_TOKEN = github_org_token
    github_api.org = org
    github_api.repo = repo

    # Attempt to get PR from ref
    if not pr:
        pr = github_api.get_pr_for_ref(ref)

    # Remove deployment label from PR
    if pr:
        print(f"Removing label {label} from PR {pr} (if exists)")
        github_api.remove_label(pr, f"deploy:{label}")

    print(f"Checking if user {user} is in GitHub group {group}")
    # Check if user is in GitHub group
    if not github_api.is_user_in_github_group(user, group):
        fail(pr, 403, f"User {user} does not have access to deploy to {label}.")

    print(f"Setting sha for ref {ref}")
    # Get sha if ref is not a sha
    sha = github_api.get_sha(ref)
    print("SHA:", sha)
    print(f"Checking if build job for sha ran successfully")
    # Check that build job ran successfully
    if not github_api.check_build_job(sha):
        fail(pr, 400, f"The docker build needs to run through before deploying.")

    print(f"Checking if environment {label} is available")
    check_lock_status(pr, label)

    available, badge_status = get_badge_status(github_token, org, repo, badge)
    if not available:
        fail(pr, 400, f"Environment {label} is already in use by ´{badge_status}´.")

    if manage_environment:
        print(f"Creating deployment for {environment_name}")
        deployment = github_api.create_deployment(environment_name, sha)
        print(f"Creating deployment status for {environment_name}")
        github_api.create_deployment_status(deployment["id"], environment_url, github_api.DeploymentStatus.IN_PROGRESS)

    try:
        print("Deploying")
        deploy(ref)

        print(f"Deployment to {label} successful.")
        if manage_environment:
            print(f"Updating deployment status for {environment_name}")
            github_api.create_deployment_status(deployment["id"], environment_url, github_api.DeploymentStatus.SUCCESS)
        if pr:
            print(f"Adding label {label} to PR {pr}")
            github_api.add_label(pr, f"lock:{label}")
        print(f"Setting badge {badge} to {ref} (red)")
        set_badge(github_token, org, repo, badge, ref, "red")
    except Exception as e:
        print(e, file=sys.stderr)
        if manage_environment:
            print(f"Updating deployment status for {environment_name}")
            github_api.create_deployment_status(deployment["id"], environment_url, github_api.DeploymentStatus.ERROR)
        fail(pr, 500, f"Deployment to {label} failed for an unknown reason. Please check the logs.")


__main__()
