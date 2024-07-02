Migrations
==========

From Jenkins and GitLab to Jenkins and LocalVC
----------------------------------------------

Here you can find the steps to migrate from a :ref:`GitLab and Jenkins <Jenkins and GitLab Setup>`
to a Jenkins and LocalVC Setup.

0. Your Artemis is configured to run with Jenkins and GitLab and is currently not running.
1. Make a Backup of your Jenkins and Artemis data.
2. Adjust parameters in a loaded `application-*.yml`:

   1. Set in ``artemis.version-control.local-vcs-repo-path`` the path where LocalVC should store the repositories.
   2. Adjust the scaling parameters under ``migration.scaling`` if you prefer other values then the defaults.
      The migrations consist of two sequential steps (GitLab and Jenkins).
      In each of them several threads process the repositories. Each batch is processed by one thread.

      1. ``batch-size`` (default 10): Number of repositories that are loaded in one batch.
      2. ``max-thread-count`` (default 4): Maximal number of concurrently running threads for the migration.
      3. ``timeout-in-hours`` (default 48): The timeout value for each step of the migration.
      4. ``estimated-time-per-repository`` (default 2): Time in seconds,
         which is used to give an overall estimation for one step and to log an estimated percentage progress.
3. Start Artemis with the additional profile ``migrate-gitlab-jenkins-to-localvc`` to perform the migration.
   After it Artemis stops automatically (with a ``Application run failed`` message).
   Check the log of Artemis for entries with ``Migration`` to see if there were any problems.
4. Adjust the credential on your Jenkins, which was previously used to access your GitLab.
   The same credential ID now have to provide username and password of an administrator account of your Artemis instance.
5. Change your Artemis configuration to run with LocalVC instead of GitLab.

   1. Adjust ``artemis.version-control.url`` to point to your Artemis instance instead of your GitLab instance.
   2. ...
6. Now you can start your Artemis again.
   Make sure to remove the migration profile and replace the ``gitlab`` profile with the ``localvc`` profile.
