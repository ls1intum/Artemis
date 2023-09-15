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
- Add your changelog in your newly created file. Take other changes and the Liquibase documentation as inspiration.


2.1. MySQL and PostgreSQL compatibility
=======================================

We support both PostgreSQL and MySQL databases.
In case you introduce database changes, carefully check the results of the continuous integration tests run against the different databases to make sure the migration works on both of them.

.. warning::
    Especially for manually written SQL statements (``<sql>…</sql>`` blocks in the changelog) you have to make sure they are compatible with both database types.

Only in special cases where you cannot avoid different SQL statements depending on the database type, you can use preconditions as part of your changeset to apply it only to one database type.

.. note::
    Only use those preconditions in exceptional cases where it definitely cannot be avoided.
    They increase the risk of migrations that are inconsistent between the two different database types.

.. code-block:: xml
    :caption: Pattern for database changes with preconditions depending on the database type.

    <databaseChangelog>
        <!-- … -->

        <changeSet id="00000000000000m" author="you">
            <!-- Only runs for MySQL -->
            <preConditions onFail="CONTINUE">
                <dbms type="mysql"/>
            </preConditions>

            <!-- your <sql> and other Liquibase changes here -->
        </changeSet>

        <changeSet id="00000000000000p" author="you">
            <!-- Only runs for PostgreSQL -->
            <preConditions onFail="CONTINUE">
                <dbms type="postgresql"/>
            </preConditions>

            <!-- your <sql> and other Liquibase changes here -->
        </changeSet>

        <!-- … -->
    </databaseChangelog>


3. Development
==============

- All executed entries are saved in the table :code:`databasechangelog`. If you delete something, it gets executed again.
- Test your changes locally first, and only commit changes you are confident that work.
- Before deploying any database changes to a test server, ask for official permission from the project lead. If the changes don't get approved, manual rollbacks can be necessary, which are avoidable.
- Make sure to add your name to the :code:`changeSet` in your file as well as your formatted time as the :code:`id`. Refer to other changes for further help.
