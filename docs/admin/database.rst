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
    The Liquibase migrations assume that they have got their original name which contains the case-sensitive prefix ``FK``.


#. Start Artemis at least once in version ``V`` ≧ 6.0.0 or greater to make sure the current database schema is PostgreSQL-compatible.

#. Stop Artemis.

#. Create a database backup using ``mysqldump --all-databases Artemis > Artemis.sql``.
   This dump is called ``Artemis.sql`` in the following steps.

#. Copy the ``docker-compose.yml`` file into the same directory as the ``Artemis.sql`` database dump
   and run the following commands to convert the ``Artemis.sql`` dump into ``Artemis.pg.sql`` that is usable by PostgreSQL.

   .. code-block:: yaml
        :caption: ``docker-compose.yml`` with helper containers for MySQL and PostgreSQL.

        ---
        services:
            mysql:
                image: docker.io/library/mysql:9.0.1
                environment:
                    - MYSQL_DATABASE=Artemis
                    - MYSQL_ALLOW_EMPTY_PASSWORD=yes
                ports:
                    - 3306:3306
                command: >
                    mysqld
                        --lower_case_table_names=1 --skip-ssl
                        --character_set_server=utf8mb4
                        --collation-server=utf8mb4_unicode_ci
                        --explicit_defaults_for_timestamp
                        --default-authentication-plugin=mysql_native_password
                networks:
                    - db-migration

            postgres:
                image: docker.io/library/postgres:16.4
                environment:
                    - POSTGRES_USER=root
                    - POSTGRES_DB=Artemis
                    - POSTGRES_HOST_AUTH_METHOD=trust
                ports:
                    - 5432:5432
                networks:
                    - db-migration

        networks:
            db-migration:
                driver: "bridge"
                name: artemis-db-migration
        ...

   .. code-block:: bash
        :caption: Commands to transform the MySQL dump into a PostgreSQL one.

        #! /usr/bin/env bash

        # start the temporary MySQL and Postgres containers
        docker compose up -d

        # import database dump into MySQL
        docker compose exec -T mysql mysql < Artemis.sql

        # use pgloader to transfer data from MySQL to Postgres
        docker run --rm --network=artemis-db-migration docker.io/dimitri/pgloader pgloader mysql://root@mysql/Artemis postgresql://root@postgres/Artemis

        # dump the Postgres data in a format that can be imported in the actual database
        docker compose exec -T postgres pg_dump -Ox Artemis > Artemis.pg.sql

        # clean up
        docker compose down

   .. note::
      Alternatively, you could use some temporary database on your PostgreSQL instance that can be deleted afterwards to migrate the data directly from your production MySQL into there.
      Use this temporary PostgreSQL database to create the ``Artemis.pg.sql`` dump that can be imported into the production database after merging with the proper schema.

      In that case the ``pgloader`` command in the steps above should work similarly without the ``--network`` flag and adapted database connection URLs.
      For ``pg_dump``, add the necessary flags to connect to your database *in addition to* ``-Ox``.

#. Update the Artemis config to connect to an *empty* new PostgreSQL database (see :ref:`admin-postgres-connection-config`).
   Start Artemis, wait until it has finished starting up and created the schema, and stop it again.

   .. warning::

        Use the same version ``V`` that was connected to MySQL before.

#. Dump the schema Artemis has created on the PostgreSQL server in the previous step using

   .. code-block:: bash

        pg_dump -Ox Artemis > empty.pg.sql

#. Now the database schema as created by Artemis (``empty.pg.sql``) and the one containing the actual data migrated from MySQL (``Artemis.pg.sql``) need to be merged.

   Use the following script like ``python3 ./merge.py > merged.pg.sql`` to create the merged database dump.

   .. code-block:: python
        :caption: ``merge.py`` database dump merge script.

        #! /usr/bin/env python3

        """
        Merges two database dumps
        - empty.pg.sql
        - Artemis.pg.sql
        created from an Artemis database where `empty.pg.sql` contains a fresh DB
        schema as created by the first start of Artemis from a new database, and
        `Artemis.pg.sql` is a dump from an Artemis database that was converted from
        MySQL to PostgreSQL using pgloader.

        It is merged so that the schema definitions are taken from `empty.pg.sql` and
        the actual data comes from `Artemis.pg.sql`. The script assumes the order of
        operations in the dumps: first the schema is created, then data is inserted,
        and finally foreign key constraints and indices are added.

        Both the empty database dump and the original MySQL data must come from an
        _identical_ version of Artemis. Otherwise, the data to be inserted might not
        match the schema definition.

        """

        from pathlib import Path
        from typing import Iterator


        def _fix_schema(line: str) -> str:
            if line.startswith("COPY artemis."):
                return line.replace("COPY artemis.", "COPY public.", 1)

            if line.startswith("SELECT"):
                old = "SELECT pg_catalog.setval('artemis."
                new = "SELECT pg_catalog.setval('public."
                return line.replace(old, new, 1)

            return line


        def _extract_data(data_file_path: Path) -> None:
            with open(data_file_path, encoding="utf-8") as data_file:
                copy_found = False
                for line in data_file:
                    if not copy_found and line.startswith("COPY "):
                        copy_found = True
                    if copy_found and line.startswith("ALTER TABLE "):
                        break
                    if copy_found:
                        print(_fix_schema(line), end="")


        def _merge_files(*, schema_file_path: Path, data_file_path: Path) -> None:
            with open(schema_file_path, encoding="utf-8") as schema_file:
                schema_file_iter: Iterator[str] = iter(schema_file)
                for line in schema_file_iter:
                    if line.startswith("COPY "):
                        break
                    print(line, end="")

                _extract_data(data_file_path)

                alter_table_found = False
                for line in schema_file_iter:
                    if line.startswith("ALTER TABLE "):
                        alter_table_found = True
                    if alter_table_found:
                        print(line, end="")


        def main() -> None:
            print("-- ensure fresh schema")
            print("drop schema if exists public cascade;")
            print("create schema public;")
            print()

            _merge_files(
                schema_file_path=Path("empty.pg.sql"), data_file_path=Path("Artemis.pg.sql")
            )


        if __name__ == "__main__":
            main()

#. Import the merged database dump ``merged.pg.sql`` into the production PostgreSQL database using ``psql < merged.pg.sql``.

   .. warning::

      The schema ``public`` of the target database will be deleted and completely overwritten when importing.


.. _admin-postgres-connection-config:

Connecting Artemis to PostgreSQL
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

In your Artemis config the following values might need to be added/updated to connect to PostgreSQL instead of MySQL:

.. code-block:: yaml

    spring:
        datasource:
            url: "jdbc:postgresql://<IP/HOSTNAME of PostgreSQL database host>/Artemis?ssl=false"
            username: <YOUR_DB_USER>
            password: <YOUR_DB_PASSWORD>
        jpa:
            database: POSTGRESQL
