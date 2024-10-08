.. _basic SSH introduction:

SSH
^^^

.. contents:: Content of this document
    :local:
    :depth: 2

Artemis uses SSH as a simple way for users to connect securely to repositories to perform Git operations.

What is SSH?
""""""""""""

.. _SSH (Secure Shell): https://en.wikipedia.org/wiki/Secure_Shell

`SSH (Secure Shell)`_ is a protocol that allows you to securely connect to another computer over a network.
It’s mostly used by system administrators, developers, and IT professionals to remotely manage servers or computers.
SSH provides a secure and encrypted communication channel between your computer and a remote machine, so any data passed (like passwords or commands) is protected from eavesdropping.
In Artemis you an use SSH to access your repositories with Git.

Why use SSH?
""""""""""""

The main advantage of SSH is security.
When you connect to a remote machine using SSH, all the data exchanged between your computer and the server is encrypted.
This means if someone tries to intercept the communication, they can't read it. It's like sending messages through a locked box that only you and the server can open.

How does SSH work?
""""""""""""""""""

SSH works by using two components:

- Client: The computer you are using to connect.
- Server: The machine you want to connect to.

When you want to connect, your SSH client sends a request to the server.
If the connection is successful, you can log in to the server and start working as if you were sitting in front of it.
The connection uses SSH keys for authentication. Although it is also possible to use username and password to connect over SSH, this is discouraged.

What are SSH Keys?
""""""""""""""""""

.. _public-key cryptography: https://en.wikipedia.org/wiki/Public-key_cryptography


SSH keys are a more secure alternative to passwords for logging into a server.
They are based on `public-key cryptography`_ and come in pairs: a public key and a private key.

- Public Key: This key is stored on the server. Think of it like a lock that only you can open.
- Private Key: This key stays on your local machine (never shared!). It’s like the key to that lock.

When you try to connect to the server, your computer proves it has the private key that matches the server's public key, allowing you access.
You can add a personal SSH key to your user account to easily authenticate when performing read operations from your local machine.
An Artemis user can currently add one key to their account.
For instructions on how to add your SSH key to your Artemis account, please refer to :ref:`the relevant documentation<use ssh key>`.

Before you can use SSH keys to secure a connection with Artemis the following must have already been done:

- SSH is enabled on your university's Artemis instance
- You need an SSH key! See :ref:`Creating SSH keys<create ssh key>`.

.. note::

    - You can use the same SSH key for multiple repositories or projects.
    - An Artemis user can currently only add one key to their account.
    - Artemis supports ECDSA, RSA2, and Ed25519 key types.
