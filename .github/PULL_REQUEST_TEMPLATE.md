<!-- Thanks for contributing to Artemis! Before you submit your pull request, please make sure to check all tasks by putting an x in the [ ] (don't: [x ], [ x], do: [x]). Remove not applicable tasks and do not leave them unchecked -->
<!-- If your pull request is not ready for review yet, create a draft pull request! -->

### Checklist
#### General
<!-- Remove tasks that are not applicable for your PR. Please only put the PR into ready for review, if all relevant tasks are checked! -->
<!-- You only need to choose one of the first two check items: Generally, test on the test servers. -->
<!-- If it's only a small change, testing it locally is acceptable, and you may remove the first checkmark. If you are unsure, please test on the test servers. -->
- [ ] I tested **all** changes and their related features with **all** corresponding user types on a test server.
- [ ] This is a small issue that I tested locally and was confirmed by another developer on a test server.
- [ ] Language: I followed the [guidelines for inclusive, diversity-sensitive, and appreciative language](https://docs.artemis.cit.tum.de/dev/guidelines/language-guidelines/).
- [ ] I chose a title conforming to the [naming conventions for pull requests](https://docs.artemis.cit.tum.de/dev/development-process/development-process.html#naming-conventions-for-github-pull-requests).


#### Server
- [ ] **Important**: I implemented the changes with a [very good performance](https://docs.artemis.cit.tum.de/dev/guidelines/performance/) and prevented too many (unnecessary) and too complex database calls.
- [ ] I **strictly** followed the principle of **data economy** for all database calls.
- [ ] I **strictly** followed the [server coding and design guidelines](https://docs.artemis.cit.tum.de/dev/guidelines/server/).
- [ ] I added multiple integration tests (Spring) related to the features (with a high test coverage).
- [ ] I added pre-authorization annotations according to the [guidelines](https://docs.artemis.cit.tum.de/dev/guidelines/server/#rest-endpoint-best-practices-for-authorization) and checked the course groups for all new REST Calls (security).
- [ ] I documented the Java code using JavaDoc style.


#### Client
- [ ] **Important**: I implemented the changes with a very good performance, prevented too many (unnecessary) REST calls and made sure the UI is responsive, even with large data (e.g. using paging).
- [ ] I **strictly** followed the principle of **data economy** for all client-server REST calls.
- [ ] I **strictly** followed the [client coding and design guidelines](https://docs.artemis.cit.tum.de/dev/guidelines/client/).
- [ ] Following the [theming guidelines](https://docs.artemis.cit.tum.de/dev/guidelines/client-design/), I specified colors only in the theming variable files and checked that the changes look consistent in both the light and the dark theme.
- [ ] I added multiple integration tests (Jest) related to the features (with a high test coverage), while following the [test guidelines](https://docs.artemis.cit.tum.de/dev/guidelines/client-tests/).
- [ ] I added `authorities` to all new routes and checked the course groups for displaying navigation elements (links, buttons).
- [ ] I documented the TypeScript code using JSDoc style.
- [ ] I added multiple screenshots/screencasts of my UI changes.
- [ ] I translated all newly inserted strings into English and German.


#### Changes affecting Programming Exercises
- [ ] **High priority**: I tested **all** changes and their related features with **all** corresponding user types on a test server configured with the **integrated lifecycle setup** (LocalVC and LocalCI).
- [ ] I tested **all** changes and their related features with **all** corresponding user types on a test server configured with **Gitlab** and **Jenkins**.


### Motivation and Context
<!-- Why is this change required? What problem does it solve? -->
<!-- If it fixes an open issue, please link to the issue here. -->


### Description
<!-- Describe your changes in detail -->


### Steps for Testing
<!-- Please describe in detail how reviewers can test your changes. Make sure to take all related features and views into account! Below is an example that you can refine. -->
Prerequisites:
- 1 Instructor
- 2 Students
- 1 Programming Exercise with Complaints enabled

1. Log in to Artemis
2. Navigate to Course Administration
3. ...

#### Exam Mode Testing
<!-- If this PR changes some components that are also used in the exam mode, the PR needs additional testing that the exam mode is still working as expected. -->
<!-- If the testing steps above already describe the exam mode or the exam mode cannot be affected by this PR in any way, you can leave this out. -->

Prerequisites:
- 1 Instructor
- 2 Students
- 1 Exam with a Programming Exercise

1. Log in to Artemis
2. Participate in the exam as a student
3. Make sure that the UI of the programming exercise in the exam mode stays unchanged. You can use the [exam mode documentation](https://docs.artemis.cit.tum.de/user/exam_mode/) as reference.
4. ...

### Testserver States
> [!NOTE]
> These badges show the state of the test servers.
> Green = Currently available, Red = Currently locked
> Click on the badges to get to the test servers.

[![](https://byob.yarr.is/ls1intum/Artemis/artemis-test1)](https://artemis-test1.artemis.cit.tum.de)
[![](https://byob.yarr.is/ls1intum/Artemis/artemis-test2)](https://artemis-test2.artemis.cit.tum.de)
[![](https://byob.yarr.is/ls1intum/Artemis/artemis-test3)](https://artemis-test3.artemis.cit.tum.de)
[![](https://byob.yarr.is/ls1intum/Artemis/artemis-test4)](https://artemis-test4.artemis.cit.tum.de)
[![](https://byob.yarr.is/ls1intum/Artemis/artemis-test5)](https://artemis-test5.artemis.cit.tum.de)
[![](https://byob.yarr.is/ls1intum/Artemis/artemis-test6)](https://artemis-test6.artemis.cit.tum.de)
[![](https://byob.yarr.is/ls1intum/Artemis/artemis-test9)](https://artemis-test9.artemis.cit.tum.de)

### Review Progress
<!-- Each PR should be reviewed by at least two other developers. The code, the functionality (= manual test) and the exam mode need to be reviewed. -->
<!-- The reviewer or author check the following boxes depending on what was reviewed or tested. All boxes should be checked before merge. -->
<!-- You can add additional checkboxes if it makes sense to only review parts of the code or functionality. -->
<!-- When changes are pushed, uncheck the affected boxes. (Not all changes require full re-reviews.) -->
<!-- All PRs that might affect the exam mode (e.g. change a client component that is also used in the exam mode) need an additional verification that the exam mode still works. -->

#### Performance Review
<!-- See [Large Course Setup](https://github.com/ls1intum/Artemis/tree/develop/supporting_scripts/course-scripts/quick-course-setup) for the automation script that handles large course setup. -->
- [ ] I (as a reviewer) confirm that the client changes (in particular related to REST calls and UI responsiveness) are implemented with a very good performance even for very large courses with more than 2000 students.
- [ ] I (as a reviewer) confirm that the server changes (in particular related to database calls) are implemented with a very good performance even for very large courses with more than 2000 students.
#### Code Review
- [ ] Code Review 1
- [ ] Code Review 2
#### Manual Tests
- [ ] Test 1
- [ ] Test 2
#### Exam Mode Test
- [ ] Test 1
- [ ] Test 2
#### Performance Tests
- [ ] Test 1
- [ ] Test 2

### Test Coverage
<!-- Please add the test coverages for all changed files here. You can see this when executing the tests locally (see build.gradle and package.json) or when looking into the corresponding Bamboo build plan. -->
<!-- The line coverage must be above 90% for changes files and you must use extensive and useful assertions for server tests and expect statements for client tests. -->
<!-- Note: Use the table below and confirm in the last column that you have implemented extensive assertions for server tests and expect statements for client tests. -->
<!--       You can use `supporting_script/generate_code_cov_table/generate_code_cov_table.py` to automatically generate one from the corresponding Bamboo build plan artefacts. -->
<!--       Remove rows with only trivial changes from the table. -->
<!--
| Class/File | Line Coverage | Confirmation (assert/expect) |
|------------|--------------:|-----------------------------:|
| ExerciseService.java | 85% | ✅                           |
| programming-exercise.component.ts | 95% | ✅              |
-->

### Screenshots
<!-- Add screenshots to demonstrate the changes in the UI. Remove the section if you did not change the UI. -->
<!-- Create a GIF file from a screen recording in a docker container https://toub.es/2017/09/11/high-quality-gif-with-ffmpeg-and-docker/ -->
