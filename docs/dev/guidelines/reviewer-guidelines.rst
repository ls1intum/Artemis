Reviewer Guidelines
===================

This document provides best-practice guidelines for reviewers participating in code reviews for the Artemis project.
These guidelines are intended to ensure high-quality, constructive, and efficient reviews, and are based on open source best practices as well as the Artemis development process.

.. attention::
    **Pull Request (PR) reviews are a crucial part of the development process.**
    They ensure code quality, knowledge sharing, and collective ownership of the codebase.
    It is very important that **all** developers actively participate in reviews and take the time to provide thoughtful, constructive feedback.
    A careful and diligent review process strengthens both the project and the team.
    Reviews work best as a give-and-take: if you'd like your PRs reviewed, make sure you also review others' PRs.

General Principles
------------------

- Be respectful and constructive. Remember that code review is a collaborative process.
- Focus on the code, not the coder. Avoid personal comments.
- Be clear and specific in your feedback. Provide actionable suggestions.
- Prioritize critical issues (correctness, security, stability) over minor style or preference issues.
- Encourage learning and knowledge sharing.

Review Workflow
---------------
1. **Preparation**

   - Make sure that the PR is marked as "ready for review" and not in a draft state. Afterwards, add yourself to the reviewers.
   - Read the pull request (PR) description and related issue(s) to understand the context and requirements.
   - Review the "Steps for Testing" and any provided documentation.
   - Check that all relevant checklist items in the pull request template are checked by the developer and that non-applicable items have been removed. If you notice missing or incorrectly checked items, request clarification or corrections from the author.

2. **Testing**

   - Deploy the PR to a test server and verify its functionality. See :doc:`../testservers` for instructions, and use the provided `Test Accounts <https://confluence.aet.cit.tum.de/spaces/ArTEMiS/pages/25252245/Test+Accounts+Test+Servers#TestAccounts%26TestServers-TestAccounts>`__ for logging in.
   - Follow the provided testing steps to verify new and changed functionality.
   - Additionally, test related or potentially affected functionality beyond the provided steps to identify possible side effects or regressions.
   - If possible, test on all relevant platforms and configurations.

3. **Code Quality**

   - Ensure code adheres to the project's :doc:`../guidelines`.
   - Check for clear, self-explanatory code and adequate comments for complex logic.
   - Look for sufficient documentation, especially for public APIs and new features.
   - Verify that code is modular, maintainable, and avoids duplication.

4. **Correctness and Functionality**

   - Confirm that the code meets the requirements and solves the described problem.
   - Check for proper error handling and edge case coverage.
   - Ensure that tests (unit, integration, or end-to-end) are present and meaningful.

5. **Performance and Security**

   - Identify any performance bottlenecks or unnecessary resource usage.
   - Check for potential security vulnerabilities or data leaks.

6. **Scope and Relevance**

   - Ensure the PR is focused and does not include unrelated changes.
   - If unrelated issues are found, suggest addressing them in a separate PR.

7. **Feedback and Communication**

   - When requesting changes, explain your reasoning (e.g., "Please change X to Y, because this improves Z").
   - Use GitHub's review tools to submit your status (Approve or Request Changes) and summarize your review.
   - Respond to questions and clarifications from the author promptly.
   - Mark conversations as resolved when addressed.

8. **Approval and Follow-up**

   - Approve the PR when all concerns are addressed and the code meets project standards.
   - In your approval comment, specify whether you reviewed only the code, only tested the changes, or performed both.
   - As a reviewer, check off the relevant review, test, and performance checklist items in the pull request template once you have completed them (e.g., code review, manual test, exam mode test, performance review).

Hephaestus Integration
----------------------

To support continuous improvement and engagement in the review process, Artemis uses `Hephaestus <https://hephaestus.aet.cit.tum.de/>`__:

   - Code Review Gamification: Reviews contribute to weekly leaderboards and team competitions.
   - AI-Powered Mentorship: Personalized feedback, reflective sessions, and goal-setting help reviewers improve their skills.
   - Motivation and Tracking: Use Hephaestus to monitor your contributions across repositories and to stay engaged with the team’s collective review effort.


Additional Resources
--------------------

- :doc:`../guidelines`
- :doc:`../development-process/development-process`
- `GitHub Pull Request Review Documentation <https://docs.github.com/en/github/collaborating-with-issues-and-pull-requests/about-pull-request-reviews>`__
- `Hephaestus <https://hephaestus.aet.cit.tum.de/>`__
