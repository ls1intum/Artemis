Troubleshooting
===============


Repository download of large courses fails
------------------------------------------

The most likely issue is that the configured timeouts on the reverse proxy are too low. 

If you set up your Artemis instance with the `Ansible Role (>=v0.1.1) <https://github.com/ls1intum/artemis-ansible-collection>`__, you can set the timeout values with the following variables:

.. code:: yaml

   proxy_send_timeout: "900s"
   proxy_read_timeout: "900s"
   fastcgi_send_timeout: "900s"
   fastcgi_read_timeout: "900s"

If you configured the nginx reverse proxy by hand, you have to adapt the following variables in your nginx configurations:

- https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_read_timeout
- https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_send_timeout
