package de.tum.in.www1.artemis.web.rest.dto;

import de.tum.in.www1.artemis.domain.exam.StudentExam;

public class ExamSessionDTO {

    public Long id;

    public StudentExam studentExam;

    public String sessionToken;

    public String userAgent;

    public String browserFingerprintHash;

}
