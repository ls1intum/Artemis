******************************************
Development Process (How to Merge Changes)
******************************************

.. contents:: Content of this document
    :local:
    :depth: 2

Naming Conventions for GitHub Pull Requests
===========================================

1. The first term is a main feature of Artemis and is using code highlighting, e.g.  “``Programming exercises``:”.

    1. Possible feature tags are: ``Programming exercises``, ``Modeling exercises``, ``Text exercises``, ``Quiz exercises``, ``File upload exercises``, ``Lectures``, ``Exam mode``, ``Assessment``, ``Communication``, ``Notifications``. More tags are possible if they make sense.
    2. If no feature makes sense, and it is a pure development or test improvement, we use the term “``Development``:”. More tags are also possible if they make sense.
    3. Everything else belongs to the ``General`` category.

2. The colon is not highlighted.

3. After the colon, there should be a verbal form that is understandable by end users and non-technical persons, because this will automatically become part of the release notes.

    1. The text should be short, non-capitalized (except the first word) and should include the most important keywords. Do not repeat the feature if it is possible.
    2. We generally distinguish between bugfixes (the verb “Fix”) and improvements (all kinds of verbs) in the release notes. This should be immediately clear from the title.
    3. Good examples:

        - “Allow instructors to delete submissions in the participation detail view”
        - “Fix an issue when clicking on the start exercise button”
        - “Add the possibility for instructors to define submission policies”



Steps to Create and Merge a Pull Request
========================================

0. Precondition -> only Developer
---------------------------------

* Limit yourself to one functionality per pull request.
* Split up your task in multiple branches & pull requests if necessary.
* `Commit Early, Commit Often, Perfect Later, Publish Once. <https://speakerdeck.com/lemiorhan/10-git-anti-patterns-you-should-be-aware-of>`_

1. Start Implementation -> only Developer
-----------------------------------------

* `Open a draft pull request. <https://docs.github.com/en/github/collaborating-with-issues-and-pull-requests/creating-a-pull-request>`_ This allows for code related questions and discussions.

2. Implementation is "done" -> only Developer
---------------------------------------------

* Make sure all steps in the `Checklist <https://github.com/ls1intum/Artemis/blob/develop/.github/PULL_REQUEST_TEMPLATE.md>`_ are completed.
* Add or update the "Steps for Testing" in the description of your pull request.
* Make sure that the changes in the pull request are only the ones necessary.
* Mark the pull request as `ready for review. <https://docs.github.com/en/github/collaborating-with-issues-and-pull-requests/changing-the-stage-of-a-pull-request>`_

3. Review
---------

Developer
^^^^^^^^^
* Organize or join a testing session. Especially for large pull requests this makes testing a lot easier.
* Actively look for reviews. Do not just open the pull request and wait.

Reviewer
^^^^^^^^
* Perform the "Steps for Testing" and verify that the new functionality is working as expected.
* Verify that related functionality is still working as expected.
* Check the changes to
    * conform with the code style.
    * make sure you can easily understand the code.
    * make sure that (extensive) comments are present where deemed necessary.
    * performance is reasonable (e.g. number of database queries or HTTP calls).
* Submit your comments and status ((thumbs up) Approve or (thumbs down) Request Changes) using GitHub.
    * Explain what you did (test, review code) and on which test server in the review comment.

4. Respond to review
--------------------

Developer
^^^^^^^^^
* Use the pull request to discuss comments or ask questions.
* Update your code where necessary.
* Revert to draft if the changes will take a while during which review is not needed/possible.
* Set back to ready for review afterwards.
* Notify the reviewer(s) once your revised version is ready for the next review.
* Comment on "inline comments" (e.g. "Done").

Reviewer
^^^^^^^^
* Respond to questions raised by the reviewer.
* Mark conversations as resolved if the change is sufficient.

Iterate steps 3 & 4 until ready for merge (all reviewers approve (thumbs up))
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

5. Merge
--------
A project maintainer merges your changes into the ``develop`` branch.



Stale Bot
=========

If the pull request doesn't have any activity for at least 7 days, the stale bot will mark the PR as `stale``.
The `stale` status can simply be removed by adding a comment or a commit to the PR. 
After the PR is marked as `stale` the bot waits another 14 days until the PR will be closed (21 days in total).
Adding activity to the PR will remove the `stale` label again and reset the stale timer. 
To prevent the bot from adding the `stale` label to the PR in the first place, the `no-stale` label 
can be used. This label should only be utilized, if the PR is blocked by another PR or the PR needs 
help from another developer. 

A full documentation on this bit can be found here:
https://github.com/actions/stale
