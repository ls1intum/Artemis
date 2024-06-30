Migrations
==========

From Jenkins and GitLab to Jenkins and LocalVC
----------------------------------------------

Here you can found the steps to migrate from a :ref:`GitLab and Jenkins <Jenkins and GitLab Setup>`
to a Jenkins and LocalVC Setup.

0. Your Artemis is configured to run with Jenkins and GitLab
1. Make a Backup of your Jenkins and Artemis data
2. Adjust the scaling parameter in `application-*.yml` if necessary:

   1. ...

3. Start Artemis with the additional profile `migrate-gitlab-jenkins-to-localvc` to perform the migration ...
4. Change the credentials on your Jenkins for git access ...
