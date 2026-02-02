package de.tum.cit.aet.artemis.tutorialgroup.dto;

import jakarta.validation.constraints.Size;

public record TutorialGroupRegisterStudentDTO(@Size(max = 50) String login, @Size(max = 10) String registrationNumber) {
}
