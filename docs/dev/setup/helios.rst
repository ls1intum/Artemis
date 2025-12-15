.. _setup-with-helios:

Server and Client Setup with Helios
--------------------------------

Helios acts as UI over GitHub actions and allows a straightforward and quick setup of the Artemis server and client on test servers.

*Prerequisites*:
1. GitHub account with access to the Artemis repository.
2. Set the GitHub account as Artemis Dev account. If not done yet, request access on https://requestaccess.aet.cit.tum.de/

Once you have fulfilled the prerequisites, follow these steps to set up Artemis on a test server by using Helios:
1. Go to https://helios.aet.cit.tum.de/ and sign in with your GitHub credentials.
2. Select the Artemis card.
3. Choose the branch or Pull Request you want to deploy. 
4. Choose an Artemis test server from the list that is not locked by another user.
5. Click on `Deploy` to start the deployment process.

Once the deployment is successful, Helios will provide you with the URL to access the Artemis application on the selected test server. 
You can now log in using your TUM credentials.

You can check out the configurations of the test servers on the :ref:`GitHub Deployment <_testservers>` page.

**Note 1**: While deploying and shortly after you will have a lock on the selected test server. Eventually, the lock will expire and make the resource available for other Helios users.

**Note 2**: You can validate that the deployed Artemis version is correct by checking the version information in the footer of the Artemis web application. It will show the branch name and the commit hash of the deployed version, as well as the date and issuer of the last commit.