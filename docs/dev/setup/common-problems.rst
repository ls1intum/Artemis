Common Setup Problems
---------------------

General Setup Problems
^^^^^^^^^^^^^^^^^^^^^^

- Restarting IntelliJ with invalidated caches *(File > Invalidate Caches...)* might resolve the current issue.
- When facing issues with deep dependencies and changes were made to the ``package.json`` file,
  executing ``npm install --force`` might resolve the issue.
- When encountering a compilation error due to ``invalid source release`` make sure that you have set
  the Java version properly at 3 places

   * File > Project Structure > Project Settings > Project > Project SDK
   * File > Project Structure > Project Settings > Project > Project Language Level
   * File > Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JVM

Database
""""""""
- On the first startup, there might be issues with the ``text_block`` table.
  You can resolve the issue by executing ``ALTER TABLE text_block CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;``
  in your database.
- One typical problem in the development setup is that an exception occurs during the database initialization.
  Artemis uses `Liquibase <https://www.liquibase.org>`__ to automatically upgrade the database scheme
  after the data model has changed.
  This ensures that the changes can also be applied to the production server.
  In case you encounter errors with Liquibase checksum values:

    * Run the following command in your terminal / command line: ``./gradlew liquibaseClearChecksums``
    * You can manually adjust the checksum for a breaking changelog: ``UPDATE `DATABASECHANGELOG` SET `MD5SUM` = NULL WHERE `ID` = '<changelogId>'``

Client
""""""

- If you are using a machine with limited RAM *(e.g. ~8 GB RAM)* you might have issues starting the Artemis Client.
  You can resolve this by following the description in :ref:`UsingTheCommandLine`