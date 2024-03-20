Benchmarking Tool
=================

The Artemis Benchmarking Tool is a web application designed for Artemis administrators to test the performance of Artemis under heavy load.
It is available on `GitHub <https://github.com/ls1intum/Artemis-Benchmarking>`_.

The Benchmarking Tool for Artemis is designed to simulate realistic user interactions with the Artemis platform,
particularly focusing on exam conduction where the system's performance and scalability are most critical.

This tool enables system administrators and developers to assess and analyze Artemis's behavior under simulated load conditions,
providing valuable insights into response times, system workload, and overall stability.
These insights can be used to identify the limits of the hardware infrastructure as well as the software architecture,
thereby allowing for targeted optimizations and improvements.
The main goal is to ensure that the respective Artemis setup can handle the expected load during exams without any performance issues.

During an exam simulation, the tool participates in the exam on behalf of a configurable number of students.
It performs various actions, such as logging in, starting the exam, submitting answers, and git operations.
For each action, the tool measures the time it takes to complete the action and records the result.
Additionally, the tool can be connected to Prometheus to collect and visualize workload metrics.

For more information on how to set up and use the Benchmarking Tool, please refer to the `README <https://github.com/ls1intum/Artemis-Benchmarking?tab=readme-ov-file#readme>`_.
