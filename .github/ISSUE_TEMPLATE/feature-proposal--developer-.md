---
name: Feature Proposal (Developer)
about: Software Engineering Process for a new feature
title: "[Feature Proposal]"
labels: feature-proposal
assignees: ''

---

> Feature Template Spec Version 0.1

## Context

### Problem  
> Describe the problem that is tackled in this issue

### Motivation 
> Describe the motivation WHY the problem needs solving. Include the affected users/roles here. 

---
## Requirements Engineering 

### Existing (Problematic) Solution / System 
> What is the current solution (if there is one)? What is the problem with the current solution? 
> You may include a UML Model here 

### Proposed System 
> How should the perfect solution look like? 

### Requirements 
> Describe the Functional and Non-Functional Requirements of the feature. Stick to the INVEST methodology! 
> 1. FR: <Title>: <Description> 
>
> 1. NFR: <FURPS+ Category>: <Title>: <Description>

---
## Analysis

### Analysis Object Model 
> What are the involved Analysis Objects? 

### Dynamic Behavior 
> Include dynamic models (Activity Diagram, State Chart Diagram, Communication Diagram) here to outline the dynamic nature of the PROBLEM 


---
## System Design 

### Subsystem Decomposition
> Show the involved subsystems and their interfaces. Make sure to describe the APIs that you add/change in detail. Model the DTOs you intend to (re)use or change! 

### Persistent Data Management
> Describe the Database changes you intend to make.
> Outline new config options you will add.
> Describe all other data persistency mechanisms you may use. 

### Access Control / Security Aspects 
> Describe the access control considerations for your feature

### Other Design Decisions
> Potential candidates to discuss here: Websockets, Test strategy 

---
## UI / UX 
> Describe the user flow (references to dynamic model). 
> Screenshots of the final UI mockup
