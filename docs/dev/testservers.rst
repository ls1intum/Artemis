.. _testservers:

Test Servers
============

+--------------------------------------------+-----------------------------+----------------+---------------------+----------------+
|                  Testserver                |    Connected Systems        | Deployment via |        Access       | Admin-Accounts |
+============================================+=============================+================+=====================+================+
| artemis-staging-localci.artemis.cit.tum.de | - Integrated Code Lifecycle |     Bamboo     | - TUMonline account |    On Demand   |
|                                            | - LDAP                      |                | - Test accounts     |                |
|                                            | - LTI                       |                |                     |                |
|                                            | - Apollon                   |                |                     |                |
|                                            | - Iris                      |                |                     |                |
|                                            | - MySQL                     |                |                     |                |
+--------------------------------------------+-----------------------------+----------------+---------------------+----------------+
| https://artemis-test0.artemis.in.tum.de/   | - ICL (migrated)            |     Bamboo     | - TUMonline account |                |
|                                            | - LDAP                      |                |                     |                |
|                                            | - Sending E-Mails possible  |                |                     |                |
|                                            | - MySQL                     |                |                     |                |
+--------------------------------------------+-----------------------------+----------------+---------------------+----------------+
| https://artemis-test1.artemis.cit.tum.de   | - Integrated Code Lifecycle |     GitHub     | - TUMonline account |    On Demand   |
|                                            | - MySQL                     |                | - Test accounts     |                |
|                                            | - 3 Nodes                   |                |                     |                |
|                                            | - Iris                      |                |                     |                |
|                                            | - LTI                       |                |                     |                |
+--------------------------------------------+-----------------------------+----------------+---------------------+----------------+
| https://artemis-test2.artemis.cit.tum.de   | - Integrated Code Lifecycle |     GitHub     | - TUMonline account |    On Demand   |
|                                            | - MySQL                     |                | - Test accounts     |                |
|                                            | - 1 Node                    |                |                     |                |
|                                            | - LTI                       |                |                     |                |
+--------------------------------------------+-----------------------------+----------------+---------------------+----------------+
| https://artemis-test3.artemis.cit.tum.de   | - Integrated Code Lifecycle |     GitHub     | - TUMonline account |    On Demand   |
|                                            | - Postgres                  |                | - Test accounts     |                |
|                                            | - 3 Nodes                   |                |                     |                |
|                                            | - Iris                      |                |                     |                |
|                                            | - LTI                       |                |                     |                |
+--------------------------------------------+-----------------------------+----------------+---------------------+----------------+
| https://artemis-test4.artemis.cit.tum.de   | - Integrated Code Lifecycle |     GitHub     | - TUMonline account |    On Demand   |
|                                            | - LDAP                      |                | - Test accounts     |                |
|                                            | - LTI                       |                |                     |                |
|                                            | - Apollon                   |                |                     |                |
|                                            | - Iris                      |                |                     |                |
|                                            | - MySQL                     |                |                     |                |
+--------------------------------------------+-----------------------------+----------------+---------------------+----------------+
| https://artemis-test5.artemis.cit.tum.de   | - Integrated Code Lifecycle |     GitHub     | - TUMonline account |    On Demand   |
|                                            | - Postgres                  |                | - Test accounts     |                |
|                                            | - 1 Node                    |                |                     |                |
|                                            | - Iris                      |                |                     |                |
|                                            | - LTI                       |                |                     |                |
+--------------------------------------------+-----------------------------+----------------+---------------------+----------------+
| https://artemis-test6.artemis.cit.tum.de   | - GitlabCI                  |     GitHub     | - TUMonline account |    On Demand   |
|                                            | - Postgres                  |                | - Test accounts     |                |
|                                            | - 1 Node                    |                |                     |                |
|                                            | - LTI                       |                |                     |                |
+--------------------------------------------+-----------------------------+----------------+---------------------+----------------+
| **Legacy Testservers**: See on confluence below                                                                                  |
+--------------------------------------------+-----------------------------+----------------+---------------------+----------------+

Test Accounts can be seen on the `Artemis Test Server Confluence Page`_.

..  _`Artemis Test Server Confluence Page`: https://confluence.ase.in.tum.de/x/lVGBAQ

GitHub Deployment
-----------------

1. In your Pull Request on GitHub, scroll all the way down to the build status.

    .. figure:: testservers/pr-build-status.png
        :alt: GitHub Actions UI: Build status

2. Wait for the GitHub Docker build to complete successfully

    .. figure:: testservers/github/docker-build-complete.png
            :alt: GitHub Actions UI: Waiting for build to complete successfully

3. Check if the test server you want to deploy to is unused. For that, do the following:

    a) Scroll to the testing steps in the PR description

    b) Look at the badges to see which testservers are currently locked

        .. figure:: testservers/github/testserver-status.png
                :alt: GitHub Actions UI: Check Testserver status

    c) If the testserver you want to deploy to is green, you can continue

4. Add the deployment label for your test server

    .. figure:: testservers/github/deployment-label.png
            :alt: GitHub Actions UI: Add deployment label for test server

    Note: If you try to deploy to a locked server, you will receive an error message (and you waste your time while waiting for it, so just check beforehand like explained above).

    .. figure:: testservers/github/deploy-error-message.png
        :alt: GitHub Actions UI: Deployment error message

5. Wait for GitHub to replace your label with the lock label of the test server

    .. figure:: testservers/github/lock-label.png
        :alt: GitHub Actions UI: Deployment label is replaced by lock label

6. Next to the deployment message in the PR history, GitHub offers a handy button that will forward you to the test server right away. Click it to reach the test server with your PR deployed.

    .. figure:: testservers/github/testserver-forward.png
        :alt: GitHub Actions UI: Forwarding to test server

7. Perform your testing

8. As soon as you're done with this PR (even if you want to test more PRs), remove the lock label to make the test server available to other PRs.

    .. figure:: testservers/github/remove-lock-label.png
        :alt: GitHub Actions UI: Remove lock label

Bamboo Deployment
-----------------
1. In your Pull Request on GitHub, scroll all the way down to the build status.

    .. figure:: testservers/pr-build-status.png
        :alt: GitHub UI: Build status

2. Click on "Details" next to the successful build with the ASE logo

    .. figure:: testservers/bamboo/build-details.png
        :alt: GitHub UI: Build status

3. Click on #<build number>, either on the green bar or the big title (above "Plan branch")

    .. figure:: testservers/bamboo/build-number.png
        :alt: Bamboo UI: Click on build number

4. (1) Create release OR (2) Click on the name of the existing release
5.

    a) Click on the "Create release" button

        .. figure:: testservers/bamboo/create-release.png
            :alt: Bamboo UI: Create release

    b) Leave the default options and confirm by clicking "Create release"

        .. figure:: testservers/bamboo/create-release-confirm.png
            :alt: Bamboo UI: Confirm create release

6. Go to Slack and check the "artemis-testserver" channel.

    a) Check if anyone is using the test server you want to use

    b) If your test server is free, type in the short name. Usually, that would be "ts1", "ts2", or "ts3", and sometimes maybe "ts0" or "staging". Send the message

    c) The test server is now "yours"

7. Back on Bamboo, click the Deploy button and select the target server. Alternatively, click on the small deployment cloud icon next to the target server in the status list.
Bamboo will ask you again to confirm, similarly to the create release workflow. Just leave all default options like they are and confirm.

    .. figure:: testservers/bamboo/deploy.png
        :alt: Bamboo UI: Deploy to testserver

8. Bamboo will now deploy the PR to the test server. Visit the website of the server and wait until Artemis booted again.

9. Perform your testing

10. After you are done with the PR, there are two options:

    a) Have another PR to test? Start over with step 1. Obviously leave out the slack part as you already reserved the test server for you.

    b) Are you done? Release the test server so it can be used by others by **striking through** your previous lock message.


