***********************************
Structural Migration with Liquibase
***********************************

To prevent accidental irreversible database modifications, we use `Liquibase <https://docs.liquibase.com/home.html>`_ to prepare changes when developing that can be confirmed in the review process.

1. Gradle
==========

We offer two Gradle commands with Liquibase:

- `liquibaseClearChecksums`: Use this whenever Liquibase detects inconsistencies between the database changelog and the XML changelog
- `liquibaseDiffChangeLog`: Generates a new changelog from the current database state. The command appears to not work for all operating systems, and you might have to add a changelog manually.

2. Changelog
============

The changelog manifest lies in :code:`src/main/resources/config/liquibase/master.xml`, which imports all single changelog files. To create a new change, you have to do the following:

- Get the current time in the format :code:`YYYYMMDDHHmmss`.
- Create a new file in :code:`/changelog` named :code:`<formatted-time>_changelog.xml` and include this file at the bottom of the :code:`master.xml` as every other file.
- Add your changelog in your newly created file. Take other changes and the Liquibase documentation as inspiration

3. Development
==============

- All executed entries are saved in the table :code:`databasechangelog`. If you delete something, it gets executed again.
- Test your changes locally first, and only commit changes you are confident that work.
- Before deploying any database changes to a test server, ask for official permission from the project lead. If the changes don't get approved, manual rollbacks can be necessary, which are avoidable.
- Make sure to add your name to the :code:`changeSet` in your file as well as your formatted time as the :code:`id`. Refer to other changes for further help.
