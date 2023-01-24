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

.. caution::
    Start Artemis at least once in version 5.12.9 or greater to make sure the current database schema is PostgreSQL-compatible.
    Only Artemis 6.0.0 or newer can connect to a PostgreSQL database.

This shows the conversion using two temporary helper databases using `pgloader <https://github.com/dimitri/pgloader>`_.
Instead, you might be able to directly transfer data between two database using the same tool.
In that case you still need to move the tables in the PostgreSQL database from the schema ``artemis`` to ``public`` with the command shown in the helper script below.

.. code-block:: yaml
    :caption: ``docker-compose.yml`` that creates the helper database servers

    ---
    services:
        mysql:
            image: docker.io/library/mysql:8
            environment:
                - MYSQL_ROOT_PASSWORD=12345678
                - MYSQL_DATABASE=Artemis
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
            # use the major version you want to deploy on the production server here
            image: docker.io/library/postgres:15
            environment:
                - POSTGRES_USER=root
                - POSTGRES_PASSWORD=12345678
                - POSTGRES_DB=Artemis
            ports:
                - 5432:5432
            networks:
                - db-migration

    networks:
        db-migration:
            name: "db-migration"
            driver: "bridge"
    ...

The script assumes that you created a full database dump from your production MySQL server into ``Artemis.sql``.

.. code-block:: bash
    :caption: Data migration script

    #! /usr/bin/env bash

    # start the temporary MySQL and Postgres containers
    docker compose up -d

    # import database dump into MySQL
    docker compose exec -T mysql mysql --password=12345678 < Artemis.sql

    # use pgloader to transfer data from MySQL to Postgres
    docker run --rm --network="db-migration" docker.io/dimitri/pgloader pgloader mysql://root:12345678@mysql/Artemis postgresql://root:12345678@postgres/Artemis

    # dump the Postgres data in a format that can be imported in the production database
    docker compose exec -T postgres pg_dump -Ox Artemis > Artemis.pg.sql

    # clean up
    docker compose down

    # move all tables into the correct schema ('public') instead of 'artemis'
    cat >> Artemis.pg.sql << EOF
    DO
    $$
    DECLARE
        row record;
    BEGIN
        FOR row IN SELECT tablename FROM pg_tables WHERE schemaname = 'artemis'
        LOOP
            EXECUTE format('ALTER TABLE artemis.%I SET SCHEMA public;', row.tablename);
        END LOOP;
    END;
    $$;

    drop schema artemis;
    EOF

You can then import the new database dump ``Artemis.pg.sql`` into a PostgreSQL database using ``psql -d Artemis < Artemis.pg.sql``.
