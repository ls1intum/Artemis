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

## Development Workflow

Find here [a guide](doc/setup/SETUP.md) on how to setup your local development environment.

## CSS Guidelines

We are using [Scss](https://sass-lang.com) to write modular, reusable css.

We have a couple of global scss files in `webapp/content` but encourage [component dependent css with angular's styleUrls](https://angular.io/guide/component-styles).

From a methodology viewpoint we encourage the use of [BEM](http://getbem.com/introduction/).
```
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
`<div class="d-flex ml-2">some content</div>`


## Testing

We create unit & integration tests for the Artemis server and client.
Adding tests is an integral part of any pull request - please be aware that your pull request will not be approved until you provide automated tests for your implementation!

### Server Testing

We use the [Spring Boot testing utilities](https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-testing.html) for server side testing.

Location of test files: `src/test/java`

Execution command:      `./gradlew executeTests`

### Client Testing

We use [Jest](https://jestjs.io/) for client side testing.

For convenience purposes we have [Sinon](https://sinonjs.org/) and [Chai](https://www.chaijs.com/) as dependencies, so that easy stubbing/mocking is possible ([sinon-chai](https://github.com/domenic/sinon-chai)).

Location of test files: `src/test/javascript`

Execution command:      `yarn test`

The folder structure is further divided into:

- component
- integration
- service

The tests located in the folder `/app` are not working at them moment and are not included in the test runs.
