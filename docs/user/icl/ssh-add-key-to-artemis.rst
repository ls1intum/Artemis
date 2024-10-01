.. _use ssh key:

Using SSH with Artemis
^^^^^^^^^^^^^^^^^^^^^^

.. contents:: Content of this document
    :local:
    :depth: 1

You can use SSH keys to establish a secure connection between your computer and Artemis when you are performing Git operations (pull, clone, push) from your local machine.
Personal keys are linked to your Artemis account, inheriting its permissions and operating under its unique identity.
To use your generated SSH keys with Artemis, you need to add it in the account settings.


Add an SSH key to your Artemis account
""""""""""""""""""""""""""""""""""""""

**1. Copy your public key**

On Windows in your command prompt, change directory to your .ssh directory, and copy the public key file to your clipboard by running:

.. code-block:: bash

    cd %userprofile%/.ssh
    clip < id_ed25519.pub

On macOS or Linux simply run the following in a terminal:

.. _xclip: https://wiki.ubuntuusers.de/xclip/

.. code-block:: bash

    pbcopy < ~/.ssh/id_ed25519.pub

If pbcopy isn't working, locate the hidden .ssh folder, open the file in a text editor, and copy it to your clipboard.
Note that on Linux, you may need to download and install `xclip`_, then use that, as shown in this code snippet:

.. code-block:: bash

    sudo apt-get install xclip
    xclip -sel clip < ~/.ssh/id_ed25519.pub

Note that the key's name is not necessarily **id_ed25519.pub**, but can be arbitrary, and depends on how you saved it.


**2. Add the key to your Artemis account**

Open the settings, go to the SSH tab, and select 'New Key'.
Then paste the copied SSH key into the text box.

+---------------------------------------------------+--------------------------------------------------------------+
|.. figure:: local-vc/open-settings.png             |     .. figure:: local-vc/ssh-add-public-key.png              |
|   :alt: Open account settings                     |        :alt: Add public SSH key to account                   |
|   :align: center                                  |        :align: center                                        |
|                                                   |                                                              |
|   Open you Artemis account settings               |        Add public SSH key to account in account settings     |
+---------------------------------------------------+--------------------------------------------------------------+

**3. Save the key. You're done!**

Use SSH to connect to Artemis repositories
""""""""""""""""""""""""""""""""""""""""""

After everything is set up, you can go to a programming exercise, and use the SSH clone URL with git to access the repository locally, like this, for example:

.. code-block:: bash

    git clone ssh://git@artemis.cit.tum.de:7921/git/COURSE/exercise-user_1.git
