***********
Code Review
***********

STEPS
======

0. Precondition -> only Developer
==================================

* Limit yourself to one functionality per pull request
* Split up your task in multiple branches & pull requests if necessary
* `Commit Early, Commit Often, Perfect Later, Publish Once <https://speakerdeck.com/lemiorhan/10-git-anti-patterns-you-should-be-aware-of>`_

1. Start Implementation -> only Developer
=========================================

* `Open draft pull request. <https://docs.github.com/en/github/collaborating-with-issues-and-pull-requests/creating-a-pull-request>`_ Allows for code related questions and discussions

2. Implementation is "done" -> only Developer
=============================================

* Make sure all steps in the `Checklist <https://github.com/ls1intum/Artemis/blob/develop/.github/PULL_REQUEST_TEMPLATE.md>`_ are completed
* Add or update the "Steps for Testing" in the description of your pull request
* Mark the pull request as `ready for review <https://docs.github.com/en/github/collaborating-with-issues-and-pull-requests/changing-the-stage-of-a-pull-request>`_
* Make sure that the changes in the pull request are only the ones necessary

3. Review -> only Reviewer
==========================

* Perform the "Steps for Testing" and verify that the new functionality is working as expected
* Verify that related functionality is still working as expected
* Check the Changes to conform with the code style
* Make sure you can easily understand the code
* Make sure that (extensive) comments are present where deemed necessary
* Performance is reasonable (e.g. Number of database queries or HTTP calls)
* Submit your comments and status (Approve or Request Changes) using GitHub

4. Respond to review -> Developer & Reviewer
============================================

Developer
=========
* Use the pull request to discuss comments or ask questions
* Update your code where necessary
* Notify the reviewer(s) once your revised version is ready for the next review
* Comment on "inline comments" (e.g. "Done")

Reviewer
=========
* Respond to questions raised by the reviewer
* Mark Conversations as Resolved if the change is sufficient

Iterate steps 3 & 4 until ready for merge (= all reviewers approve (thumbs up) )
================================================================================

4. Merge
========
A Project Maintainer merges your changes to develop

