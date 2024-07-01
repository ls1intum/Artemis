Migrations
==========

From Jenkins and GitLab to Jenkins and LocalVC
----------------------------------------------

Here you can find the steps to migrate from a :ref:`GitLab and Jenkins <Jenkins and GitLab Setup>`
to a Jenkins and LocalVC Setup.

0. Your Artemis is configured to run with Jenkins and GitLab.
1. Make a Backup of your Jenkins and Artemis data.
2. Adjust parameters in `application-*.yml`:

   1. Set in ``artemis.version-control.local-vcs-repo-path`` the path where LocalVC should store the repositories.
   2. Adjust scaling parameter if necessary ...

3. Start Artemis with the additional profile ``migrate-gitlab-jenkins-to-localvc`` to perform the migration.
   After it Artemis stops automatically.
   Check the log of Artemis for entries with ``Migration`` to see if there were any problems.
4. Change the credentials on your Jenkins for git access ...
5. Change your Artemis configuration to run with LocalVC instead of GitLab.

   1. Adjust ``artemis.version-control.url`` to point to your Artemis instance instead of your GitLab instance.
   2. ...
