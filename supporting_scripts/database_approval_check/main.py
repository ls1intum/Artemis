import os
import sys

from github import Auth, Github
from github.Commit import Commit
from github.GithubException import BadCredentialsException
from github.GithubObject import _ValuedAttribute
from github.NamedUser import NamedUser

MIGRATION_DIRECTORY = "src/main/resources/config/liquibase"
DATABASE_MAINTAINER_TEAM_SLUG = "database-maintainers"

token = os.environ.get("GITHUB_TOKEN")
pr_number = os.environ.get("GITHUB_PR")

if not token:
    print("Environment variable GITHUB_TOKEN is missing.")
    sys.exit(1)

if not pr_number:
    print("Environment variable GITHUB_PR is missing.")
    sys.exit(1)

try:
    gh = Github(auth=Auth.Token(token))
    orga = gh.get_organization("ls1intum")
except BadCredentialsException:
    print("Authentication with GitHub failed. Check GITHUB_TOKEN.")
    sys.exit(1)

repo = orga.get_repo("Artemis")

try:
    pr = repo.get_pull(int(pr_number))
except ValueError:
    print(f"Invalid PR number: {pr_number}")
    sys.exit(1)

branch = pr.head.ref

db_team = orga.get_team_by_slug(DATABASE_MAINTAINER_TEAM_SLUG)
db_team_members = [user for user in db_team.get_members()]


def has_db_changes(commit: Commit):
    print(f"Checking commit {commit.sha} for database changes...")
    for file in commit.files:
        if file.filename.startswith(MIGRATION_DIRECTORY):
            return True
    return False


def is_db_maintainer(user: NamedUser):
    return user in db_team.get_members()


# Check if the PR has any changes to the database folder at all, before checking commits
if not any(
    file.filename.startswith(MIGRATION_DIRECTORY) for file in pr.get_files()
):
    print(f"PR #{pr_number} has no changes in the database folder.")
    sys.exit(0)


last_commit_with_db_changes = None
for commit in pr.get_commits().reversed:
    # Ignore merges from the develop branch (local and remote-tracking)
    if (
        commit.commit.message.startswith("Merge branch 'develop' into")
        or commit.commit.message.startswith("Merge branch 'origin/develop' into")
        or commit.commit.message.startswith("Merge remote-tracking branch 'origin/develop' into")
    ):
        continue
    if has_db_changes(commit):
        last_commit_with_db_changes = commit
        print(f"Last commit with database changes: {last_commit_with_db_changes.sha}")
        break
else:
    print("No commits with database changes found.")
    last_commit_with_db_changes = None
    sys.exit(0)


reviews = [review for review in pr.get_reviews()]
events = pr.get_issue_events()

for event in events:
    if event.event == "review_dismissed":
        for review in reviews:
            if review.id == event.dismissed_review["review_id"]:
                review._state = _ValuedAttribute(event.dismissed_review["state"].upper())

approvals = [review for review in reviews if review.state == "APPROVED"]
approvals_from_db_maintainers = [
    approval for approval in approvals if is_db_maintainer(approval.user)
]

# Check if a db maintainer has approved after the last commit with db changes
last_commit_date = last_commit_with_db_changes.commit.author.date

for approval in approvals_from_db_maintainers:
    if approval.submitted_at > last_commit_date:
        print(
            f"PR #{pr_number} has been approved by a database maintainer ({approval.user.name}) "
            f"after the last commit with database changes."
        )
        sys.exit(0)

print(
    f"PR #{pr_number} has not been approved by a database maintainer after the last commit with database changes."
)
sys.exit(1)
