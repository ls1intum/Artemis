Programming Exercise Setup
====================

1. **Open Course Management**
* `https://artemis.ase.in.tum.de/#/course-management <https://artemis.ase.in.tum.de/#/course-management>`_
* Navigate into **Exercises** of your preferred course
TODO: Add picture

2. **Generate programming exercise**
* Click on **Generate new programming exercise**
TODO: Add picture
* Fill out all required values and click on **Generate**
TODO: Add two pictures

Result: **Programming Exercise**
TODO: Add picture
* 3 repositories
- **Template:** template code, can be empty, all students receive this code at the beginning of the exercises
- **Test:** contains all test cases, e.g. based on JUnit, hidden for students
- **Solution:** solution code, typically hidden for students, can be made available after the exercise

* 2 build plans
- **Template:** also called BASE, basic configuration for the test + template repository, used to create student build plans
- **Solution:** also called SOLUTION, configuration for the test + solution repository, used to manage test cases and to verify the exercise configuration
TODO: Add pictures

3. **Update exercise code in repositories**
* **Alternative 1:** Clone the 3 repositories and adapt the code on your local computer in your preferred development environment (e.g. Eclipse)
- To execute tests, copy the template (or solution) code into a folder **assignment** in the test repository and execute the tests (e.g. using maven clean test)
- Commit and push your changes
* **Alternative 2:** Open Edit in Editor in Artemis (in the browser) and adapt the code in online code editor
- You can change between the different repos and submit the code when needed
* **Alternative 3:** Use IntelliJ with the Orion plugin and change the code directly in IntelliJ

* Edit in Editor
TODO: Add picture

* Check the results of the template and the solution build plan
* They should not have the status **build failed**
* In case of a **build failed** result, some configuration is wrong, please check the build errors on the corresponding build plan.
* **Hints:**
- Test cases should only reference code, that is available in the template repository. In case this is **not** possible, please try out the option **Sequential Test Runs**

4. **Optional:** Adapt the build plans
* The build plans are preconfigured and typically do not need to be adapted
* However, if you have additional build steps or different configurations, you can adapt the BASE and SOLUTION build plan as needed
* When students start the programming exercise, the current version of the BASE build plan will be copied. All changes in the configuration will be considered

5. Adapt the interactive problem statement
TODO: Add picture

* Click the Edit button of the programming exercise or navigate into Edit in Editor and adapt the interactive problem statement.
* The initial example shows how to integrate tasks, link tests and integrate interactive UML diagrams

6. Manage test cases
TODO: Add picture

7. Verify the exercise configuration
* Open the **View** page of the programming exercise
TODO: Add picture
- The template result should have a score of **0%** with **0 of X passed**
- The solution result should have a score of **100%** with **X of X passed**

* Click on **Edit**
- Below the problem statement, you should see **Test cases** ok and **Hints** ok
TODO: Add picture


