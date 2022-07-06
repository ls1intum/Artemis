# Contributing Guide for Artemis

Please read this guide before creating a pull request, otherwise your contribution might not be approved.

## Branch Organization

All pull request branches are created from develop.

We use the following structure for branch names:

\<type\>/\<area\>/\<short-description\>

Possible types are:

- feature
- enhancement
- bugfix
- hotfix

The pull request template will provide additional information on the requirement for the integration of changes into Artemis.  
Once the changes in your pull request are approved by one of our reviewers, they can be merged into develop.

## Pull request (PR) guidelines:

- **Merge fast**: PRs should only be open for a couple of days.
- **Small packages**: PRs should be as small as possible and ideally concentrate on a single topic. Features should be split up into multiple PRs if it makes sense.
- **Until the PR is _ready-for-review_, the PR should be a [Draft PR](https://help.github.com/en/github/collaborating-with-issues-and-pull-requests/about-pull-requests#draft-pull-requests)**
- **Definition of done**: Before requesting a code review make sure that the PR is _ready-for-review_:
  - The PR template is filled out completely, containing as much information as needed to understand the feature.
  - All tasks from the template checklist are done and checked off (writing tests, adding screenshots, etc.).
  - The branch of the PR is up-to-date with develop.
  - The last build of the PR is successful.

## Code review guidelines

- **Check out the code and test it**: Testing the feature/enhancement/bugfix helps to understand the code.
- **Respect the PR scope**: Bugfixes, enhancements or implementations that are unrelated to the PRs topic should not be enforced in a code review. 
In this case the reviewer or PR maintainer needs to make sure to create an issue for this topic on GitHub or the internal task tracking tool so it is not lost.
- **Code style is not part of a code review**: Code style and linting issues are not part of the review process. If issues in code style or linting arise, the linters and auto formatters used in our CI tools need to be updated.
- **Enforce guidelines**: Enforcing technical & design guidelines is an integral part of the code review (e.g. consistent REST urls).
- **Mark optional items**: Review items that are optional from the reviewers' perspective should be marked as such (e.g. "Optional: You could also do this with...")
- **Explain your rational**: If the reviewer requests a change, the reasoning behind the change should be explained (e.g. not "Please change X to Y", but "Please change X to Y, because this would improve Z")

## Development Workflow

Find here [a guide](docs/dev/setup.rst) on how to setup your local development environment.

## Route Naming Conventions

- Always use **kebab-case** (e.g. "/exampleAssessment" → "/example-assessment")
- The routes should follow the general structure entity > entityId > sub-entity ... (e.g. "/exercises/{exerciseId}/participations")
- Use **plural for server route's** entities and **singular for client route's** entities
- Specify the key entity at the end of the route (e.g. "text-editor/participations/{participationId}" should be changed to "participations/{participationId}/text-editor")
- Never specify an id that is used only for consistency and not used in the code (e.g. GET "/courses/{courseId}/exercises/{exerciseId}/participations/{participationId}/submissions/{submissionId}" can be simplified to GET "/submissions/{submissionId}" because all other entities than the submission are either not needed or can be loaded without the need to specify the id)

## CSS Guidelines

We are using [Scss](https://sass-lang.com) to write modular, reusable css.

We have a couple of global scss files in `webapp/content` but encourage [component dependent css with angular's styleUrls](https://angular.io/guide/component-styles).

From a methodology viewpoint we encourage the use of [BEM](http://getbem.com/introduction/).
```scss
.my-container {
    // container styles
    &__content {
        // content styles
        &--modifier {
            // modifier styles
        }
    }
}
```

Within the component html files, we encourage the use of [bootstrap css](https://getbootstrap.com/).

Encouraged html styling:
`<div class="d-flex ms-2">some content</div>`

## Testing

We create unit & integration tests for the Artemis server and client.
Adding tests is an integral part of any pull request - please be aware that your pull request will not be approved until you provide automated tests for your implementation!
Our goal is to keep the test coverage above 80%.

### Server Testing

We use the [Spring Boot testing utilities](https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-testing.html) for server side testing.

Location of test files: `src/test/java`

Execution command:      `./gradlew executeTests`

### Client Testing

We use [Jest](https://jestjs.io/) for client side testing.

For convenience purposes we have [Sinon](https://sinonjs.org/) and [Chai](https://www.chaijs.com/) as dependencies, so that easy stubbing/mocking is possible ([sinon-chai](https://github.com/domenic/sinon-chai)).

Location of test files: `src/test/javascript`

Execution command:      `npm run test`

The folder structure is further divided into:

- component
- integration
- service

The tests located in the folder `/app` are not working at the moment and are not included in the test runs.

### Mutation Testing (Server-only)

We (partially) use [PIT Mutation Testing](https://pitest.org/) for mutation testing on server side. It mutates the code to break or modify functionality and re-runs the tests to see if the tests can catch (kill) the mutations by failing. The test quality is assessed by checking the percentage of the mutations killed.

Since there will be multiple mutations and re-runs, it can take significantly longer to execute compared to other server tests.

Location of test files: `src/test/java` (Uses the same files in Server Testing)

Execution command:      `./gradlew pitest -x webapp`

The report will be at:   `build/reports/pitest/index.html`
