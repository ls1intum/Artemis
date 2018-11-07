# ${exerciseName}

<Introduction Text>

### Part 1: <Part 1>

<Text>

**You have the following tasks:**

<Some Examples>

1. ✅[Course Object](testHierarchy[Checking class Course],testFields[Checking class Course])
Create a **`Course`** class. A Course class should have the following attributes. For now, make all attributes **`public`**.

    1.  **`title`**: Course name of type String
    2.  **`lecturer`**: Object of type Lecturer

@startuml

interface Person

class Student
class Lecturer

Student -up-|> Person
Lecturer -up-|> Person

class Course {
  +<color:testsColor(testFields[Checking class Course])>title: String</color>
  +<color:testsColor(testFields[Checking class Course])>dates: List<Date></color>
  +<color:testsColor(testMethods[Checking class Course],testPrintCourseTitle)>printCourseTitle(): void</color>
}

Student "*" -left-  Course #testsColor(testFields[Checking class Course]): attendees 
Lecturer "1" -right-  Course #testsColor(testFields[Checking class Course]): lecturer 

@enduml


2. ✅[Course Constructor](testConstructors[Checking class Course])
Create a constructor for Course with one argument **`title`** of type String,
which initializes the title value of Course objects.

### Part 2: Inheritance

We want the University App to both support *Online Courses* and *Lecture
Courses*.

**You have the following tasks:**

1. ✅[LectureCourse Subclass](testHierarchy[Checking class LectureCourse]) 
Create the subclass **`LectureCourse`** which extends the Course class
with:

    1.  ✅[LectureCourse Attributes](testFields[Checking class LectureCourse])
    Create an attribute **`location`** of the Java type Point. This will store
    the location of the lecture hall as x and y coordinates.
    2.  ✅[LectureCourse Constructor](testConstructors[Checking class LectureCourse])
    Create a constructor with two arguments: **`title`** of
    type String and **`location`** of type Point.
