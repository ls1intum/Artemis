.. _testservers:

Test Servers
============

Test Server 1-3
---------------

Deployment via Bamboo. Only for branches of the ``ls1intum/Artemis`` repository, no forks.
Guide available in the Artemis Developer Confluence Space: `Deploying changes to test server`_.

.. _`Deploying changes to test server`: https://confluence.ase.in.tum.de/display/Artemis/Deploying+changes+to+test+server

Test Server 5
-------------


Pull requests on GitHub can be deployed to TS5_, including forks.
To invoke a deployment, you need to be part of the `ls1intum` GitHub organization.

Start the deployment using the `Test Server Deployment Workflow`_.
(Refer to the `GitHub documentation "Manually running a workflow"`_.)
Supply the pull request number to initiate the deployment (e.g. ``42`` for PR #42).
TS5 is locked to a pull request using the `lock:artemistest5`_ label.
The workflow applies the lock label automatically on deployment.
Remove the label from the PR once the test server is free to use by other developers.


.. _TS5: https://artemistest5.ase.in.tum.de
.. _`Test Server Deployment Workflow`: https://github.com/ls1intum/Artemis/actions?query=workflow%3A%22Testserver+Deployment%22
.. _`GitHub documentation "Manually running a workflow"`: https://docs.github.com/en/free-pro-team@latest/actions/managing-workflow-runs/manually-running-a-workflow
.. _`lock:artemistest5`: https://github.com/ls1intum/Artemis/pulls?q=is%3Aopen+is%3Apr+label%3Alock%3Aartemistest5
