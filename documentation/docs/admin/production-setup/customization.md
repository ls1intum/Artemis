---
id: customization
title: Customization
sidebar_label: Customization
---

You can define the following custom assets for Artemis to be used
instead of the TUM defaults:

* The logo next to the “Artemis” heading on the navbar → `${artemisRunDirectory}/public/images/logo.png`
* The favicon → `${artemisRunDirectory}/logo/favicon.svg`
* The contact email address in the `application-{dev,prod}.yml` configuration file under the key `info.contact`
* Set `info.operatorName` (the operating organization), `info.operatorAdminName` (the administrator), and `info.universityName` (the university or institution) in your configuration. Meaningful values are required on every production core node, even when telemetry is disabled; development servers and test servers (`info.testServer: true`) start without them. The `/about` page displays the values that are set. The optional `info.contact` supplies the contact email address. Artemis also uses this information for the [telemetry](/admin/telemetry) service; `sendAdminDetails` controls transmission of the administrator's name and contact, not their display on the About page.
* The maximal number of plagiarism results stored per plagiarism checks in the `application-{dev,prod}.yml` configuration file under the key `artemis.plagiarism-checks.plagiarism-results-limit`
