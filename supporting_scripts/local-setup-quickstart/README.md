# Create local course and users

Scripts in this folder help to configure and setup your first Artemis course to get started quickly.

## Setup

1. Install the [Python Plugin for IntelliJ](https://plugins.jetbrains.com/plugin/631-python)

2. Enable Python support in your IntelliJ

   `File > Project Structure > Facets > Add - Python` (press the plus and add a Python environment there,
   make sure that you have configured the python interpreter)

   `File > Project Structure > Modules > Add - Python`
3. Configure the `Artemis.main` module for the run configuration of the run configuration `Artemis (Server, LocalVC & LocalCI)`

   `Run > Edit Configurations > Spring Boot > Artemis (Server, LocalVC & LocalCI) > -cp Artemis.main`

_Tested on python 3.11.6, other versions might work as well._

## Usage

1. Start your local Artemis instance
2. Configure the values in `config.ini` according to your setup
3. Install the packages of the python scripts that you want to execute
4. Either use the play button within IntelliJ _(which should be displayed if Python was configured properly within
   IntelliJ)_ to run the scripts or follow the following descriptions

### Create users

#### Atlassian

The users have already been created by `atlassian-setup.sh`, but they still need to be logged in order to be added to a
course _(without a first login Artemis does not know that the users exist)_

```shell
python3 authenticate_all_users.py
```

#### LocalVC & LocalCI

Creates users 1-20 (students, tutors, editors, instructors - 5 for each group) and users needed for Cypress E2E
testing (100-104, 106)

```shell
python3 create_users.py
```

### Create a course with standard user groups

Creates a course for which the users from the previous section [Create users](#create-users) are registered as they have the same user
groups (students, tutors, editors, instructors)

```shell
python3 create_course.py
```
