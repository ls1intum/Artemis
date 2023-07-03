Privacy Statement and Imprint
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The privacy statement and the imprint are stored in the ``./legal``  directory by default. You can override this value by setting the ``artemis.legal-path`` value in the ``application-artemis.yml``.
The privacy statement and the imprint are stored as markdown files. Currently, English and German as languages are supported.
The documents have to follow the naming convention ``<privacy_statement|imprint>_<de|en>.md``.
In case you add only a file for one language, this file will always be shown regardless of the user's language setting.
If you add a file for each language, the file will be shown depending on the user's language setting.

In the following, the documentation provides a template in English and German for the privacy statement and the imprint with placeholders that have to be replaced with the actual content.

.. warning::

   These are only templates that are used similarly at TUM and need to be adapted to your needs.
   Make sure to consult your data protection officer and/or legal department before making the privacy statement/imprint publicly available.
   We do not take any responsibility for the content of the privacy statement or the imprint.

------------------------------------------------------------------------------------------------------------------------

.. include:: setup/privacy-statement-templates.rst

------------------------------------------------------------------------------------------------------------------------

.. include:: setup/imprint-templates.rst
