<!-- Thanks for contributing to Artemis! Before you submit your pull request, please make sure to check the following boxes by putting an x in the [ ] (don't: [x ], [ x], do: [x]) -->
<!-- If your pull request is not ready for review yet, create a draft pull request! -->

### Checklist
#### General
<!-- You only need to choose one of the first two check items: Generally, test on the test servers. -->
<!-- If it's only a small change, testing it locally is acceptable and you may remove the first checkmark. If you are unsure, please test on the test servers. -->
- [ ] I tested **all** changes and their related features with **all** corresponding user types on a test server.
- [ ] This is a small issue that I tested locally and was confirmed by another developer on a test server.
- [ ] Language: I followed the [guidelines for inclusive, diversity-sensitive, and appreciative language](https://docs.artemis.ase.in.tum.de/dev/guidelines/language-guidelines/).
- [ ] I chose a title conforming to the [naming conventions for pull requests](https://artemis-platform.readthedocs.io/en/latest/dev/guidelines/development-process.html#naming-conventions-for-github-pull-requests).
#### Server
- [ ] I followed the [coding and design guidelines](https://docs.artemis.ase.in.tum.de/dev/guidelines/server/).
- [ ] I added multiple integration tests (Spring) related to the features (with a high test coverage).
- [ ] I added `@PreAuthorize` and checked the course groups for all new REST Calls (security).
- [ ] I implemented the changes with a good performance and prevented too many database calls.
- [ ] I documented the Java code using JavaDoc style.
#### Client
- [ ] I followed the [coding and design guidelines](https://docs.artemis.ase.in.tum.de/dev/guidelines/client/).
- [ ] I added multiple integration tests (Jest) related to the features (with a high test coverage), while following the [test guidelines](https://docs.artemis.ase.in.tum.de/dev/guidelines/client-tests/).
- [ ] I added `authorities` to all new routes and checked the course groups for displaying navigation elements (links, buttons).
- [ ] I documented the TypeScript code using JSDoc style.
- [ ] I added multiple screenshots/screencasts of my UI changes.
- [ ] I translated all newly inserted strings into English and German.
#### Changes affecting Programming Exercises
- [ ] I tested **all** changes and their related features with **all** corresponding user types on Test Server 1 (Atlassian Suite).
- [ ] I tested **all** changes and their related features with **all** corresponding user types on Test Server 2 (Jenkins and Gitlab).

### Motivation and Context
<!-- Why is this change required? What problem does it solve? -->
<!-- If it fixes an open issue, please link to the issue here. -->

### Description
<!-- Describe your changes in detail -->

### Steps for Testing
<!-- Please describe in detail how the reviewer can test your changes. -->
Prerequisites:
- 1 Instructor
- 2 Students
- 1 Programming Exercise with Complaints enabled

1. Log in to Artemis
2. Navigate to Course Administration
3. ...

### Review Progress
<!-- Each Pull Request should be reviewed by at least two other developers. The code as well as the functionality (= manual test) needs to be reviewed. -->
<!-- The reviewer or author check the following boxes depending on what was reviewed or tested. All boxes should be checked before merge. -->
<!-- You can add additional checkboxes if it makes sense to only review parts of the code or functionality. -->
<!-- When changes are pushed, uncheck the affected boxes. (Not all changes require full re-reviews.) -->

#### Code Review
- [ ] Review 1
- [ ] Review 2
#### Manual Tests
- [ ] Test 1
- [ ] Test 2

### Test Coverage
<!-- Please add the test coverages for all changed files here. You can see this when executing the tests locally (see build.gradle and package.json) or when looking into the corresponding Bamboo build plan. -->
<!-- Please use the schema "Branches % | Lines %" -->
<!-- Lines are the main reference but a significantly lower branch percentage can indicate missing edge cases in the tests. -->
<!-- - ExerciseService.java: 85% | 77% -->
<!-- - programming-exercise.component.ts: 13% | 95% -->

### Screenshots
<!-- Add screenshots to demonstrate the changes in the UI. -->
<!-- Create a GIF file from a screen recording in a docker container https://toub.es/2017/09/11/high-quality-gif-with-ffmpeg-and-docker/ -->
