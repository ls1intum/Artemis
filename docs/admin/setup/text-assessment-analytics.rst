Text Assessment Analytics Service
=================================

Text Assessment Analytics is an internal analytics service used to gather data regarding the features of the text
assessment process. Certain assessment events are tracked:

1. Adding new feedback on a manually selected block
2. Adding new feedback on an automatically selected block
3. Deleting a feedback
4. Clicking to resolve feedback conflicts
5. Clicking to view origin submission of automatically generated feedback
6. Hovering over the text assessment feedback impact warning
7. Editing/Discarding an automatically generated feedback
8. Clicking the Submit button when assessing a text submission
9. Clicking the Assess Next button when assessing a text submission

These events are tracked by attaching a POST call to the respective DOM elements on the client side.
The POST call accesses the **TextAssessmentEventResource** which then adds the events in its respective table.
This feature is disabled by default. We can enable it by modifying the configuration in the file:
``src/main/resources/config/application-artemis.yml`` like so:

.. code:: yaml

   info:
      text-assessment-analytics-enabled: true
