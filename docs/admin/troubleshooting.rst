Troubleshooting
===============


Repository download  of large courses fails
-------------------------------------------

The most likely issue is that the configured timeouts on the reverse proxy are to low. 

If you set up your Artemis instance with the `Ansible Role (>=v0.1.1) <https://github.com/ls1intum/artemis-ansible-collection>`__, 
you can set the timeout values with the following varaibles:

.. code:: yaml
   proxy_send_timeout: "900s"
   proxy_read_timeout: "900s"
   fastcgi_send_timeout: "900s"
   fastcgi_read_timeout: "900s"
