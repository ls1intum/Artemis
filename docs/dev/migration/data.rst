*************************
Data Migration with Java
*************************

.. note::
    This type of migration should only be used if the logic cannot be implemented in SQL as part of a Liquibase changelog.
    The Java migrations are *significantly slower* since they need to load and convert the data from the DBMS into Java objects, apply the changes, and then write the new data back to the database.


1. Changelog
=============

The changelog can be found in :code:`src/main/java/config/migration/MigrationRegistry.java`. To create a new change, you have to do the following:

- Get the current time in the format :code:`YYYYMMDD_HHmmss`.
- Create a new file in :code:`/entries` named :code:`MigrationEntry<formatted-time>.java` containing a class extending :code:`MigrationEntry`.
- Implement the required methods in your class and follow the JavaDoc.
- Add the class to the :code:`migrationEntryMap` attribute in the :code:`MigrationRegistry.java` together with the next Integer key inside the constructor.

2. Development
==============

- All executed entries are saved in the table :code:`migration_changelog`. If you delete entries, they get executed again.
- Test your changes locally first, and only commit changes you are confident that work.
- Before deploying any database changes to a test server, ask for official permission from the project lead. If the changes don’t get approved, manual rollbacks can be necessary, which are avoidable.
- If queries fail due to the authorization object being null, call `SecurityUtils.setAuthorizationObject()` beforehand to set a dummy object.
