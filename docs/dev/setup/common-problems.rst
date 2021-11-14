Common Setup Problems
===============================================================

General Setup Problems
----------------------

- Restarting IntelliJ with invalidating caches *(File > Invalidate Caches...)* might resolve the current issue.

Database
^^^^^^^^
- On the first startup, there might be issues with the ``text_block`` table.
  You can resolve the issue by executing ``ALTER TABLE text_block CONVERT TO CHARACTER SET utf8 COLLATE utf8_unicode_ci;`` in your database.
- One typical problem in the development setup is that an exception occurs during the database initialization. Artemis uses
  `Liquibase <https://www.liquibase.org>`__ to automatically upgrade the database scheme after changes to the data model. This ensures that the
  changes can also be applied to the production server. In case you encounter errors with Liquibase checksum values:

    * Run the following command in your terminal / command line: ``./gradlew liquibaseClearChecksums``
    * You can manually adjust the checksum for a breaking changelog: ``UPDATE `DATABASECHANGELOG` SET `MD5SUM` = NULL WHERE `ID` = '<changelogId>'``

Client
^^^^^^

- If you are using a machine with limited RAM *(e.g. ~8 GB RAM)* you might have issues starting the Artemis Client. You can resolve this by following
  the description in :ref:`UsingTheCommandLine`

Programming Exercise Setup
--------------------------

Atlassian Setup (Bamboo, Bitbucket and Jira)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
- When setting up the Bamboo, Bitbucket, and Jira, at the same time within the same browser, you might receive the message that the Jira token expired.
  You can resolve the issue by using another browser for configuring Jira, as there seems to be a synchronization problem within the browser.
- When create a new programming exercise and receive the error message ``The project <ProgrammingExerciseName> already exists
  in the CI Server. Please choose a different short name!`` and you have double checked that this project does not exist within the CI Server Bamboo,
  you might have to renew the trial licenses for the Atlassian products.

    .. raw:: html

       <details>
       <summary>Update Atlassian Licenses</summary>
       You need to create new Atlassian Licenses, which requires you to retrieve the server id and navigate to the license editing page after
       creating new <a href="https://my.atlassian.com/license/evaluation">trial licenses</a>.
       <ul>
           <li>
            Bamboo: Retrive the Server ID and edit the license in <a href="http://localhost:8085/admin/updateLicense!doDefault.action">License key details</a> <i>(Administration > Licensing)</i>
           </li>
           <li>
            Bitbucket: Retrieve the Server ID and edit the license in <a href="http://localhost:7990/admin/license">License Settings</a> <i>(Administration > Licensing)</i>
           </li>
           <li>
            Jira: Retrieve the <a href="http://localhost:8081/secure/admin/ViewSystemInfo.jspa">Server ID</a> <i>(System > System info)</i> and edit the <b>JIRA Service Desk</b> <i>License key</i>
                  in <a href="http://localhost:8081/plugins/servlet/applications/versions-licenses">Versions & licenses</a>
           </li>
       </ul>
       </details>
