from enum import Enum

import requests

GITHUB_TOKEN = None
GITHUB_ORG_TOKEN = None
org = None
repo = None


class DeploymentStatus(Enum):
    ERROR = "error"
    FAILURE = "failure"
    INACTIVE = "inactive"
    IN_PROGRESS = "in_progress"
    QUEUED = "queued"
    PENDING = "pending"
    SUCCESS = "success"


# Check if user is in GitHub group using GitHub API
def is_user_in_github_group(user, group):
    url = f"https://api.github.com/orgs/{org}/teams/{group}/memberships/{user}"
    headers = {"Authorization": f"token {GITHUB_ORG_TOKEN}"}
    response = requests.get(url, headers=headers)
    return response.status_code == 200


# List all teams in GitHub organization
def list_groups():
    url = f"https://api.github.com/orgs/{org}/teams"
    headers = {"Authorization": f"token {GITHUB_ORG_TOKEN}"}
    response = requests.get(url, headers=headers)
    return response.json()


# List all environments in GitHub repository
def list_environments():
    url = f"https://api.github.com/repos/{org}/{repo}/environments"
    headers = {"Authorization": f"token {GITHUB_TOKEN}"}
    response = requests.get(url, headers=headers)
    return response.json()


# Get environment by name
def get_environment(name):
    url = f"https://api.github.com/repos/{org}/{repo}/environments/{name}"
    headers = {"Authorization": f"token {GITHUB_TOKEN}"}
    response = requests.get(url, headers=headers)
    return response.json()


# List all deployments in GitHub repository
def list_deployments():
    url = f"https://api.github.com/repos/{org}/{repo}/deployments"
    headers = {"Authorization": f"token {GITHUB_TOKEN}"}
    response = requests.get(url, headers=headers)
    return response.json()


# List deployments for environment
def list_deployments_for_environment(environment):
    url = f"https://api.github.com/repos/{org}/{repo}/deployments?environment={environment}"
    headers = {"Authorization": f"token {GITHUB_TOKEN}"}
    response = requests.get(url, headers=headers)
    return response.json()


# Get deployment by id
def get_deployment(id):
    url = f"https://api.github.com/repos/{org}/{repo}/deployments/{id}"
    headers = {"Authorization": f"token {GITHUB_TOKEN}"}
    response = requests.get(url, headers=headers)
    return response.json()


# Get sha for branch
def get_sha_for_branch(branch):
    url = f"https://api.github.com/repos/{org}/{repo}/branches/{branch}"
    headers = {"Authorization": f"token {GITHUB_TOKEN}"}
    response = requests.get(url, headers=headers)
    if response.status_code == 200:
        return response.json()["commit"]["sha"]
    elif response.status_code == 404:
        return None


# Get sha for tag
def get_sha_for_tag(tag):
    url = f"https://api.github.com/repos/{org}/{repo}/git/ref/tags/{tag}"
    headers = {"Authorization": f"token {GITHUB_TOKEN}"}
    response = requests.get(url, headers=headers)
    if response.status_code == 200:
        return response.json()["object"]["sha"]
    elif response.status_code == 404:
        return None


# Get sha for ref
def get_sha_for_ref(ref):
    url = f"https://api.github.com/repos/{org}/{repo}/git/ref/{ref}"
    headers = {"Authorization": f"token {GITHUB_TOKEN}"}
    response = requests.get(url, headers=headers)
    if response.status_code == 200:
        return response.json()["object"]["sha"]
    elif response.status_code == 404:
        return None


# Get sha for branch/tag/ref
def get_sha(ref):
    sha = get_sha_for_branch(ref)
    if sha is None:
        sha = get_sha_for_tag(ref)
    if sha is None:
        sha = get_sha_for_ref(ref)
    if sha is None:
        sha = ref
    return sha


# Create deployment for environment
def create_deployment(environment, branch):
    url = f"https://api.github.com/repos/{org}/{repo}/deployments"
    headers = {"Authorization": f"token {GITHUB_TOKEN}"}
    data = {
        "ref": branch,
        "sha": get_sha_for_branch(branch),
        "environment": environment,
        "required_contexts": [],
        "auto_merge": False,
        "transient_environment": False,
        "production_environment": False,
    }
    response = requests.post(url, headers=headers, json=data)
    return response.json()


# Create deployment status for deployment
def create_deployment_status(deployment, environment_url, state: DeploymentStatus, description=""):
    url = f"https://api.github.com/repos/{org}/{repo}/deployments/{deployment}/statuses"
    headers = {"Authorization": f"token {GITHUB_TOKEN}"}
    data = {
        "state": state.value,
        "description": description,
        "environment_url": environment_url,
        "auto_inactive": False,
    }
    response = requests.post(url, headers=headers, json=data)
    return response.json()


# Create a comment on a pull request
def create_comment(pr, comment):
    url = f"https://api.github.com/repos/{org}/{repo}/issues/{pr}/comments"
    headers = {"Authorization": f"token {GITHUB_TOKEN}"}
    data = {"body": comment}
    response = requests.post(url, headers=headers, json=data)
    return response.json()


def add_label(pr, label):
    url = f"https://api.github.com/repos/{org}/{repo}/issues/{pr}/labels"
    headers = {"Authorization": f"token {GITHUB_TOKEN}"}
    data = [label]
    response = requests.post(url, headers=headers, json=data)
    return response.json()


def remove_label(pr, label):
    url = f"https://api.github.com/repos/{org}/{repo}/issues/{pr}/labels/{label}"
    headers = {"Authorization": f"token {GITHUB_TOKEN}"}
    response = requests.delete(url, headers=headers)
    return response.json()


def check_build_job(sha, workflow="build.yml"):
    url = f"https://api.github.com/repos/{org}/{repo}/actions/workflows/{workflow}/runs?status=success&head_sha={sha}"
    headers = {"Authorization": f"token {GITHUB_TOKEN}"}
    response = requests.get(url, headers=headers)
    return response.json()["total_count"] > 0


# const opts = github.rest.issues.listForRepo.endpoint.merge({
#   owner: context.repo.owner,
#   repo: context.repo.repo,
#   labels: ['lock:${{ matrix.label-identifier }}']
# })
def get_issues_with_label(label):
    url = f"https://api.github.com/repos/{org}/{repo}/issues?labels={label}"
    headers = {"Authorization": f"token {GITHUB_TOKEN}"}
    response = requests.get(url, headers=headers)
    return response.json()


def get_pr_for_ref(ref):
    url = f"https://api.github.com/repos/{org}/{repo}/commits/{ref}/pulls"
    headers = {"Authorization": f"token {GITHUB_TOKEN}"}
    response = requests.get(url, headers=headers)
    if response.status_code == 200:
        return response.json()[0]["number"]
    elif response.status_code == 404:
        return None
