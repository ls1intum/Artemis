Shibboleth/SAML2 Login & Registration
=====================================
Artemis supports user login and registration using SAML2 / Shibboleth.
The SAML2 feature is intended for use with Artemis' internal user management and primarily serves as a registration mechanism.
With the help of this feature it is possible to store not only the login, name and email, but also the **student's matriculation number** directly in the database.
For each user who registers in the system for the first time, a "normal" artemis user is created and the data is taken from the attributes of the Shibboleth request.

* The feature is activated by the *saml2* profile.
* If you use a **reverse proxy**, you have to redirect the following endpoints to the artemis server: **/login** and **/saml2** .
* For testing you can use a `preconfigured environment <https://github.com/kit-sdq/Artemis-SAML2-Test-Docker>`__.

If you activate the SAML2 Feature an additional login button will be activated (you can set the text of the button as you like):

.. figure:: saml2-shibboleth/SAML2-Login.png
    :align: center
    :alt: SAML2 Login


The workflow of the SAML2 feature is shown in the following picture:

.. figure:: saml2-shibboleth/SAML2-Workflow.png
    :align: center
    :alt: SAML2 Workflow


The SAML2 library of Spring Boot is used to create a second security filter chain.
The new (and old) security filter chain is presented in the following figure:

.. figure:: saml2-shibboleth/SAML2-Filterchain.png
    :align: center
    :alt: SAML2 Filterchain


The feature is configured by the application-saml2.yml file.
You can configure multiple identity providers.
In addition, the SAML2 feature allows to decide whether a user can obtain a password (see "info.saml2.enable-password").
This app password allows to use the connected services as VCS and CI as usual with the local user credentials.

You can see the structure of the saml2 configuration in the following:

.. code:: yaml

    saml2:
        # Define the patterns used when generating users. SAML2 Attributes can be substituted by surrounding them with
        # curly brackets. E.g. username: '{user_attribute}'. Missing attributes get replaced with an empty string.
        # This enables definition of alternative attribute keys when using multiple IdPs. E.g. username: '{uid}{user_id}'.
        # User template pattern:
        username-pattern: '{first_name}_{last_name}'
        first-name-pattern: '{first_name}'
        last-name-pattern: '{last_name}'
        email-pattern: '{email}'
        registration-number-pattern: '{uid}'
        lang-key-pattern: 'en' # can be a pattern or fixed to en/de
        # It is also possible to only extract parts of the attribute values.
        # For each attribute key exactly one regular expression can optionally be defined that is used to extract only parts
        # of the received value. The regular expression must match the whole value. It also has to contain a named capture
        # group with the name 'value'.
        # E.g. when receiving 'pre1234post' from the SAML2 service in the 'uid'-example below, only '1234' will be used when
        # replacing '{uid}' in one of the user attributes defined above.
        value-extraction-patterns:
            #- key: 'registration_number'
            #  value_pattern: 'somePrefix(?<value>.+)someSuffix'
            #- key: 'uid'
            #  value_pattern: 'pre(?<value>\d+)post'
        # A list of identity provides (IdP). Metadata location can be a local path (or classpath) or url.
        # If your IdP does not publish its metadata you can generate it here: https://www.samltool.com/idp_metadata.php
        identity-providers:
            #- metadata: https://idp_host/.../metadata
            #  registration-id: IdPName
            #  entity-id: artemis
            #  cert-file: # path-to-cert (optional) Set this path to the Certificate for encryption/signing or leave it blank
            #  key-file: # path-to-key (optional) Set this path to the Key for encryption/signing or leave it blank (must be a PKCS#8 file!)
            # Multiple IdPs can be configured
            # - metadata: <URL>
            #   registrationid: <id>
            #   entityid: <id>

    # String used for the SAML2 login button. E.g. 'Shibboleth Login'
    info.saml2.button-label: 'SAML2 Login'
    # Sends a e-mail to the new user with a link to set the Artemis password. This password allows login to Artemis and its
    # services such as GitLab and Jenkins. This allows the users to use password-based Git workflows.
    # Enabled the password reset function in Artemis.
    info.saml2.enable-password: true
