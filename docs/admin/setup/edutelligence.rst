.. _edutelligence_setup:

EduTelligence Suite
===================

EduTelligence is a comprehensive suite of AI-powered microservices designed to enhance Learning Management Systems (LMS) with intelligent features for education. The suite seamlessly integrates with Artemis to provide automated assessment, exercise creation, competency modeling, and intelligent tutoring capabilities.

The EduTelligence suite is available as a monorepo at `https://github.com/ls1intum/edutelligence <https://github.com/ls1intum/edutelligence>`_.

.. important::
   **Compatibility Matrix**: EduTelligence maintains compatibility with different versions of Artemis. 
   Please refer to the `compatibility matrix in the EduTelligence README <https://github.com/ls1intum/edutelligence#-artemis-compatibility>`_ 
   to ensure you're using compatible versions for optimal integration and functionality.

Services Overview
-----------------

The EduTelligence suite includes several microservices. The following services are currently integrated with Artemis:

* **Iris** - AI Virtual Tutor powered by Pyris for intelligent student assistance
* **Athena** - Automated Assessment System for text, modeling, and programming exercises

Additional services are available in the EduTelligence suite for advanced deployments. For detailed information about all available services, please refer to the `EduTelligence repository <https://github.com/ls1intum/edutelligence>`_.

For detailed setup instructions for the integrated services, please refer to their individual documentation pages below.

.. toctree::
   :includehidden:
   :maxdepth: 2

   iris
   pyris
   athena

Repository and Documentation
----------------------------

* **Main Repository**: `https://github.com/ls1intum/edutelligence <https://github.com/ls1intum/edutelligence>`_
* **Documentation**: Each service includes comprehensive setup and configuration documentation
* **Community**: Join the discussion and contribute to the EduTelligence ecosystem

Getting Started
---------------

1. **Check Compatibility**: Verify that your Artemis version is compatible with the EduTelligence version you plan to use
2. **Choose Services**: Select which EduTelligence services you want to integrate based on your needs
3. **Follow Setup Guides**: Use the individual service setup guides linked above
4. **Configure Integration**: Update your Artemis configuration to connect with the EduTelligence services