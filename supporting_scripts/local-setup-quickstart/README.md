# Create local course and users

Scripts in this folder help to configure and setup your first Artemis course to get started quickly.

## Setup

1. Install Python

2. Enable Python support in your IntelliJ

   `File > Project Structure > Facets > Add - Python` (press the plus and add a Python environment there,
   make sure that you have configured the python interpreter)

_Tested on python 3.11.6, other versions might work as well._

## Usage

1. Start your local Artemis instance
2. Configure the values in `config.ini` according to your setup

### Create course with users

Run the script:

```
python3 create_users.py
```

### Create a course with standard user groups

Run the script:

```
python3 create_course.py
```
