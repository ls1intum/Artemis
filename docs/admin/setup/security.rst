Security
========


Passwords
---------

The Artemis configuration files contain a few default passwords and secrets
that have to be overridden in your own configuration files or via environment
variables (`Spring relaxed binding <https://github.com/spring-projects/spring-boot/wiki/Relaxed-Binding-2.0>`_).

.. code-block:: yaml

    artemis:
        user-management:
            internal-admin:
                username: "artemis-admin"
                # can be changed later, Artemis will update the password in the database
                # and connected systems on the next start
                password: "artemis-admin"
        version-control: # only required if using Integrated code lifecycle. In a distributed setup, this needs to be configured for localvc nodes and buildagent nodes
            build-agent-git-username: "buildagent_user" # Required for https access to localvc
            build-agent-git-password: "buildagent_password" # Required for https access to localvc. You can otherwise use an ssh key
    jhipster:
        security:
            authentication:
                jwt:
                    # used to sign the JWT tokens for user authentication
                    # can be changed later, will require all users to log in again
                    #
                    # encoded using Base64 (you can use `echo 'secret-key'|base64` on your command line)
                    base64-secret: ""
        registry:
            password: "change-me"  # only for distributed setups with multiple Artemis instances

    spring:
        prometheus:
            # if Prometheus monitoring is enabled: a comma-separated list of
            # IPs that are allowed to access the metrics endpoint
            monitoring-ip: "127.0.0.1"
        websocket:
            broker:
                username: "guest"  # only for distributed setups
                password: "guest"  # only for distributed setups


.. note::

    The usernames/passwords for external systems (GitLab,
    Jenkins, …) are not listed here, since the general setup documentation
    describes how to set up those systems.
    Without replacing the default values, the connection to them will not work.


.. note::

    Ensure restrictive access to the configuration files, so that access is only
    possible for the system account that runs Artemis and administrators.


.. _configure-ssh-access:

SSH Access
----------

To allow users to clone their programming exercises via SSH in the integrated code lifecycle setup, SSH must be
configured correctly on the server.

Follow the next steps to create and manage SSH key pairs,
distribute them across multiple nodes via Ansible, configure the
system to use these keys, and adapt Nginx to enable SSH routing.

Creating Key Pairs for Each Supported Algorithm
"""""""""""""""""""""""""""""""""""""""""""""""

Generate key pairs for the supported algorithms (RSA, ECDSA, and Ed25519)
using the ssh-keygen command. Here's how you can do it:

.. code-block:: bash

    # Generate RSA key pair
    ssh-keygen -t rsa -b 4096 -f ~/id_rsa

    # Generate Ed25519 key pair
    ssh-keygen -t ed25519 -f ~/id_ed25519

Make sure the keys have the standard name for the according key type. E.g. id_rsa for RSA.

Adding Key Pairs to VM via Ansible
""""""""""""""""""""""""""""""""""

Use Ansible to distribute the generated key pairs to all VMs in your
infrastructure. Here's an example Ansible playbook to achieve this:

.. code-block:: yaml

    - name: Distribute SSH keys to VMs
      hosts: all
      vars:
        key_dir: "/path/to/keys"
      tasks:
        - name: Copy RSA key pair to VM
          copy:
            src: "{{ key_dir }}/id_rsa"
            dest: "~/.ssh/id_rsa"
            mode: '0600'

        - name: Copy RSA public key to VM
          copy:
            src: "{{ key_dir }}/id_rsa.pub"
            dest: "~/.ssh/id_rsa.pub"
            mode: '0644'


Configuring System to Use Keys
""""""""""""""""""""""""""""""

Ensure the configuration variables point to the folder containing the keys. You can set this in your
Ansible playbook or configuration management tool.

In a multinode setup, it is crucial that all nodes use the same set of keys to ensure hosts can communicate with all
nodes correctly. Ensure the key distribution playbook is applied to all nodes in the cluster.

For Artemis to find the key set `artemis.version-control.ssh-host-key-path` to the path where you stored the keys.

Adapting Nginx to Enable SSH Routing
""""""""""""""""""""""""""""""""""""

To enable SSH routing through Nginx, you can set up an SSH proxy. However, Nginx by itself does
not support SSH, but you can use Nginx to reverse proxy an SSH service (e.g., using sslh to multiplex SSH and HTTPS).

Configure sslh to listen on port 443 (to handle both HTTPS and SSH), by editing the sslh configuration
file (e.g., /etc/default/sslh):

.. code-block:: text

    RUN=yes
    DAEMON=/usr/sbin/sslh
    DAEMON_OPTS="--user sslh --listen 0.0.0.0:443 --ssh 127.0.0.1:22 --ssl 127.0.0.1:8443"



Configure Nginx to proxy HTTPS traffic, by adapting the configuration file to listen on port 8443 for HTTPS:

.. code-block:: nginx

    server {
        listen 8443 ssl;
        server_name yourdomain.com;

        ssl_certificate /etc/nginx/ssl/nginx.crt;
        ssl_certificate_key /etc/nginx/ssl/nginx.key;

        location / {
            proxy_pass http://127.0.0.1:8080;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }
    }

Restart sslh and Nginx:

.. code-block:: bash

    sudo systemctl restart sslh
    sudo systemctl restart nginx

By following these steps, you ensure that your key pairs are properly generated and distributed across all
nodes, the configuration is set up to point to the folder with the keys, and Nginx is adapted to handle
SSH routing through a proxy setup.
