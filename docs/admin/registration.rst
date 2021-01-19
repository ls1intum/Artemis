User Registration
=================

Artemis supports user registration. It has to be enabled in one ``application-*.yml`` file and can be customized.

Example:

.. code:: yaml

    artemis:
        user-management:
            use-external: false
            registration:
                enabled: true
                allowed-email-pattern: '[a-zA-Z0-9_\-\.\+]+@(tum\.de|in\.tum\.de|mytum\.de)'
                allowed-email-pattern-readable: '@tum.de, @in.tum.de, @mytum.de'
    spring:
        mail:
            host: <host>
            port: 25
            username: <username>
            password: <password>
            protocol: smtp
            tls: true
            properties.mail.smtp:
                auth: true
                starttls:
                    enable: true
                ssl:
                    trust: <host>
    jhipster:
        mail:
            base-url: https://artemis.ase.in.tum.de
            from: artemis.in@tum.de
    management:
        health:
            mail:
                enabled: true

Users can register a new account on the start page based on the regex defined in ``allowed-email-pattern``.
If no email pattern is defined, any email address can be used.
Upon registration, users receive an email to activate their account.

You can find more information on how to configure the email server on the official
`Jhipster <https://www.jhipster.tech/tips/011_tip_configuring_email_in_jhipster.html>`__
documentation.
