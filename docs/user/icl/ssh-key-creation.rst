.. _create ssh key:

Creating SSH keys
^^^^^^^^^^^^^^^^^

SSH keys can be used to establish a secure connection with the Artemis Local Version Control, where you are performing Git operations from your local machine.
The SSH key needs to be added to Artemis before you can make use of the key.

Creating an SSH key on Windows
""""""""""""""""""""""""""""""

**1. Check for existing keys**

You should check for existing SSH keys on your local computer. Open a command prompt, and run:

.. code-block:: bash

   cd %userprofile%/.ssh

- If you see "No such file or directory", then there aren't any existing keys:  go to step 3.

- Check to see if you have a key already:

.. code-block:: bash

   dir id_*

If there are existing keys, you may want to use those: :ref:`Add your key to Artemis<use ssh key>`.

**2. Back up old SSH keys**

If you have existing SSH keys, but you don't want to use them when connecting to Bitbucket, you should back those up.
In a command prompt on your local computer, run:

.. code-block:: bash

   mkdir key_backup
   copy * key_backup

**3. Generate a new SSH key**

If you don't have an existing SSH key that you wish to use, generate one as follows:
1. Log in to your local computer as an administrator.
2. In a command prompt, run:

.. _Git (with Git Bash): <https://gitforwindows.org/>


.. code-block:: bash

   ssh-keygen -t ed25519 -C "your_email@example.com"

Associating the key with your email address helps you to identify the key later on.
Note that the ssh-keygen command is only available if you have already installed `Git (with Git Bash)`_.
You'll see a response similar to this:

.. code-block:: bash

    C:\Users\artemis>ssh-keygen -t ed25519 -C "your_email@example.com"
    Generating public/private ed25519 key pair.
    Enter file in which to save the key (/c/Users/artemis/.ssh/id_ed25519):

3. Just press <Enter> to accept the default location and file name. If the .ssh directory doesn't exist, the system creates one for you.
4. Enter, and re-enter, a passphrase when prompted. The whole interaction will look similar to this:

.. code-block:: bash

    C:\Users\artemis>ssh-keygen -t ed25519 -C "your_email@example.com"
    Generating public/private ed25519 key pair.
    Enter file in which to save the key (/c/Users/artemis/.ssh/id_ed25519):
    Created directory '/c/Users/artemis/.ssh'.
    Enter passphrase (empty for no passphrase):
    Enter same passphrase again:
    Your identification has been saved in c/Users/artemis/.ssh/id_ed25519.
    Your public key has been saved in c/Users/artemis/.ssh/id_ed25519.pub.
    The key fingerprint is:
    SHA256:wvaHYeLtY6+DlvV5sFZgDi3abcdefghijklmnopqrstuvw your_email@example.com

5. You're done and you can now :ref:`add your key to Artemis<use ssh key>`.

Creating an SSH key on Linux & macOS
""""""""""""""""""""""""""""""""""""

**1. Check for existing SSH keys**

You should check for existing SSH keys on your local computer. Open a terminal and run:

.. code-block:: bash

    cd ~/.ssh

If you see "No such file or directory, then there aren't any existing keys:  go to step 3.
Check to see if you have a key already:

.. code-block:: bash

    ls id_*

If there are existing keys, you may want to use those: :ref:`Add your key to Artemis<use ssh key>`.

**2. Back up old SSH keys**

If you have existing SSH keys, but you don't want to use them when connecting to Bitbucket, you should back those up.
In a command prompt on your local computer, run:

.. code-block:: bash

   mkdir key_backup
   cp * key_backup

**3. Generate a new SSH key**

If you don't have an existing SSH key that you wish to use, generate one as follows:

1. Open a terminal on your local computer and enter the following:

.. code-block:: bash

   ssh-keygen -t ed25519 -C "your_email@example.com"

Associating the key with your email address helps you to identify the key later on. You'll see a response similar to this:

.. code-block:: bash

    artemis@homemac ~ % ssh-keygen -t ed25519 -C artemis@email.com
    Generating public/private ed25519 key pair.
    Enter file in which to save the key (/Users/artemis/.ssh/id_ed25519):

2. Just press <Enter> to accept the default location and file name. If the .ssh directory doesn't exist, the system creates one for you.
3. Enter, and re-enter, a passphrase when prompted. The whole interaction will look similar to this:

.. code-block:: bash

    artemis@homemac ~ % ssh-keygen -t ed25519 -C artemis@email.com
    Generating public/private ed25519 key pair.
    Enter file in which to save the key (/Users/artemis/.ssh/id_ed25519):
    Enter passphrase (empty for no passphrase):
    Enter same passphrase again:
    Your identification has been saved in /Users/artemis/.ssh/id_ed25519.
    Your public key has been saved in /Users/artemis/.ssh/id_ed25519.pub.
    The key fingerprint is:
    SHA256:gTVWKbn41z6JgBNu3wYjLC4abcdefghijklmnopqrstuvwxy artemis@email.com
    The keys randomart image is:
    +--[ED25519 256]--+
    |==+.    +o..     |
    |.oE.   +o..      |
    |    . ...o       |
    |     .o...       |
    |     oo+S  .     |
    |  + ..B = . .    |
    |.+.+.oo+ * o .   |
    |o++.o+  . + +    |
    |B+ o.    .   .   |
    +----[SHA256]-----+
    artemis@homemac ~ %

5. You're done and you can now :ref:`add your key to Artemis<use ssh key>`.
