<!-- Thanks for contributing to Artemis! Before you submit your pull request, please make sure to check the following boxes by putting an x in the [ ] (don't: [x ], [ x], do: [x]) -->
<!-- If your pull request is not ready for review yet, create a draft pull request! -->

### Checklist
- [ ] I tested *all* changes and *all* related features with different users (student, tutor, instructor, admin) on the test server https://artemistest.ase.in.tum.de.
- [ ] Server: I added multiple integration tests (Spring) related to the features
- [ ] Server: I added `@PreAuthorize` and check the course groups for all new REST Calls (security)
- [ ] Server: I implemented the changes with a good performance and prevented too many database calls
- [ ] Server: I documented the Java code using JavaDoc style.
- [ ] Client: I added multiple integration tests (Jest) related to the features
- [ ] Client: I added `authorities` to all new routes and check the course groups for displaying navigation elements (links, buttons)
- [ ] Client: I documented the TypeScript code using JSDoc style.
- [ ] Client: I added multiple screenshots/screencasts of my UI changes
- [ ] Client: I translated all the newly inserted strings into German and English

### Motivation and Context
<!-- Why is this change required? What problem does it solve? -->
<!-- If it fixes an open issue, please link to the issue here. -->

### Description
<!-- Describe your changes in detail -->

### Steps for Testing
<!-- Please describe in detail how the reviewer can test your changes. -->

1. Log in to Artemis
2. Navigate to Course Administration
3. ...

### Screenshots
<!-- Add screenshots to demonstrate the changes in the UI. -->
<!-- Create a GIF file from a screen recording in a docker container https://toub.es/2017/09/11/high-quality-gif-with-ffmpeg-and-docker/ -->
