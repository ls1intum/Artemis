# Artemis

**Interactive Learning with Individual Feedback**

Artemis is an open-source platform for interactive learning with individual feedback. It enables
universities to provide scalable, interactive learning experiences with individual feedback, authentic
assessment, adaptive learning, and responsible AI support. Institutions deploy and operate Artemis on
their own infrastructure.

[![CI](https://github.com/ls1intum/Artemis/actions/workflows/ci.yml/badge.svg?event=push)](https://github.com/ls1intum/Artemis/actions/workflows/ci.yml)
[![Documentation](https://github.com/ls1intum/Artemis/actions/workflows/deploy-documentation.yml/badge.svg?event=push)](https://docs.artemis.tum.de)
[![Code Quality Status](https://app.codacy.com/project/badge/Grade/89860aea5fa74d998ec884f1a875ed0c)](https://www.codacy.com/gh/ls1intum/Artemis?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=ls1intum/Artemis&amp;utm_campaign=Badge_Grade)
[![Coverage Status](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fls1intum%2FArtemis%2Fbadges%2Fcoverage.json)](https://github.com/ls1intum/Artemis/actions/workflows/ci.yml)
[![Latest version](https://img.shields.io/github/v/tag/ls1intum/Artemis?label=%20Latest%20version&sort=semver)](https://github.com/ls1intum/Artemis/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](./LICENSE)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/ls1intum/Artemis/badge)](https://scorecard.dev/viewer/?uri=github.com/ls1intum/Artemis)
[![OpenSSF Best Practices](https://www.bestpractices.dev/projects/14163/badge)](https://www.bestpractices.dev/projects/14163)

## Why Artemis

- **Individual feedback at scale.** Programming submissions are built, tested, and analyzed
  automatically; quizzes are evaluated on submission; text and modeling exercises combine manual
  assessment with feedback suggestions.
- **Interactive exercises as first-class objects.** Programming, modeling, text, quiz, and file-upload
  exercises, with version control, continuous integration, and static code analysis built in.
- **Authentic assessment and online exams.** Coursework and examinations use the same exercise types,
  with exercise variants per student, test runs, plagiarism checks, and structured student reviews.
- **Adaptive learning.** Competencies, learning paths, and learning analytics let courses adapt to
  individual progress.
- **Responsible AI support.** Iris, Athena, and Hyperion are optional, configuration-gated
  integrations; instructors and tutors stay responsible for assessment decisions.
- **Open digital infrastructure.** MIT-licensed, developed in public, self-hosted, and built to scale
  horizontally for large courses.

## Quick links

| | |
| --- | --- |
| Try Artemis (one Docker command) | https://docs.artemis.tum.de/about/try |
| Documentation | https://docs.artemis.tum.de |
| About the project | https://docs.artemis.tum.de/about |
| Get started (development setup) | https://docs.artemis.tum.de/developer/setup |
| Deployment (administrators) | https://docs.artemis.tum.de/admin/intro |
| Roadmap | https://docs.artemis.tum.de/about/roadmap |
| Releases (minor release every two weeks) | https://github.com/ls1intum/Artemis/releases |
| Release and support policy | https://docs.artemis.tum.de/about/releases |
| Security policy | [SECURITY.md](./SECURITY.md) |
| Trust and transparency | https://docs.artemis.tum.de/about/trust |
| Contributing | [CONTRIBUTING.md](./CONTRIBUTING.md) |
| Project governance | https://docs.artemis.tum.de/about/governance |
| Research and publications | https://docs.artemis.tum.de/publications |
| Platform comparison | https://docs.artemis.tum.de/compare |
| Questions | [GitHub Discussions](https://github.com/ls1intum/Artemis/discussions/categories/q-a) |

## Try it

A complete Artemis with **working programming exercises**, from the repository root. No configuration,
published images for `amd64` and `arm64`:

```shell
# Linux with a native Docker daemon only: the build agent needs your host's docker group to reach the
# Docker socket, and Compose resolves it when it creates the container, so set it first.
export ARTEMIS_DOCKER_GROUP_ID=$(getent group docker | cut -d: -f3)
docker compose --env-file .env -f docker/artemis-dev-local-vc-local-ci-postgres.yml pull
docker compose --env-file .env -f docker/artemis-dev-local-vc-local-ci-postgres.yml up
```

Then open [http://localhost:8080](http://localhost:8080) and sign in as `artemis_admin` / `artemis_admin`.
This includes the integrated code lifecycle, so Artemis hosts the git repositories and a build agent
runs the tests. Docker Desktop on macOS and Windows needs no docker group, so the `export` line is
harmless there and can be skipped. See [Try Artemis](https://docs.artemis.tum.de/about/try) for what it
leaves out and how to get a hosted trial instead. The TUM instance is at https://artemis.tum.de.

## Key capabilities

Each entry links to the full documentation. The
[instructor guide](https://docs.artemis.tum.de/instructor/intro) has the complete reference.

| Area | What it does |
| --- | --- |
| [Exercises](https://docs.artemis.tum.de/instructor/exercises/intro) | Automatic and manual assessment, team exercises, multiple submissions, and a practice mode |
| [Programming exercises](https://docs.artemis.tum.de/instructor/exercises/programming-exercise) | Version control, test-case-based feedback, static code analysis, submission policies, and templates for 20 languages; any language that runs in a build container can be added |
| [Integrated code lifecycle](https://docs.artemis.tum.de/instructor/integrations/integrated-code-lifecycle) | Built-in version control and continuous integration with scalable build agents, so no external VCS or CI system is required |
| [Quiz exercises](https://docs.artemis.tum.de/instructor/exercises/quiz-exercise) | Multiple choice, drag and drop, and short answer questions in live, practice, and exam mode |
| [Modeling exercises](https://docs.artemis.tum.de/instructor/exercises/modeling-exercise) | UML diagrams in the [Apollon](https://apollon.ase.in.tum.de) editor with semi-automatic assessment |
| [Text exercises](https://docs.artemis.tum.de/instructor/exercises/text-exercise) | Manual and semi-automatic assessment with feedback suggestions |
| [File upload exercises](https://docs.artemis.tum.de/instructor/exercises/file-upload-exercise) | Free-form submissions assessed against structured grading criteria |
| [Exam mode](https://docs.artemis.tum.de/instructor/exams/intro) | Online exams with exercise variants, test runs, plagiarism checks, and student reviews |
| [Assessment and grading](https://docs.artemis.tum.de/instructor/assessment-grading/) | Double-blind grading, structured criteria, assessment training, complaints, grade keys, and export |
| [Communication](https://docs.artemis.tum.de/instructor/communication-support/communication) | Channels, threads, direct messages, announcements, notifications, and FAQs |
| [Lectures](https://docs.artemis.tum.de/instructor/lectures) | Slides, units, video integration, and transcriptions linked to learning objectives |
| [Adaptive learning](https://docs.artemis.tum.de/instructor/analytics/adaptive-learning) | Competencies, learning paths, and learning analytics (Atlas) |
| [Artemis Intelligence](https://docs.artemis.tum.de/admin/artemis-intelligence) | The maintained map of every AI-enabled subsystem: Iris, Athena, Hyperion, Atlas agents, and global search |
| [Tutorial groups](https://docs.artemis.tum.de/instructor/communication-support/tutorial-groups) | Session planning, tutor assignment, registration, and attendance |
| [Plagiarism checks](https://docs.artemis.tum.de/instructor/assessment-grading/plagiarism-check) | Programming ([JPlag](https://github.com/jplag/JPlag)), text, and modeling exercises, with a student review workflow |
| [LTI](https://docs.artemis.tum.de/instructor/integrations/lti-configuration) | Integration into existing learning management systems such as Moodle |
| [Mobile apps](https://docs.artemis.tum.de/student/getting-started/mobile-applications) | Native iOS and Android clients |

### AI services

Artemis integrates with the [EduTelligence](https://github.com/ls1intum/edutelligence) suite of
AI services. **Iris** is an LLM-based virtual tutor that guides students with hints and leading
questions, and **Athena** suggests feedback for text, modeling, and programming exercises.
**Hyperion** is Artemis-native AI-assisted exercise authoring built on Spring AI. All of them are
optional and gated by configuration.

EduTelligence maintains a
[compatibility matrix](https://github.com/ls1intum/edutelligence#-artemis-compatibility) that states
which service versions match which Artemis versions.
[Artemis Intelligence](https://docs.artemis.tum.de/admin/artemis-intelligence) documents which
capability is native to Artemis, which is an external service, and where the boundaries are.

## Used and developed across universities

Artemis is used and evaluated by universities and schools in several countries, each operating its own
instance. The canonical list, including instance URLs and contact people, is on the
[adoption page](https://docs.artemis.tum.de/about/adoption).

Artemis is also developed across institutions: it is initiated and primarily maintained by the
Applied Education Technologies (AET) group at the Technical University of Munich, with contributions
from a growing community. See [About Artemis](https://docs.artemis.tum.de/about).

## Deployment

Artemis is self-hosted. A production deployment consists of the Artemis server and client, a database
(PostgreSQL or MySQL), and, for programming exercises, one or more build agents. For smaller
deployments such as schools, and for institutions that want to try Artemis before running it
themselves, the Artemis team at TUM can host an instance; write to `artemis@xcit.tum.de`.

- [Administrator guide](https://docs.artemis.tum.de/admin/intro) covers production setup, security,
  scaling, and operations.
- The [Artemis Ansible Collection](https://github.com/ls1intum/artemis-ansible-collection) automates a
  production setup and is the recommended path.
- The [upgrade guide](https://docs.artemis.tum.de/admin/upgrade-guide) documents the required upgrade
  path between major versions.

### Development setup

Follow the [setup guide](https://docs.artemis.tum.de/developer/setup). For programming exercises we
recommend the
[integrated code lifecycle setup](https://docs.artemis.tum.de/admin/production-setup/integrated-code-lifecycle-setup);
Artemis can alternatively be run with
[LocalVC and Jenkins](https://docs.artemis.tum.de/admin/jenkins-localvc).

### Building for production

```shell
./gradlew -Pprod -Pwar clean bootWar
```

This produces `build/libs/Artemis-<version>.war`. To run it locally:

```shell
java -jar build/libs/*.war --spring.profiles.active=dev,localci,localvc,artemis,scheduling,buildagent,core,local,atlas
```

Then open [http://localhost:8080](http://localhost:8080). You may need to copy a configuration YAML
file into `build/libs` first; see the
[development setup](https://docs.artemis.tum.de/developer/setup).

The following command automates deployment to a test server:

```shell
./artemis-server-cli deploy username@artemis-test0.artemis.in.tum.de -w build/libs/Artemis-10.0.war
```

## Architecture

Artemis is decomposed into an application client (Angular) and an application server (Spring Boot).
For programming exercises, the server integrates version control and continuous integration, either
through the built-in integrated code lifecycle or through external systems. Authentication can be
delegated to an external user management system.

![Top-Level Design](documentation/docs/developer/assets/system-design/TopLevelDesign.png 'Top-Level Design')

The [system design documentation](https://docs.artemis.tum.de/developer/system-design) contains the
server architecture, the deployment view, and the data model.

## Contributing

Contributions are welcome, from bug reports and documentation fixes to new features. Start with
[CONTRIBUTING.md](./CONTRIBUTING.md), which explains the contribution flow, the identity and
transparency policy, and where the detailed guidelines live.

Development in Artemis follows a documented engineering process: feature request, feature proposal,
implementation against the coding guidelines, automated tests, manual verification on a test server,
peer review, feature-maintainer approval, and a final review by an Artemis maintainer. See
[Open-source development](https://docs.artemis.tum.de/developer/open-source) and the
[development process](https://docs.artemis.tum.de/developer/development-process).

Please also read the [Code of Conduct](./CODE_OF_CONDUCT.md).

## Project governance

Artemis has named maintainers per goal area and per feature module, layered code review, and a set of
automated quality gates that every change has to pass.
[Project governance](https://docs.artemis.tum.de/about/governance) documents the roles, the maintainer
tables, the decision path, and the gates.
[`.github/CODEOWNERS`](./.github/CODEOWNERS) is the machine-readable counterpart.

Artemis is not a separate legal entity. It is developed at the Technical University of Munich under
the MIT license, and every Artemis instance is operated by the institution running it.

## Security

Please report suspected vulnerabilities privately as described in [SECURITY.md](./SECURITY.md), not as
a public issue. Confirmed issues are published as
[GitHub Security Advisories](https://github.com/ls1intum/Artemis/security/advisories) once operators
have had a chance to upgrade. The same document describes the supported versions, the security
architecture, and the supply chain measures in place.
[Trust and transparency](https://docs.artemis.tum.de/about/trust) is the entry point for security,
privacy, accessibility, and AI data processing questions.

Every release ships with `SHA256SUMS`, CycloneDX SBOMs for the server and the client, and a signed
build provenance attestation:

```shell
sha256sum --check SHA256SUMS
gh attestation verify Artemis.war --repo ls1intum/Artemis
```

## Research

Artemis grew out of research on interactive learning and is still used as an education research
platform. The [research page](https://docs.artemis.tum.de/publications) explains the loop from
research question to released feature and lists the peer-reviewed publications behind the platform.

If you reference Artemis in your work, please use the citation metadata in
[`CITATION.cff`](./CITATION.cff).

## Documentation

The documentation is published at https://docs.artemis.tum.de and lives in
[`documentation/`](./documentation). See [documentation/README.md](./documentation/README.md) for how
to build and write it.

## License

Artemis is released under the [MIT license](./LICENSE), Copyright (c) TUM Applied Education
Technologies.
