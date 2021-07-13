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
To invoke a deployment, you need to be part of the `@ls1intum/artemis-developers` GitHub team.

#. Waiting for build to finish

    .. figure:: testservers/actions-deploy-wait-for-build.png
        :alt: GitHub Actions UI: Waiting for build to finish

#. Deployment waiting for approval

    .. figure:: testservers/actions-deploy-wait-for-approval.png
        :alt: GitHub Actions UI: Deployment waiting for approval

#. Review Deployment

    .. figure:: testservers/actions-deploy-review-deployment.png
        :alt: GitHub Actions UI: Review Deployment

#. Deployment done

    .. figure:: testservers/actions-deploy-done.png
        :alt: GitHub Actions UI: Deployment done

Start the deployment by reviewing the `Build & Deploy` action.
(Refer to the `GitHub documentation "Reviewing deployments"`_.)
TS5 is locked to a pull request using the `lock:artemistest5`_ label.
The workflow applies the lock label automatically on deployment.
Remove the label from the PR once the test server is free to use by other developers.


.. _TS5: https://artemistest5.ase.in.tum.de
.. _`GitHub documentation "Reviewing deployments"`: https://docs.github.com/en/actions/managing-workflow-runs/reviewing-deployments
.. _`lock:artemistest5`: https://github.com/ls1intum/Artemis/pulls?q=is%3Aopen+is%3Apr+label%3Alock%3Aartemistest5
