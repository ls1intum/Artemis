/**
 * This class is used to define modeling tasks for the guided tutorial and assess the created UML model in the editor
 */
export class GuidedTourModelingTask {
    /** Name of the UML element that has to be created */
    umlName: string;
    /** Translate key of the task description */
    taskTranslateKey: string;

    constructor(umlName: string, taskTranslateKey: string) {
        this.umlName = umlName;
        this.taskTranslateKey = taskTranslateKey;
    }
}

/** Person class with attribute */
export const personUML = {
    name: 'Person',
    attribute: 'name: String',
};

/** Student class with attribute and method */
export const studentUML = {
    name: 'Student',
    attribute: 'major: String',
    method: 'visitLecture()',
};

/** Association */
export const associationUML = {
    name: 'Association',
};

/**
 * This class is used to define assessment tasks for the guided tutorial and assess the input for the example submission assessment
 * of the tutor
 */
export class GuidedTourAssessmentTask {
    /** Translate key of the task description */
    taskTranslateKey: string;
    assessmentObject: AssessmentObject;

    constructor(taskTranslateKey: string, assessmentsObject: AssessmentObject) {
        this.taskTranslateKey = taskTranslateKey;
        this.assessmentObject = assessmentsObject;
    }
}

/**
 * This class defines the expected assessment in terms of number of assessments and total score of the assessment
 */
export class AssessmentObject {
    /** Number of assessments */
    assessments: number;
    /** Total score of the assessment */
    totalScore: number;

    constructor(assessments: number, totalScore: number) {
        this.assessments = assessments;
        this.totalScore = totalScore;
    }
}
