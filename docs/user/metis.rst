.. _metis:

Discussion
==========

.. contents:: Content of this document
    :local:
    :depth: 2

Overview
--------

Artemis enables students, tutors and instructors to actively participate in discussions which contribute to a better learning experience.
Various discussion features allow students to engage with peers, and ask all kinds of questions whereas instructors and tutors can provide general course information and answer content-related questions.
At first, we want to distinguish between different types of discussion contributions, so called ``Posts``.
Each of the type corresponds to a certain context, the post is created in.
Those contexts can be lectures, exercise or the course in general.
You will afterwards find more information on specific features and how to use them in the following.
In general, Artemis courses will by default enable all the discussion features.
In case you do not want to provide users with these features, you can disable this feature on course creation by unchecking the respective checkbox (``Enable postings by students``) - it can also be edited afterwards.

Please note that we refer to tutors and instructors that are involved with student communication as moderators collectively in the following.

Lecture Posts
^^^^^^^^^^^^^
Posts, that are specifically related to a certain lecture.
These posts have to be created on the detail page of the lecture they belong to.
However, they are listed in the course discussion overview (see :ref:`Metis Course-wide Posts & Course Discussion Overview`).

Exemplary lecture detail page with posts:
|lecture-posts|

Exercise Posts
^^^^^^^^^^^^^^

Posts, that are specifically related to a certain exercise.
These posts have to be created on the detail page of the exercise they belong to.

Exemplary exercise detail page with posts:
|exercise-posts|

.. _Metis Course-wide Posts & Course Discussion Overview:
Course-wide Posts & Course Discussion Overview
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Besides lecture or exercise related questions, Artemis offers a third type of posts: posts with course-wide topics.
Such topics can be ``Organization`` or ``Tech Support`` and they address questions or information that is of course-wide relevance.
These posts can only be created on the course discussion overview which is showed in the screenshot below.

The ``Discussion`` space of an Artemis course serves as overview for *all* posts in a course.
Hence, course-wide posts as well as exercise posts and lecture posts are listed.
At the overview level, users can easily make use of features to query, sort and filter existing posts.
|course-posts|

Features for Users
------------------

This section captures the most important features that are offered to any Artemis user.

Search, Filter and Sort
^^^^^^^^^^^^^^^^^^^^^^^^

On the course overview, a user can query *all* existing posts by different criteria.
This can be a free-text search, context filters (i.e., a certain lecture, exercise or course-wide topic), or other post characteristics such as if it is already resolved.
By using different sort configurations, users can find the information they are looking for at the top of the list: for example the post that got the most votes, or the post that was created most recently.

React on Posts
^^^^^^^^^^^^^^

To foster interaction we integrate the well-known emoji reaction bar.
Each user can react on every post by making use of the emoji selection button.
The ``+`` emoji serves as upvoting reaction, which influences the display order.
|react-on-posts|

Reference Posts of Others
^^^^^^^^^^^^^^^^^^^^^^^^^

If users want to refer to other posts, they can integrate a simple pattern including the hashtag combined with the post identifier.
A post's identifier appended to the post title (such as seen in the screenshot).

With #ID

Find Duplicates of Posts
^^^^^^^^^^^^^^^^^^^^^^^^

In order to prevent duplicated questions from being posted, we integrate a duplication check that runs during post creation.
We strongly recommend users that create a post, to check the automatically provided list of similar posts to find out if a the question in mind has already be asked and resolved in the best case.


Mark Your Post As Resolved
^^^^^^^^^^^^^^^^^^^^^^^^^^

Marking a post as resolved will indicate to other users that the posted question is resolved and does not need any further input.
This can be done by selecting one of the given answers as resolving, i.e., as correct as shown in the screenshot.
Note, that only the author of the post as well as a moderator can perform this action.
This is helpful for moderators to search for open questions that they might want to address, e.g., by applying the according filter in the course overview.
It also highlights the correct answer for other students that have a similar problem and search for a suitable solution.

Tag Your Post
^^^^^^^^^^^^^

Tagging a post will further narrow down the post purpose or content in precise and descriptive buzzwords, that might follow a course-specific taxonomy.

Features for Moderators
-------------------
The following features are only available for moderators, not for students.

Move Posts
^^^^^^^^^^

In case a post is created in a context (lecture, exercise, course-wide overview), tutors can change the context in the edit mode of the post.
By changing the context, for example from a certain exercise to a course-wide overview, the post will automatically be moved.
In the example at hand, the post will not be shown on the according exercise page anymore but rather only in the course-wide overview, associated with that certain course-wide topic.

Pin Posts
^^^^^^^^^^

By clicking the pushpin icon next to the reaction button of a post, a moderator can *pin* the discussion.
As a consequence, it is listed at the top of a list to receive higher attention.

Archive Posts
^^^^^^^^^^^^^

As a complement to pinning, i.e., highlighting posts, a moderator can archive posts and thereby but them at the bottom of a list of posts.
This can ba achieved by clicking the folder icon next to the reaction button.
Moderators should be aware, that this reduces the visibility of posts.

Features for Instructors
------------------------

The following feature is only available for instructors that act as moderators.

Post Announcements
^^^^^^^^^^^^^^^^^^
Instructors can create course-wide posts that serve as *Announcements*, that target every course participant and have higher relevance than normal posts.
These types of posts can be created on the discussion course overview level by selecting the topic ``Announcement``.
As soon as the announcement is created, all participants that did not actively refrained from being notified, will receive an email containing the announcement content.
Additionally, those post visually differ from normal posts and are displayed on top of the discussion overview.

.. |lecture-posts| image:: metis/lecture-posts.png
    :width: 500
.. |exercise-posts| image:: metis/exercise-posts.png
    :width: 500
.. |course-posts| image:: metis/course-posts.png
    :width: 500
.. |react-on-posts| image:: metis/react-on-posts.png
    :width: 200
