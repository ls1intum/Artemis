Programming exercises use a version control system called **Git**. Git is a distributed version control system that allows you to track changes in your code.
These changes are stored in a repository. A repository is a collection of files and directories that are tracked by Git.
How to navigate through the repository and view the code is explained in the following sections. This guide is divided into two parts: one for students and one for instructors.
It is demonstrated using course programming exercises, but the same principles apply to exams as well.

Access Repositories as Student
""""""""""""""""""""""""""""""

As a student, you can use the repository to view your course submissions.
To access your submissions, you need to navigate to the exercise, press the '*Code*' button, and click on |open-repository-button|.

.. figure:: local-vc/open-repository-student.png
   :alt: Opening the repository in the student view
   :align: center

   Opening the repository (student view)

After pressing the button, you will be redirected to the **Current Repository Content** page of the repository.
This view shows the current content of the repository with the latest changes in a read-only code editor. You can view the code, but you cannot edit it.
You can also navigate through the whole code by clicking on the file names in the file browser on the left side.
To the right side of the screen, above the problem statement, you can see the result of the submission. You can also copy the repository URI by pressing
the '*Code*' button to clone the repository to your local machine and see the full commit history by pressing the '*Open Commit History*' button. Finally, you can
download the repository as a ZIP file by pressing the '*Download Repository*' button.

.. figure:: local-vc/current-repository-content-view.png
   :alt: Current Repository Content
   :align: center

   Current Repository Content

The *Commit History* page shows all the commits that you uploaded to the repository. The commits are ordered from the latest to the oldest.
You can see the commit message, the author of the commit, the date of the commit, the commit hash and the result of the commit.
If you created a submission with multiple commits, you can see all of them here but only the last commit has the result of the submission.
Lastly, you can also view the code of the commit by pressing on the commit hash.

.. figure:: local-vc/commit-history-view.png
   :alt: Commit History View
   :align: center

   Commit History View

By clicking on a commit hash, you are redirected to the **Commit Diff** page. This page shows the changes that you made in this commit.
You can see the changes in the code, the files that have been added, the files that have been deleted and the files that have been modified.
Green color indicates the lines that have been added and red color indicates the lines that have been deleted.
You can also see the commit message, the author of the commit, the date of the commit and the commit hash.

.. figure:: local-vc/commit-diff-view.png
   :alt: Commit Diff View
   :align: center

   Commit Diff View

Access Repositories as Instructor
"""""""""""""""""""""""""""""""""

As an instructor, you can use the repository to view the submissions of your students. For this purpose, you need to navigate to the exercise participations page.
Here you can see all the students’ participations for the exercise and navigate to the repository of a student by pressing the '*Code*' button and clicking on |open-repository-button|.
This will show the repository of the student just like it is shown to the student.

.. figure:: local-vc/open-repository-instructor-participations.png
   :alt: Open Repository Button in Participations Page
   :align: center

   Open Repository Button in Participations Page

You can also see the repositories for an exercises **solution**, **template** and **test** repositories on the exercise management page.
You can navigate to these repositories by pressing the '*Code*' button and clicking |open-repository-button| as described above.

.. figure:: local-vc/course-management-repositories.png
   :alt: Solution, Template and Test Repositories in Exercise Management Page
   :align: center

   Solution, Template and Test Repositories in Exercise Management Page

.. |open-repository-button| image:: local-vc/open-repository-button.png
   :scale: 50%
