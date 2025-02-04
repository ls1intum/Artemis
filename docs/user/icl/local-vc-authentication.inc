.. _authentication-mechanisms:

Authentication Mechanisms
^^^^^^^^^^^^^^^^^^^^^^^^^

Artemis supports three mechanisms for authenticating to repositories: password authentication, token authentication and authentication via SSH.
Admins can configure which mechanisms should be available for users, and the order in which they appear in the code button drop down, as described :ref:`here<Configure Artemis>`.

+------------------------------------------------------------------------+
|.. figure:: local-vc/code-button-extended-authentication.png            |
|   :alt: Open account settings                                          |
|   :align: center                                                       |
|                                                                        |
|   Authentication mechanism selection                                   |
+------------------------------------------------------------------------+

There are two types of tokens: Each student has a token, for every repository of exercises they participate in, which Artemis automatically generates and provides through the code button dialog.
Instructors additionally have the ability to create an access token, with the ability to access all repositories their user account has permissions to.
Find more information on repository types and access rights :ref:`here<repository-types-and-access>`.
Here is a short overview on the advantages and disadvantages of the provided mechanisms:


+----------------------+----------------------------------------------------+---------------------------------------------------------------+
| Method               | Advantages                                         | Disadvantages                                                 |
+======================+====================================================+===============================================================+
| Password             | - easy to use                                      | - slow with external authentication                           |
|                      |                                                    | - potentially very insecure (when user has weak password)     |
|                      |                                                    | - not available to all universities                           |
+----------------------+----------------------------------------------------+---------------------------------------------------------------+
| User Token           | - easy to use, simple setup and revocation         | - dangerous if it gets leaked                                 |
|                      | - useful for repository access via API             |                                                               |
|                      | - very secure                                      |                                                               |
+----------------------+----------------------------------------------------+---------------------------------------------------------------+
| Participation Token  | - easy to use, no setup needed                     | - only usable for one repository                              |
|                      | - very limited access scope                        |                                                               |
|                      | - very secure                                      |                                                               |
+----------------------+----------------------------------------------------+---------------------------------------------------------------+
| SSH                  | - very secure                                      | - slower than HTTPS                                           |
|                      | - industry standard for use with git               | - can be difficult to use                                     |
|                      | - easy to use after correct set up                 | - can be overwhelming for new users                           |
+----------------------+----------------------------------------------------+---------------------------------------------------------------+

The participation token is the preferred authentication method for students due to its simplicity, security, and lack of setup requirements.
While SSH offers strong security, its complexity, setup challenges, and higher computational overhead make it less ideal.
Password usage is discouraged for security reasons.
Therefore, the order of authentication mechanisms should be ideally set up in the following order: token, SSH, and lastly password, with the option to disable password authentication entirely.


Cloning a repository
^^^^^^^^^^^^^^^^^^^^

Using the chosen mechanism, you can use Sourcetree, git from the terminal, or any client you like to clone your Git repository.
These instructions show you how to clone your repository using Git from the terminal.

From the exercise view, click the code button to display the Clone dialog.
Copy the clone URL (either the SSH format or the HTTPS, with or without token).
If you are using the SSH protocol, ensure your public key is :ref:`stored in your Artemis account settings<use ssh key>` and loaded on the local system to which you are cloning.
From a terminal window, change to the local directory where you want to clone your repository.

Paste the link you copied from Artemis, for example:

Clone over HTTPS:

.. code-block:: bash

   git clone https://username@artemis.cit.tum.de/course/documentation-tests.git


Clone over HTTPS with access token:

.. code-block:: bash

   git clone https://username:vcpat-ndbI5a2QhvEIOQYmtd8rdqAEjsJsKAAOqLQk6NukXRC9@artemis.cit.tum.de/course/documentation-tests.git


Clone over SSH:

.. code-block:: bash

   git clone ssh://git@artemis.cit.tum.de/course/documentation-tests.git


If the clone was successful, a new sub-directory appears on your local drive.
This directory has the same name as the repository that you cloned.
The clone contains the files and metadata that Git requires to maintain the changes you make to the source files.


VCS access tokens
^^^^^^^^^^^^^^^^^^^

The tokens for student repositories are automatically created, and provided to the student via the code button dialog.

.. warning:: Never share your access tokens or commit them to version control. Treat tokens like passwords. If a token is compromised, revoke it immediately and create a new one.
Instructors can create HTTP access tokens for repository access in Artemis.
They are created in the account settings and are used in place of passwords for Git over HTTPS.
For every student's exercise, Artemis automatically generates an access token, only associated with the repository of this particular exercise.
You can use these to authenticate to the Artemis Local Version Control.

.. _token-authentication:

Token Creation as Instructor
""""""""""""""""""""""""""""

1. Go to Profile > Settings > VCS token.
2. Create a new token

+------------------------------------------------------------------------+
|.. figure:: local-vc/add-vcs-access-token.png                           |
|   :alt: Open account settings                                          |
|   :align: center                                                       |
|                                                                        |
|   Adding a new VCS access token                                        |
+------------------------------------------------------------------------+


Using SSH keys to secure Git operations
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Artemis provides a simple way for users to connect securely to repositories, using SSH to perform Git operations.
Next is a :ref:`small and basic introduction to SSH<basic SSH introduction>`, and if you already know it, :ref:`here is a guide on how to create SSH keys<create ssh key>`.
If you already have an SSH key, :ref:`learn how to add it to your Artemis account here<create ssh key>`.

