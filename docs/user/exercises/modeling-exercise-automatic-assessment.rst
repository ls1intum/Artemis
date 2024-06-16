.. _generation_of_assessment_suggestions_for_modeling_exercises :

:orphan:

Generation of Assessment Suggestions for Modeling Exercises
===========================================================

Suggestion Generation Process
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
This section intends to provide insights into how automated feedback suggestions are generated for modeling exercises using Athena.
While Athena does support having various evaluation modules per exercise type, at the moment only one module exists for the evaluation of modeling exercises (``module_modeling_llm``).
The following section therefore outlines the generation process implemented in this module which uses a Large Language Model (LLM) internally to generate feedback through the following process:

1. **Feedback Request Reception:** Upon receiving a feedback request, the corresponding modeling submission is serialized into an appropriate exchange format depending on the diagram type.
For BPMN diagrams, BPMN 2.0 XML is used as it is a commonly used exchange format for process models and proved to be well-understood by LLMs.
IDs of diagram elements are shortened during serialization to minimize the token count of the input provided to the language model.

2. **Prompt Input Collection:** The module gathers all required input to query the connected language model. This includes:

- Number of points and bonus points achievable
- Grading instructions
- Problem statement
- Explanation of the submission format
- Optional example solution
- Serialized submission

3. **Prompt Template Filling:** The collected input is used to fill in the prompt template. If the prompt exceeds the language model's token limit, omittable features are removed in the following order: example solution, grading instructions, and problem statement.
The system can still provide improvement suggestions without detailed grading instructions.

4. **Token Limit Check:** Feedback generation is aborted if the prompt is still too long after removing omittable features.
Otherwise, the prompt is executed on the connected language model.

5. **Response Parsing:** The model's response is parsed into a dictionary representation.
Feedback items are mapped back to their original element IDs, ensuring that the feedback suggestions can be attached to referenced elements in the original diagram.

.. figure:: modeling/modeling-llm-activity.svg
          :align: center

Optimizing Exercises for Automated Assessment
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
A few best practices should be considered to get the best possible assessment suggestions for a modeling exercise.
As the current version of the module for generating suggestions for modeling exercises is based on a large language model, when composing grading instructions for an exercise, it is advisable to follow similar strategies as for prompt engineering an LLM: https://platform.openai.com/docs/guides/prompt-engineering

One of the strategies for optimizing the prompt results of an LLM is instructing the model as clearly as possible about the expected output of the task at hand.
The following listing shows grading instructions for an exemplary BPMN process modeling exercise optimized for automatic assessment.
The instructions explicitly list all aspects Athena should assess of and how credits should be assigned accordingly ensuring consistent suggestions across all submissions.

.. code-block:: html

    Evaluate the following 10 criteria:

    1. Give 1 point if all elements described in the problem statement are present in the submission, 0 otherwise.
    2. Give 1 point if the outgoing flows from an exclusive gateway are also labeled if there is more than one outgoing flow from the exclusive gateway, 0 otherwise.
    3. Give 1 point if a start-event is present in the student's submission, 0 otherwise.
    4. Give 1 point if an end-event is present in the student's submission, 0 otherwise.
    5. Give 0 points if the activities in the diagram are not in the correct order according to the problem statement, 1 otherwise.
    6. Give 1 point if all pools and swimlanes are labeled, 0 otherwise.
    7. Give 1 point if the submission does not contain elements that are not described in the problem statement, 0 otherwise.
    8. Give 1 point if all diagram elements are connected, 0 otherwise.
    9. Give 1 point if all tasks are named in the "Verb Object"-format where a name consists of a verb followed by the object, 0 otherwise.
    10. Give 1 point if no sequence flows connect elements in two different pools, 0 otherwise.
