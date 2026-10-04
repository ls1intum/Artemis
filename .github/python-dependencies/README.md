# Deployment Python dependencies

`prod-like-deployment.yml` installs this complete hash lock with Python 3.14. The input is based on
the Artemis-Ansible snapshot recorded in `requirements.in`, with HTTP dependency security fixes.
The workflow checks out this lock from its own ref into a separate directory, leaving the
Artemis-Ansible checkout and Ansible Galaxy requirement paths intact.

When upstream Python requirements change, review and update `requirements.in`, then run
`supporting_scripts/update_python_dependency_locks.sh` from the repository root. Commit both files.
See the developer site's Builds and Dependency Management page for the full procedure.
