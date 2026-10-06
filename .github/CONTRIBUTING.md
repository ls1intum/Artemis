# Contributing to Artemis

Thanks for your interest in Artemis. Contributions of every size are welcome, from a typo in the
documentation to a new feature. This page is the short version: what to do, in which order, and where
the detailed guidelines live.

Please read the [Code of Conduct](./CODE_OF_CONDUCT.md) before you start, and note the
[identity and transparency policy](#identity-and-transparency-policy) below, which applies to every
contribution.

## Quick contribution flow

1. **Find or discuss an issue.** New here? Start with
   [good first issue](https://github.com/ls1intum/Artemis/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22)
   or [help wanted](https://github.com/ls1intum/Artemis/issues?q=is%3Aissue+is%3Aopen+label%3A%22help+wanted%22).
   Otherwise look through [all open issues](https://github.com/ls1intum/Artemis/issues). If nothing
   matches, open a [bug report or feature request](https://github.com/ls1intum/Artemis/issues/new/choose).
   For questions rather than changes, use
   [GitHub Discussions](https://github.com/ls1intum/Artemis/discussions/categories/q-a).
2. **Check whether a feature proposal is needed.** Small fixes can go straight to a pull request.
   Larger changes need a written feature proposal first, so that requirements, architecture, and UI/UX
   are agreed on before implementation. See [Larger changes](#larger-changes-and-feature-proposals).
3. **Set up the development environment.** Follow the
   [setup guide](https://docs.artemis.tum.de/developer/setup).
4. **Create a branch.** Members of the organization branch directly in the repository; external
   contributors fork it first. Branch names follow `<type>/<area>/<short-description>`, where the type
   is `feature`, `chore`, or `bugfix`. Branches that do not follow this structure are rejected
   automatically.
5. **Implement and test.** Follow the
   [coding and design guidelines](https://docs.artemis.tum.de/developer/guidelines/) and add automated
   tests. See [Code quality and testing](#code-quality-and-testing).
6. **Open a pull request.** Start as a draft while you work, so questions can be discussed early. The
   title follows the
   [pull request naming conventions](https://docs.artemis.tum.de/developer/development-process#pr-naming-conventions),
   because it becomes part of the release notes. Fill in the template, including the steps for testing,
   and mark the pull request as ready for review once everything applicable is checked.
7. **Participate in review.** Respond to comments, push updates, and re-request review. See
   [Pull request review](#pull-request-review).

The [development process](https://docs.artemis.tum.de/developer/development-process) documents each of
these steps in full.

## Larger changes and feature proposals

Anything beyond a contained fix goes through a feature proposal: requirements, analysis, system
architecture, and, for user-facing changes, a UI/UX design. The
[feature proposal template](https://github.com/ls1intum/Artemis/blob/develop/.github/ISSUE_TEMPLATE/feature-proposal--developer-.md)
defines the structure, and labeling an issue `needs-feature-proposal` adds it to the issue
automatically.

Writing the proposal before the code is not bureaucracy: Artemis runs examinations at several
universities, and agreeing on scope and design up front is what keeps large changes reviewable.

## Code quality and testing

- [Coding and design guidelines](https://docs.artemis.tum.de/developer/guidelines/) is the entry point.
- [Server](https://docs.artemis.tum.de/developer/guidelines/server-development) and
  [client](https://docs.artemis.tum.de/developer/guidelines/client-development) guidelines cover the
  conventions that reviewers check against.
- [Performance guidelines](https://docs.artemis.tum.de/developer/guidelines/performance) matter: Artemis
  runs courses with thousands of students.
- Add [server tests](https://docs.artemis.tum.de/developer/guidelines/server-tests) and
  [client tests](https://docs.artemis.tum.de/developer/guidelines/client-tests). Coverage thresholds are
  enforced per module in CI, and the review checklist asks for line coverage above 90% on changed files.
- Translate every new user-facing string into English and German. A CI check verifies that both
  translation files define the same keys.
- Follow the
  [guidelines for inclusive, diversity-sensitive, and appreciative language](https://docs.artemis.tum.de/developer/guidelines/language).
- Run the linters and formatters before pushing; the commands are listed in the
  [setup guide](https://docs.artemis.tum.de/developer/setup).

## Pull request review

Reviewing is a shared responsibility. If you would like your pull requests reviewed, review others'
too.

What to expect:

- Reviewers deploy the change to a test server, execute the documented testing steps, and check related
  functionality for regressions. [Reviewer guidelines](https://docs.artemis.tum.de/developer/reviewer-guidelines)
  describes what a good review covers.
- The maintainer responsible for the affected feature or module approves the change within their scope.
- An Artemis maintainer performs the final review and merges into `develop`.
- A pull request is ready to merge once it has approvals from at least four reviewers, every applicable
  checklist item is checked, and every review thread is resolved.
- Keep pull requests focused and short-lived. A pull request without activity for 7 days is marked
  `stale` and is closed after 21 days of inactivity.

## Community expectations

All interaction in this project is governed by the [Code of Conduct](./CODE_OF_CONDUCT.md). Be
respectful and constructive, review the code rather than the person, and explain the reasoning behind a
requested change.

## Identity and transparency policy

Artemis is used to run courses and examinations, so it matters who wrote and who approved a change.
Contributions are therefore only accepted from accounts with a verifiable identity.

**This applies to everyone:**

1. **Real name.** Use your full real name in your GitHub profile.
2. **Authentic profile picture.** Use a clear, professional photo. Avatars, comic-style images, memojis,
   and similar non-authentic pictures are not accepted.

**Members of the organization** additionally create branches and pull requests directly in the
repository and follow the internal branching and review process linked above. Using a real name is a
prerequisite for joining the organization.

**External contributors** fork the repository, work on a branch there, keep it up to date with
`develop`, and open a pull request. Contributions that do not follow this policy may not be accepted.

These requirements are aligned with the
[GitHub Acceptable Use Policies](https://docs.github.com/en/site-policy/acceptable-use-policies), which
stress authenticity and transparency in user profiles. For general background on contributing to
open-source projects, the [Open Source Guides](https://opensource.guide/) are a good starting point.

## Governance

[Project governance](https://docs.artemis.tum.de/about/governance) documents the maintainer roles, the
path a change takes from feature request to release, and the automated quality gates that every change
has to pass. [Open-source development](https://docs.artemis.tum.de/developer/open-source) explains how
the engineering process fits together.

Maintainer roles are not reserved for one institution. Anyone with a sustained record of contributions
and reviews in an area can be nominated as its maintainer, and the criteria, the decision, and the
record are all public. See
[Becoming a maintainer](https://docs.artemis.tum.de/about/governance#becoming-a-maintainer). If your
university wants to take responsibility for part of Artemis, that is the path, and we would like to
hear from you at [artemis@xcit.tum.de](mailto:artemis@xcit.tum.de).

## Security

Do not report suspected vulnerabilities as public issues. Follow the private reporting process in
[SECURITY.md](./SECURITY.md).

Thank you for helping us maintain a high standard of quality and trust in this project.
