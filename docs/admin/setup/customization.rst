Customize your Artemis instance
-------------------------------

You can define the following custom assets for Artemis to be used
instead of the TUM defaults:

* The logo next to the “Artemis” heading on the navbar → ``${artemisRunDirectory}/public/images/logo.png``
* The favicon → ``${artemisRunDirectory}/logo/favicon.svg``
* The contact email address in the ``application-{dev,prod}.yml`` configuration file under the key ``info.contact``
* The university's name and its main contact information can be specified in the ``application-{dev,prod}.yml`` configuration file under the keys ``info.universityName`` and ``info.universityAdminName``. These values are also displayed on the ``/about`` page.
* The maximal number of plagiarism results stored per plagiarism checks in the ``application-{dev,prod}.yml`` configuration file under the key ``artemis.plagiarism-checks.plagiarism-results-limit``
