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
 * This class is used to define modeling tasks for the guided tutorial and assess the created UML model in the editor
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

export class AssessmentObject {
    assessments: number;
    totalScore: number;

    constructor(assessments: number, totalScore: number) {
        this.assessments = assessments;
        this.totalScore = totalScore;
    }
}
