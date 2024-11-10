.. _cleanup:

Cleanup Service
===============

Artemis provides a feature to delete data from older courses.

.. _cleanup-menu:

.. figure:: cleanup/cleanup-menu.png
    :align: center
    :alt: Cleanup view

As shown in the image, administrators can delete the following data types:

* Plagiarism results with an undecided outcome
* Orphaned data
* Non-rated results from older courses
* Rated results from older courses

Since orphaned data has no connections to other data by nature, it is deleted without considering specific dates.
For other types, administrators can track the related exercises and courses.
When a cleanup operation is performed with specified "from" and "to" dates, all data associated with that type and related to courses that started after the "from" date and ended before the "to" date is deleted.

Data Deletion by Operation Type
------------------------------------------

1. **Orphaned Data**:
      - Long Feedback Text with feedback that has no results
      - Text Block with feedback that has no results
      - Feedback records without results
      - Student and team scores where either a student or a team is specified
      - Long Feedback Text where both participation and submission are missing
      - Text Block where the referenced feedback has no associated participation or submission
      - Feedback with no associated participation or submission
      - All Ratings where the related result has no associated participation or submission
      - Results without associated participation or submission

2. **Plagiarism Results with an Undecided Outcome**:
      - All plagiarism comparisons related to courses within the specified dates and marked as undecided.

3. **Rated and Non-rated Results**:
      - Both types follow the same logic, except for the rating status of results (rated vs. non-rated).
      - For each type, only the latest valid result within a participation is retained, while all others are deleted.
      - Because direct result deletion is restricted due to data integrity reasons, Artemis first removes associated data for results scheduled for deletion, including Long Feedback Text, Text Block, Feedback, Student Score, and Team Score.

Artemis also records the date of the last cleanup operation, as seen in the last column of the table shown in the image.
