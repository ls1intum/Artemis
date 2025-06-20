import os
import sys

from github import Github
from github.Commit import Commit
from github.NamedUser import NamedUser

MIGRATION_DIRECTORY = "src/main/resources/config/liquibase"
DATABASE_MAINTAINER_TEAM_SLUG = "database-maintainers"

token = os.environ.get("GITHUB_TOKEN")
pr_number = os.environ.get("GITHUB_PR")

gh = Github(token)
orga = gh.get_organization("ls1intum")
repo = orga.get_repo("Artemis")
pr = repo.get_pull(int(pr_number))
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


last_commit_with_db_changes = None
for commit in pr.get_commits().reversed:
    # Ignore merges from the develop branch
    if commit.commit.message.startswith("Merge branch 'develop' into"):
        continue
    if has_db_changes(commit):
        last_commit_with_db_changes = commit
        print(f"Last commit with database changes: {last_commit_with_db_changes.sha}")
        break
else:
    print("No commits with database changes found.")
    last_commit_with_db_changes = None
    sys.exit(0)


reviews = pr.get_reviews()
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
else:
    print(
        f"PR #{pr_number} has not been approved by a database maintainer after the last commit with database changes."
    )
    sys.exit(1)


