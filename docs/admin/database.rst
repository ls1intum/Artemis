.. _admin_databaseTips:

Useful Database Commands
========================

Changing the Version Control System URL
---------------------------------------

This might be useful when the version control system changed its base-url but all repositories are still present there.

.. code-block:: sql

    update participation
    set repository_url = replace(repository_url, 'some.old.domain.com', 'your.new.vcs.domain')
    where repository_url is not null;


Migrating MySQL Data to PostgreSQL
----------------------------------

.. warning::
    Do *not* use `pgloader <https://pgloader.io/>`_ to convert the database from MySQL to PostgreSQL.
    This results in a database schema that is not compatible with future migrations.

    PgLoader converts constraint names into all-lowercase.
    The Liquibase migrations assume that they have got their original name which contains the prefix ``FK``.


.. note::
    Start Artemis at least once in version 5.12.9 or greater to make sure the current database schema is PostgreSQL-compatible.
    Only Artemis 6.0.0 or newer can connect to a PostgreSQL database.


In your Artemis config the following values might need to be added/updated to connect to PostgreSQL instead of MySQL:

.. code-block:: yaml

    spring:
        datasource:
            url: "jdbc:postgresql://<IP/HOSTNAME of PostgreSQL database host>/Artemis?ssl=false"
            username: <YOUR_DB_USER>
            password: <YOUR_DB_PASSWORD>
        jpa:
            database-platform: org.hibernate.dialect.PostgreSQL10Dialect
            database: POSTGRESQL
