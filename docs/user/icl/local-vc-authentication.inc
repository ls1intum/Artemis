Cloning a repository
^^^^^^^^^^^^^^^^^^^^

You can use Sourcetree, Git from the terminal, or any client you like to clone your Git repository.
These instructions show you how to clone your repository using Git from the terminal.

From the exercise view, click the code button to display the Clone dialog.
Copy the clone URL (either the SSH format or the HTTPS, with or without token).
If you are using the SSH protocol, ensure your public key is stored in your Artemis account settings and loaded on the local system to which you are cloning.
From a terminal window, change to the local directory where you want to clone your repository.

Paste the command you copied from Bitbucket, for example:

Clone over HTTPS:

.. code-block:: bash

   git clone https://username@artemis.cit.tum.de/course/documentation-tests.git


Clone over SSH:

.. code-block:: bash

   git clone ssh://git@artemis.cit.tum.de/course/documentation-tests.git


If the clone was successful, a new sub-directory appears on your local drive.
This directory has the same name as the repository that you cloned.
The clone contains the files and metadata that Git requires to maintain the changes you make to the source files.

HTTPS access tokens
^^^^^^^^^^^^^^^^^^^

Instructors can create HTTP access tokens for repository access in Artemis.
They are created in the account settings and are used in place of passwords for Git over HTTPS.

Token Creation
""""""""""""""

1. Go to Profile > Settings > VCS token.
2. Create a new token

Using SSH keys to secure Git operations
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Artemis provides a simple way for users and other systems to connect securely to repositories, using SSH keys, in order to perform Git operations.
You can add a personal SSH key to your user account to easily authenticate when performing read operations from your local machine. An Artemis user can currently add one key to their account.
Read more at SSH user keys for personal use.

Before you can use SSH keys to secure a connection with Artemis the following must have already been done:

- SSH is enabled on your universities Artemis instance
- you need an SSH key! See Creating SSH keys.

Note that:

- You can use the same SSH key for multiple repositories or projects.
- An Artemis user can add only one key to their account.
- Artemis supports ECDSA, RSA2, and Ed25519 key types.


Creating SSH keys
^^^^^^^^^^^^^^^^^

dasf
