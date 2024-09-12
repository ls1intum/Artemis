Customize your Artemis instance
-------------------------------

You can define the following custom assets for Artemis to be used
instead of the TUM defaults:

* The logo next to the “Artemis” heading on the navbar → ``${artemisRunDirectory}/public/images/logo.png``
* The favicon → ``${artemisRunDirectory}/logo/favicon.svg``
* The contact email address in the ``application-{dev,prod}.yml`` configuration file under the key ``info.contact``
* The operator's name  (e.g. university) and the operator's contact information (administrator email address and name) can be specified in the ``application-{dev,prod}.yml`` configuration file under the keys ``info.operatorName``, ``info.contact`` and ``info.operatorAdminName``. These values are also displayed on the ``/about`` page. The operator's name is required, while the administrator's name is optional. Artemis also uses this information for the :ref:`telemetry` service.
* The maximal number of plagiarism results stored per plagiarism checks in the ``application-{dev,prod}.yml`` configuration file under the key ``artemis.plagiarism-checks.plagiarism-results-limit``
