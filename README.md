# Artemis: Interactive Learning with Individual Feedback 

[![Build & Deploy](https://github.com/ls1intum/Artemis/actions/workflows/build.yml/badge.svg?event=push)](https://github.com/ls1intum/Artemis/actions/workflows/build.yml)
[![Test](https://github.com/ls1intum/Artemis/actions/workflows/test.yml/badge.svg?event=push)](https://github.com/ls1intum/Artemis/actions/workflows/test.yml)
[![Documentation](https://github.com/ls1intum/Artemis/actions/workflows/docs.yml/badge.svg?event=push)](https://docs.artemis.cit.tum.de)
[![Code Quality Status](https://app.codacy.com/project/badge/Grade/89860aea5fa74d998ec884f1a875ed0c)](https://www.codacy.com/gh/ls1intum/Artemis?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=ls1intum/Artemis&amp;utm_campaign=Badge_Grade)
[![Coverage Status](https://app.codacy.com/project/badge/Coverage/89860aea5fa74d998ec884f1a875ed0c)](https://www.codacy.com/gh/ls1intum/Artemis?utm_source=github.com&utm_medium=referral&utm_content=ls1intum/Artemis&utm_campaign=Badge_Coverage)
[![Latest version)](https://img.shields.io/github/v/tag/ls1intum/Artemis?label=%20Latest%20version&sort=semver)](https://github.com/ls1intum/Artemis/releases/latest)

Artemis brings interactive learning to life with instant, individual feedback on programming exercises, quizzes, modeling tasks, and more. Offering customization for instructors and real-time collaboration for students, this platform bridges creativity and education. Embrace a new era of engaging, adaptive learning and artificial intelligence support with Artemis, where innovation meets inclusivity. Find out more on https://artemisapp.github.io

## Main features

1. **[Programming exercises](https://docs.artemis.cit.tum.de/user/exercises/programming/)** with version control, automatic individual feedback (and assessment) based on test cases and static code analysis (executed using continuous integration) for `any programming language`. 
   * **Instant**: Students receive immediate and individual feedback on submissions. Instructors can customize feedback messages easily, hide feedback during the working time (e.g., with hidden tests)
   * **Interactive:** Instructors integrate interactive instructions based on tasks and UML diagrams directly into the dynamic problem statements. They can define hints for difficult exercise parts.
   * **Independent**: Instructors can customize programming exercises to support any programming language. To simplify the setup, Artemis includes sophisticated templates for the most common languages (e.g., Java, Python, C, Haskell, Kotlin, VHDL, Assembler, Swift, Ocaml, ...)
   * **Local and online**: Students can participate locally in their preferred IDE or in the online code editor (without any setup)
   * **Manual reviews** are possible directly in the online code editor or in [Orion](https://github.com/ls1intum/Orion)
   * **Policies**: Instructors can define submission policies (e.g., penalties after 10 attempts) to prevent that students try out all possibilities without thinking.
   * **Grading**: Instructors have many options to configure grading, analyze the results based on tests and static code analysis categories and re-evaluate the results
   * **Secure**: Test cases and student code run in Docker environments on build agents. Test frameworks such as [Ares](https://github.com/ls1intum/Ares) simplify the creation of structural and dynamic tests and prevent that students can cheat.
2. **[Integrated code lifecycle](https://docs.artemis.cit.tum.de/dev/setup/integrated-code-lifecycle)**: Elevating the programming exercise experience, Artemis seamlessly integrates **version control** and **continuous integration**, creating a streamlined, cohesive development environment. Unlike external systems, this integrated approach minimizes maintenance overhead and is tailored to the specific needs of a learning platform, offering unparalleled flexibility. It simplifies the initial setup for administrators and ensures a smoother, integrated workflow for students and instructors alike.
3. **[Quiz exercises](https://docs.artemis.cit.tum.de/user/exercises/quiz/)** with multiple choice, drag and drop, and short answer questions
    * **Modeling quizzes**: Instructors can easily create drag and drop quizzes based on UML models
    * **Different modes**: Quizzes support a live mode (rated) during lectures, a practice mode for students to repeat the quiz as often as they want, and an exam mode (see below)
4. **[Modeling exercises](https://docs.artemis.cit.tum.de/user/exercises/modeling/)** based on the easy-to-use online modeling editor [Apollon](https://apollon.ase.in.tum.de) with semi-automatic assessment using machine learning concepts
   * **Multiple diagram types**: Artemis supports 7 UML diagram types (e.g. class diagrams) and 4 additional diagrams (e.g. flow charts)
   * **Easy to use**: Students can create models easily using drag and drop, they can explain the models using additional text.
   * **Integrated feedback**: Reviews can provide feedback and points directly next to the model elements.
5. **[Text exercises](https://docs.artemis.cit.tum.de/user/exercises/textual/)** with manual, semi-automatic assessment based on supervised machine learning and natural language processing (NLP) using [Athena](https://github.com/ls1intum/Athena)
   * **Integrated feedback**: Reviews can provide feedback and points directly next to the text segments.
   * **Language detection**: Artemis detects the language of the submission and shows the word and character count. 
6. **[File upload exercises](https://docs.artemis.cit.tum.de/user/exercises/file-upload/)** allow full flexibility to instructors. Students can create any kind of file (e.g. PDF, PNG) and submit it to Artemis when they have completed their work. Artemis allows instructors and tutors to download the files and assess them manually based on structured grading criteria (see below in the section Assessment).
7. **[Exam mode](https://docs.artemis.cit.tum.de/user/exam_mode/)**: Instructors can create online exams with exercise variants, integrated plagiarism checks, test runs and student reviews. You can find more information on [Exam mode student features](https://artemis.cit.tum.de/features/students) and on [Exam mode instructor features](https://artemis.cit.tum.de/features/instructors).
8. **[Grading](https://docs.artemis.cit.tum.de/user/grading/)**: Instructors can configure grade keys for courses and exams to automatically calculate grades and display them to students. Grades can be easily exported as csv files to upload them into university systems (such as Campus online). Instructors can optionally define bonus configurations for final exams to improve student grades according to their grades from a midterm exam or course exercises.
9. **[Assessment](https://docs.artemis.cit.tum.de/user/exercises/assessment/)**: Artemis uses double-blind grading and structured grading criteria to improve consistency and fairness. It integrates an assessment training process (based on example submissions and example assessments defined by the instructor), has a grading leader board, and allows students to rate the assessments. Students can complain or ask for more feedback.   
10. **[Communication](https://docs.artemis.cit.tum.de/user/communication/)**: Instructors can post announcements. Students can ask questions, post comments, and react to other posts in channels or private chats. Tutors can filter unanswered questions.
11. **[Notifications](https://docs.artemis.cit.tum.de/user/notifications)**: Artemis supports customizable web and email notifications. Users can enable and disable different notification types.
12. **[Team exercises](https://docs.artemis.cit.tum.de/user/exercises/team-exercises/)**: Instructors can configure team exercises with real time collaboration and dedicated tutors per team.
13. **[Lectures](https://docs.artemis.cit.tum.de/user/lectures/)**: Instructors can upload lecture slides, divide lectures into units, integrate video streams, lecture recordings, and exercises into lectures, and define competencies.
14. **[Integrated markdown editor](https://docs.artemis.cit.tum.de/user/markdown-support/)**: Markdown is used to format text content across the platform using an integrated markdown editor.
15. **[Plagiarism checks](https://docs.artemis.cit.tum.de/user/plagiarism-check/)**: Artemis integrates plagiarism checks for programming exercises (based on [JPlag](https://github.com/jplag/JPlag)), text exercises, and modeling exercises in courses and exams. It allows notifying students about identified plagiarism. Students can review and comment on the allegation.
16. **[Learning analytics](https://docs.artemis.cit.tum.de/user/learning-analytics/)**: Artemis integrated different statistics for students to compare themselves to the course average. It allows instructors to evaluate the average student performance based on exercises and competencies.
17. **[Adaptive learning](https://docs.artemis.cit.tum.de/user/adaptive-learning/)**: Artemis allows instructors and students to define and track competencies. Students can monitor their progress towards these goals, while instructors can provide tailored feedback. This approach integrates lectures and exercises under overarching learning objectives.
18. **[Learning paths](https://docs.artemis.cit.tum.de/user/adaptive-learning/adaptive-learning-student.html#learning-paths)**: Based on the competency model and students' individual progress, Artemis creates learning paths that guide students through the course content.
19. **[Tutorial groups](https://docs.artemis.cit.tum.de/user/tutorialgroups/)**: Artemis supports the management of tutorial groups of a course. This includes planning the sessions, assigning responsible tutors, registering students and tracking the attendance.
20. **[Iris](https://artemis.cit.tum.de/about-iris)**: Artemis integrates Iris, a chatbot that supports students and instructors with common questions and tasks.
21. **[Scalable](https://docs.artemis.cit.tum.de/admin/scaling/)**: Artemis scales to multiple courses with thousands of students simultaneously using it. In fact, the largest course had 2,400 students. Administrators can easily scale Artemis with additional build agents in the continuous integration environment.
22. **[High user satisfaction](https://docs.artemis.cit.tum.de/user/user-experience/)**: Artemis is easy to use, provides guided tutorials. Developers focus on usability, user experience, and performance.
23. **Customizable**: It supports multiple instructors, editors, and tutors per course and allows instructors to customize many course settings
24. **[Open-source](https://docs.artemis.cit.tum.de/dev/open-source/)**: Free to use with a large community and many active maintainers.

## Roadmap

The Artemis development team prioritizes the following areas in the future. We welcome feature requests from students, tutors, instructors, and administrators. We are happy to discuss any suggestions for improvements.

* **Short term**: Further improve the communication features with mobile apps for iOS and Android
* **Short term**: Add the possibility to use Iris for questions on all exercise types and lectures (partly done)
* **Short term**: Provide GenAI based automatic feedback to modeling, text and programming exercise with Athena
* **Short term**: Improve the LTI integration with Moodle
* **Medium term**: Improve the REST API of the server application
* **Medium term**: Integrate an online IDE (e.g. Eclipse Theia) into Artemis for enhanced user experience
* **Medium term**: Add more learning analytics features while preserving data privacy
* **Medium term**: Improve the user experience, usability and navigation
* **Medium term**: Add automatic generation of hints for programming exercises
* **Medium term**: Add GenAI support for reviewing exercises for instructors
* **Medium term**: Add GenAI support for learning analytics (partly done)
* **Long term**: Explore the possibilities of microservices, Kubernetes based deployment, and micro frontends
* **Long term**: Allow students to take notes on lecture slides and support the automatic updates of lecture slides
* **Long term**: Develop an exchange platform for exercises

## Contributing to This Project

We welcome contributions from both members of our organization and external contributors. To maintain transparency and trust:

- **Members**: Must use their full real names and upload a professional and authentic profile picture. Members can directly create branches and PRs in the repository.
- **External Contributors**: Must adhere to our identity guidelines, using real names and authentic profile pictures. Contributions will only be considered if these guidelines are followed.

We adhere to best practices as recommended by [GitHub's Open Source Guides](https://opensource.guide/) and their [Acceptable Use Policies](https://docs.github.com/en/site-policy/acceptable-use-policies). Thank you for helping us create a respectful and professional environment for everyone involved.

We follow a pull request contribution model. For detailed guidelines, please refer to our [CONTRIBUTING.md](./CONTRIBUTING.md). Once your pull request is ready to merge, notify the responsible feature maintainer on Slack:

#### Maintainers

The following members of the project management team are responsible for specific feature areas in Artemis. Contact them if you have questions or if you want to develop new features in this area.

| Feature / Aspect               | Responsible maintainer                                                             |
|--------------------------------|------------------------------------------------------------------------------------|
| Programming exercises          | Stephan Krusche ([@krusche](https://github.com/krusche))                           |
| Integrated code lifecycle      | Stephan Krusche ([@krusche](https://github.com/krusche))                           |
| Quiz exercises                 | Felix Dietrich ([@FelixTJDietrich](https://github.com/FelixTJDietrich))            |
| Modeling exercises (+ Apollon) | Stephan Krusche ([@krusche](https://github.com/krusche))                           |
| Text exercises                 | Maximilian Sölch ([@maximiliansoelch](https://github.com/maximiliansoelch))        |
| File upload exercises          | Maximilian Sölch ([@maximiliansoelch](https://github.com/maximiliansoelch))        |
| Exam mode                      | Stephan Krusche ([@krusche](https://github.com/krusche))                           |
| Grading                        | Maximilian Sölch ([@maximiliansoelch](https://github.com/maximiliansoelch))        |
| Assessment                     | Maximilian Sölch ([@maximiliansoelch](https://github.com/maximiliansoelch))        |
| Communication                  | Ramona Beinstingel ([@rabeatwork](https://github.com/rabeatwork))                  |
| Notifications                  | Ramona Beinstingel ([@rabeatwork](https://github.com/rabeatwork))                  |
| Team Exercises                 | Stephan Krusche ([@krusche](https://github.com/krusche))                           |
| Lectures                       | Maximilian Anzinger ([@maximiliananzinger](https://github.com/maximiliananzinger)) |
| Integrated Markdown Editor     | Patrick Bassner ([@bassner](https://github.com/bassner))                           |
| Plagiarism checks              | Markus Paulsen ([@MarkusPaulsen](https://github.com/MarkusPaulsen))                |
| Learning analytics             | Maximilian Anzinger ([@maximiliananzinger](https://github.com/maximiliananzinger)) |
| Adaptive learning              | Maximilian Anzinger ([@maximiliananzinger](https://github.com/maximiliananzinger)) |
| Learning paths                 | Maximilian Anzinger ([@maximiliananzinger](https://github.com/maximiliananzinger)) |
| Tutorial Groups                | Felix Dietrich ([@FelixTJDietrich](https://github.com/FelixTJDietrich))            |
| Iris                           | Patrick Bassner ([@bassner](https://github.com/bassner))                           |
| Scalability                    | Matthias Linhuber ([@mtze](https://github.com/mtze))                               |
| Usability                      | Ramona Beinstingel ([@rabeatwork](https://github.com/rabeatwork))                  |
| Performance                    | Ramona Beinstingel ([@rabeatwork](https://github.com/rabeatwork))                  |
| Infrastructure                 | Matthias Linhuber ([@mtze](https://github.com/mtze))                               |
| Development process            | Felix Dietrich ([@FelixTJDietrich](https://github.com/FelixTJDietrich))            |
| Mobile apps (iOS + Android)    | Maximilian Sölch ([@maximiliansoelch](https://github.com/maximiliansoelch))        |

## Setup and guidelines

### Development setup, coding, and design guidelines

* [How to set up your local development environment](https://docs.artemis.cit.tum.de/dev/setup/)
* [Server coding and design guidelines](https://docs.artemis.cit.tum.de/dev/guidelines/server/)
* [Client coding and design guidelines](https://docs.artemis.cit.tum.de/dev/guidelines/client/)
* [Code Review Guidelines](https://docs.artemis.cit.tum.de/dev/development-process/#review)
* [Performance Guidelines](https://docs.artemis.cit.tum.de/dev/guidelines/performance/)

### Documentation

The Artemis documentation is available [here](https://docs.artemis.cit.tum.de/).
You can find a guide on [how to write documentation](docs/README.md).

### Server setup

Setting up Artemis in your development environment or a demo production environment is really easy following the instructions on https://docs.artemis.cit.tum.de/dev/setup. When you want to support programming exercises, we recommend using the [Integrated Code Lifecycle](https://docs.artemis.cit.tum.de/dev/setup/#integrated-code-lifecycle-setup).

Artemis can also be set up in conjunction with external tools for version control and continuous integration:
1. [LocalVC and Jenkins](https://docs.artemis.cit.tum.de/dev/setup/#jenkins-and-localvc-setup)
2. [GitLab and Jenkins (deprecated)](https://docs.artemis.cit.tum.de/dev/setup/#jenkins-and-gitlab-setup)
3. [GitLab and GitLab CI (experimental)](https://docs.artemis.cit.tum.de/dev/setup/#gitlab-ci-and-gitlab-setup)

Artemis uses these external tools for user management and the configuration of programming exercises.

### Administration setup

If needed, you can configure self service [user registration](https://docs.artemis.cit.tum.de/admin/registration).

### Building for production

To build and optimize the Artemis application for production, run:

```shell
./gradlew -Pprod -Pwar clean bootWar
```

This will create a Artemis-<version>.war file in the folder `build/libs`. The build command compiles the TypeScript into JavaScript files, concatenates and minifies the created files (including HTML and CSS files). It will also modify `index.html` so it references these new files. To ensure everything worked, run the following command to start the application on your local computer:

```shell
java -jar build/libs/*.war --spring.profiles.active=dev,localci,localvc,artemis,scheduling,buildagent,core,local
```

(You might need to copy a yml file into the folder build/libs before, also see [development setup](https://docs.artemis.cit.tum.de/dev/setup/))

Then navigate to [http://localhost:8080](http://localhost:8080) in your browser.

Refer to [Using JHipster in production](http://www.jhipster.tech/production) for more details.

The following command can automate the deployment to a server. The example shows the deployment to the main Artemis test server (which runs a virtual machine):

```shell
./artemis-server-cli deploy username@artemistest.ase.in.tum.de -w build/libs/Artemis-7.5.3.war
```

## Architecture

The following diagram shows the top level design of Artemis which is decomposed into an application client (running as Angular web app in the browser) and an application server (based on Spring Boot). For programming exercises, the application server connects to a version control system (VCS) and a continuous integration system (CIS). Authentication is handled by an external user management system (UMS).

![Top-Level Design](docs/dev/system-design/TopLevelDesign.png "Top-Level Design")

While Artemis includes generic adapters to these three external systems with a defined protocol that can be instantiated to connect to any VCS, CIS, or UMS, it also provides 3 concrete implementations for these adapters to connect to.

### Server architecture

The following simplified UML component diagram exemplary shows more details of the Artemis application server architecture and its REST interfaces to the application client.

![Server Architecture](docs/dev/system-design/ServerArchitecture.png "Server Architecture")

### Deployment

The following UML deployment diagram shows a typical deployment of Artemis application server and application client. Student, Instructor, and Teaching Assistant (TA) computers are all equipped equally with the Artemis application client being displayed in the browser.

The Continuous Integration Server typically delegates the build jobs to local build agents within the university infrastructure or to remote build agents, e.g., hosted in the Amazon Cloud (AWS).

![Deployment Overview](docs/dev/system-design/DeploymentOverview.svg "Deployment Overview")

### Data model

The Artemis application server uses the following (simplified) data model in the MySQL database (notice that the actual data model is more complex by now). It supports multiple courses with multiple exercises. Each student in the participating student group can participate in the exercise by clicking the **Start Exercise** button. 
Then a repository and a build plan for the student (User) will be created and configured. The initialization state helps to track the progress of this complex operation and allows recovering from errors. 
A student can submit multiple solutions by committing and pushing the source code changes to a given example code into the version control system or using the user interface. The continuous integration server automatically tests each submission and notifies the Artemis application server when a new result exists. 
In addition, teaching assistants can assess student solutions and "manually" create results.

![Data Model](docs/dev/system-design/DataModel.svg "Data Model")

Please note that the actual database model is more complex. The UML class diagram above omits some details for readability (e.g., lectures, student questions, exercise details, static code analysis, quiz questions, exam sessions, submission subclasses, etc.)

### Artemis Community

There is a growing community of university instructors who are using Artemis.

#### Communication

We communicate using GitHub issues and pull requests. Additionally, you can join us on Slack to ask questions and get support. If you are interested, please send an email to [Stephan Krusche](mailto:krusche@tum.de).

#### Universities / Schools with Artemis in Use

The following universities are actively using Artemis or are currently evaluating Artemis.

* **Technical University of Munich**  
  https://artemis.cit.tum.de  
  Main contact person: [Stephan Krusche](mailto:krusche@tum.de)  
  
* **LFU Innsbruck, Uni Salzburg, JKU Linz, AAU Klagenfurt, TU Wien**  
  https://artemis.codeability.uibk.ac.at  
  [codeAbility project](https://codeability.uibk.ac.at)  
  Main contact person: [Michael Breu](mailto:Michael.Breu@uibk.ac.at)  
  
* **University of Stuttgart**  
  https://artemis.sqa.ddnss.org  
  Main contact person: [Steffen Becker](mailto:steffen.becker@informatik.uni-stuttgart.de)  
  
* **Universität Passau**  
  https://artemis.fim.uni-passau.de (only accessible via the university network/VPN)  
  Main contact person: [Benedikt Fein](mailto:fein@fim.uni-passau.de)  
  
* **Karlsruhe Institute of Technology**  
  https://artemis.praktomat.cs.kit.edu  
  Main contact person: [Dominik Fuchß](mailto:dominik.fuchss@kit.edu)  
  
* **Hochschule München**  
  https://artemis.cs.hm.edu  
  Main contact person: [Michael Eggers](mailto:michael.eggers@hm.edu)  
  
* **Technische Universität Dresden**  
  Main contact person: [Andreas Domanowski](mailto:andreas.domanowski@tu-dresden.de)  
  
* **Hochschule Heilbronn**  
  Main contact person: [Jörg Winckler](mailto:joerg.winckler@hs-heilbronn.de)  
  
* **Maria-Theresia-Gymnasium München**  
  Main contact person: [Valentin Herrmann](mailto:valentin.herrmann@tum.de)

##### Interested universities

* **HU Berlin**  
   Main contact person: [Lars Grunske](https://www.informatik.hu-berlin.de/de/Members/lars-grunske)

* **Westsächsische Hochschule Zwickau**  
   Main contact person: [Heiko Baum](https://www.fh-zwickau.de/pti/organisation/fachgruppe-informatik/personen/dr-ing-heiko-baum)

* **Technische Universität Chemnitz**  
   Main contact person: [Danny Kowerko](https://www.tu-chemnitz.de/informatik/mc/professor.php.en)

* **Universität zu Köln**  
   Main contact person: [Andreas Vogelsang](https://cs.uni-koeln.de/sse/team/prof-dr-andreas-vogelsang)
* **Technische Universität Dortmund**  
   Main contact person: [Falk Howar](https://se.cs.tu-dortmund.de)
* **Universität Bielefeld**  
   Main contact person: [Daniel Merkle](https://ekvv.uni-bielefeld.de/pers_publ/publ/PersonDetail.jsp?personId=451188465)
* **Universität Ulm**  
   Main contact person: [Matthias Tichy](https://www.uni-ulm.de/in/sp/team/tichy)
* **Imperial College London**  
   Main contact person: [Robert Chatley](https://www.doc.ic.ac.uk/~rbc)
* **University of South Australia**  
   Main contact person: [Srecko Joksimovic](https://people.unisa.edu.au/srecko.joksimovic)
