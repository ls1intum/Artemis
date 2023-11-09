Graphical representation of exercises
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

We need to provide students with UML diagrams that highlight incorrect elements,
but at the same time we want to prevent them from
copying from the diagram and want to provide a diagram in a picture
format.

Also have in mind that provided text in a picture format can interfere
with the cross platform functionality and the visualization is not as
beautiful as with real text.

-  **UML diagrams:** Give students rather UML diagrams that they can not
   copy from easily instead of all the attribute names in the text
   instructions. AI needs to infer the attribute names and also will
   give attributes wrong names that need to be reviewed by the students.

   -  Give diagram in picture format

   -  Use associations/aggregation between classes since it is harder to copy these
      from the UML diagrams

   .. note::
      Giving the UML diagrams in picture format might be more suitable for the exam mode and not in the regular exercise operation

   Example for an exercise where a UML diagram was given and the
   attributes were not mentioned in the problem statement, which led to the AI struggling, as seen in the subsequent code comparison.

   .. figure:: programming/AI-proof-exercises/JackOLantern.png
      :alt: UML diagram for an exercise
      :align: center
      :width: 50%

      UML diagram for an exercise showcasing the relationship between the classes as well as the content of the individual classes.

   Generated solution:

   .. code:: java

      public class Pumpkin {


          private double weight;
          private String face;

          public Pumpkin(double weight) {
          this.weight = weight;
          this.face = "";


          }


      }

   Example solution:

   .. code:: java

      public class Pumpkin {
          private double weight;
          private String face;
          private String type;
          private boolean seeds;

          public Pumpkin(String type,
                  double weight) {
              this.type = type;
              this.weight = weight;
              face = "";
              seeds = true;
          }

      }


-  **Depictions of functionalities:** Rather than explaining how a
   concept works in theory, provide depictions of the functionality.
   This encourages students to understand the basic code instead of
   going to AI for answers. Examples that were used in previous
   exercises:


   .. figure:: programming/AI-proof-exercises/List-generics.png
      :align: center
      :alt: This figure showcases the functionality of a linked-list
      :width: 50%


      Depiction shows the functionality of a linked list with 3 integer values
      as a basic concept for understanding linked lists. The task of this exercise
      were to implement the common List functionalities from scratch rather than
      rely on the standard methods from java. ListNode and LinkedList were
      therefore own classes that needed to be filled with the required
      functionalities.


   This depiction provides an understanding of how a linked list should look
   like after the add function was called.

   .. figure:: programming/AI-proof-exercises/add.png
      :align: center
      :width: 50%


      Figure shows what a list should look like after the add functionality was called. Picture from the Problem statement

-  **Java frameworks:** Using Java frameworks, such as JavaFX, is challenging for AI
   and helps to visualize projects in a more interactive way
   for students through gamification. How can we incorporate that for
   our exercises?

   -  Provide Graphical User Interfaces (GUI) for
      students with which they can also test the functionality of their
      code.

   -  Give exercises that use GUIs because AI has difficulty
      solving them accurately.

   -  Provide pictures of how the GUI should look in the end.

      Students needed to implement the GUI based on the this figure that
      was given in the problem statement.

      .. figure:: programming/AI-proof-exercises/GUI-upgrade-for-game.png
         :align: center
         :width: 30%


         Example GUI from homework exercise

   .. note::
      Apply this approach when students already have knowledge of what a GUI is. Most likely in
      a later stage of the semester.

.. note::
   ChatGPT is limited to text input. Including a fair amount of graphical
   representation for the exercises helps to limit the effectiveness of such tools.

Problem statement and exercise instructions
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^


-  **Exercise length:** There is a negative correlation between the
   length of exercises and AI performance:

   -  Do not keep the exercises between 500 and 1000 characters, but
      rather keep them between 1200 and 2500 characters. Short exercises
      are normally easier to solve for AI since there are not so many
      constraints and the problem statement only focused on the most
      relevant implementation parts.

   .. note::
       Apply this with more complex exercises at a later stage of the semester. In the first exercises
       it does not make sense to give more text just to make the exercise more complicated.


-  **Standard Java methods:** Make use of letting students implement
   specific parts of methods on their own instead of using standard
   functionality provided by Java. AI often takes advantage of these to
   save on implementation. It should be explicitly inside of a method
   that has additional functionality instead of just on its own. It
   obviously needs to be checked for these standard methods then in the
   test cases. Some functionality that can be implemented from scratch.

   -  Sorting (e.g., Bubble Sort, Selection Sort, Insertion Sort)

   -  Searching (e.g., Linear Search, Binary Search)

   -  String manipulation (e.g., Counting characters, Reversing a
      string)

   -  Collection manipulation (e.g., Adding elements to a custom list,
      Removing duplicates from a list)

   -  Math operations (e.g., Finding the factorial of a number,
      Computing the Fibonacci sequence)

   Example showing the use of the standard method ’sort’ from the array
   library by the AI instead of implementing sort from scratch:

   Generated solution:

   .. code:: java

      public Item[] fillBagMax(int bagCapacity) {
          if (shoppingList.length == 0) {
              return null;
          }
          // Sort the shopping list by value in descending order
          Arrays.sort(shoppingList, (a, b) -> b.getValue() - a.getValue());
          ...
      }

   Example solution:

   .. code:: java

      public Item[] fillBagMax() {
          if (checkShoppingListExists()) {
              //create a new array without null item to aplly the
              //sorting algorithm
              int size = 0;
              for (int i = 0; i < shoppingList.length; i++) {
                  if (checkItemExists(i)) {
                      size++;
                  }
              }
              Item[] listWithoutNull = new Item[size];
              int nextInShoppingList = 0;

              for (int i = 0; i < listWithoutNull.length; i++) {
                  // Move the pointer one further, if a spot on the list
                  //is not filled.
                  while (shoppingList[nextInShoppingList] == null) {
                      nextInShoppingList++;
                  }
                  listWithoutNull[i] = shoppingList[nextInShoppingList++];
              }
              ...
      }

   .. note::
      The java standard methods which can be used to solve specific
      tasks need to be excluded in the test cases and feedback has to be provided to the student.
      e.g. The method Arrays.sort is not allowed to be used.

   .. note::
       Apply this when students are at the current stage of covering these topics in the lecture.
       For more complex exercises, where they need to implement different content, they can use standard methods.


-  **Continuous feedback:** Exercise instructions should not be there to just give a
   thorough step by step guide of the task. Have students deduce, using specific test cases,
   what might be missing in the code.
   This promotes critical thinking and resembles a more real world scenario:

   -  null-checks

   -  check existence of list

   Example shows that AI disregards to check if shopping list exists:

   Generated solution:

   .. code:: java

      public int search(String itemName) {
         for (int i = 0; i < shoppingList
                     .length; i++) {
            if (shoppingList[i].getName()
                     .equals(itemName)) {
            return i;
            }
         }
         return -1;
      }

   Example solution:

   .. code:: java

      public int search(String itemName) {
      //check whether the shoppingList exists
         if (!checkShoppingListExists()) {
            return -1;
         }

         //the rest is the same as
         //the AI solution

      }

-  **Split Up Instructions for Improved Engagement:** To prevent
   students from blindly copying and pasting, it's essential to
   distribute the instructions strategically. Ideally, around 80%
   of the guidelines should be placed within the problem
   statement, leaving a minimum of 20% to be incorporated
   directly into the template code, marked clearly with "TODO"
   annotations. This approach ensures students interact more
   deeply with the code while also referencing the overarching
   problem statement.

   .. note::
      You have to then write in the problem statement: "Also follow the TODOs
      in the code."

   The following table shows with a simple example, how that might look like in practice.


.. list-table::
  :widths: 30 70
  :header-rows: 1
  * - **Problem Statement**
    - **Template Code**
  * - Implement the `reverseString` method in the `StringUtil` class. |Ensure the returned string is the reverse of |the input string. Follow the TODOs in the code for more information.
    -
      .. code-block:: java
         public class StringUtil {
             public static String reverseString(String input) {
                 // TODO: Return the reversed version of the input string.
                 // Do not use built-in methods or libraries to reverse the string directly.
                 return "";
             }
         }

-  **Already implemented code:** Give exercises that require students
   to work with code that is already in the template repository. Two approaches:

   -  Projects that continue from week to week and needs to be developed
      throughout the semester.

   -  Certain functionalities in already implemented classes that need
      to be used by students in order to solve the exercise.

   The item class was provided in the template and needed to be used by
   students. It was an existential part that is required to solve the
   other exercises, since it was also given for example given as the
   return value for methods Without this knowledge.
   ChatGPT needs to guess the content of this class.

   Template code:

   .. code:: java

      public class Item {
          private String name;
          private int weight;
          private int value;

          public Item(String name, int weight, int value) {
              this.name = name;
              this.weight = weight;
              this.value = value;
          }

          public String getName() {
              return name;
          }

          public int getWeight() {
              return weight;
          }

          public int getValue() {
              return value;
          }
      }


-  **Static questioning:** Avoid giving students exercises that test
   knowledge in a static manner. Make the newly learned concept part of a
   bigger picture and incorporate it with previously learned concepts
   (goes hand in hand with working on a project throughout the
   semester). Exercises that were previously solved statically.

   -  Stream functionality

   -  Recursion

   .. figure:: programming/AI-proof-exercises/Streams-exercise.png
      :align: center
      :width: 50%

      Part of a static stream exercise where a method's functionality needed to
      be created using only streams.

   An alternate formulation for this exercise which promotes critical thinking and prevents
   blind copy and paste attempts could look like this:

      In the Artemis class, you're faced with a collection of exams represented by a stream,
      each having a status and a mode of taking - either online (``isOnline=true``) or onsite (``isOnline=false``).

      Your challenge is to dive deep into this collection and uncover some interesting statistics. Here's a
      hint of what you should be looking for:

      - Are all exams in a worthy state to be considered for your analysis? Think about the "VALID" status.

      - We're living in a digital age, but not all exams have embraced the online platform. Can you gauge the distribution?

      - When presenting your findings, a map could be an elegant way to represent the dichotomy of online and onsite exams.

      Remember, it's not just about counting. It's about discerning patterns and presenting them meaningfully. If, hypothetically, you discovered that all exams were conducted online, how would your result reflect that?

   .. note::
      It might be prudent not to specifically mention in the problem statement that streams should be used for the exercise.
      Rather state that the students should use the concepts that were
      introduced in the last week and rename the exercise to something like "Exam System with Streams."
      This recommendation can be applied at any level to every exercise type.

-  **Additional resources:** Give the students the opportunity to read
   more about concepts and problems they struggle with. Provide
   additional links and resources for specific problems. Some examples
   for resources that can be provided for suitable exercises:

   -  how lists work

   -  common sorting algorithms

   -  difference between Queue, Stack and other Collection types

   .. figure:: programming/AI-proof-exercises/Greedy.png
      :align: center
      :width: 50%

      In this exercise additional resources were provided as you can see
      from the blues clickable links in the text. These are about the
      ’greedy’ algorithm and the ’Knapsack’ problem, which were concepts
      that are not explained in the lecture but are important for
      solving the exercise.
