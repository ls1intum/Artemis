import os
import json
import time


def setup_git(token):
    # Set git credentials
    os.environ['GIT_ASKPASS'] = 'echo'
    os.environ['GIT_USERNAME'] = 'github-actions[bot]'
    os.environ['GIT_PASSWORD'] = token
    os.environ['GIT_EMAIL'] = 'github-actions[bot]@users.noreply.github.com'
    os.environ['GIT_COMMITTER_NAME'] = 'github-actions[bot]'
    os.environ['GIT_COMMITTER_EMAIL'] = 'github-actions[bot]@users.noreply.github.com'


def setup_branch(org, repo, branch):
    if os.path.isdir(repo):
        os.system(f"rm -rf {repo}")

    # Clear repo directory except .git folder
    os.system(
        f"git clone --depth 1 -b {branch} https://{os.environ['GIT_PASSWORD']}@github.com/{org}/{repo} && "
        f"cd {repo} && "
        "find . -not -path './.git/*' -delete"
    )
    # Create empty shield.json
    os.system(
        f"cd {repo} && "
        "echo '{}' > shields.json"
    )
    # Create branch, commit and push
    os.system(
        f"cd {repo} && "
        f"git checkout --orphan {branch} && "
        f"git add . && "
        f"git commit --author='github-actions[bot] <github-actions[bot]@users.noreply.github.com>' -m 'Create {branch}' && "
        f"git push --quiet origin {branch}"
    )


# Pull branch from GitHub repository using git command
def pull_branch(org, repo, branch):
    # Remove repository if exists
    if os.path.isdir(repo):
        os.system(f"rm -rf {repo}")

    # Clone repository
    code = os.system(
        f"git clone --depth 1 -b {branch} https://{os.environ['GIT_PASSWORD']}@github.com/{org}/{repo} && "
        f"cd {repo} && "
        f"git pull origin {branch}"
    )

    if code == 256:
        # Branch does not exist
        setup_branch(org, repo, branch)
    return code == 0 or code == 256


# Push branch to GitHub repository using git command
def commit_and_push_branch(repo, branch):
    author_name = os.environ['GIT_COMMITTER_NAME']
    author_email = os.environ['GIT_COMMITTER_EMAIL']
    code = os.system(
        f"cd {repo} && "
        f"git add . && "
        f"git commit --author='{author_name} <{author_email}>' -m 'Update {branch}' && "
        f"git push --quiet origin {branch}"
    )
    return code == 0 or code == 256


def remove_repo(repo):
    code = os.system(
        f"rm -rf {repo}"
    )
    return code == 0


def read_badges_json(repo, file_name="shields.json"):
    with open(f"{repo}/{file_name}") as f:
        return json.load(f)


def write_badges_json(badges, repo, file_name="shields.json"):
    with open(f"{repo}/{file_name}", "w") as f:
        json.dump(badges, f, indent=4)


def set_badge_values(repo, badge_name, badge_status, badge_color, lifetime=None):
    badges = read_badges_json(repo)
    if badge_name not in badges:
        badges[badge_name] = {}
    badges[badge_name]["status"] = badge_status
    badges[badge_name]["color"] = badge_color
    badges[badge_name]["timestamp"] = int(time.time())
    badges[badge_name]["lifetime"] = lifetime
    write_badges_json(badges, repo)


def set_badge(token, org, repo, badge_name, badge_status, badge_color, branch="shields"):
    setup_git(token)
    pull_branch(org, repo, branch)
    set_badge_values(repo, badge_name, badge_status, badge_color)
    commit_and_push_branch(repo, branch)
    remove_repo(repo)


def get_badge_status(token, org, repo, badge_name, branch="shields"):
    setup_git(token)
    pull_branch(org, repo, branch)
    badges = read_badges_json(repo)
    remove_repo(repo)
    if badge_name not in badges:
        return True
    current_time = int(time.time())
    badge_status = badges[badge_name]["status"]
    badge_color = badges[badge_name]["color"]
    badge_time = badges[badge_name]["timestamp"]
    badge_lifetime = badges[badge_name]["lifetime"]
    if (badge_time is None or badge_lifetime is None) and badge_color == "red":
        return False
    return current_time - badge_time > badge_lifetime or badge_color != "red", badge_status


def unlock_badges(token, org, repo, branch="shields"):
    setup_git(token)
    pull_branch(org, repo, branch)
    badges = read_badges_json(repo)
    current_time = int(time.time())
    for badge_name in badges:
        badge_time = badges[badge_name]["timestamp"]
        badge_lifetime = badges[badge_name]["lifetime"]
        if badge_lifetime is None:
            continue
        if current_time - badge_time > badge_lifetime:
            badges[badge_name]["color"] = "green"
    write_badges_json(badges, repo)
