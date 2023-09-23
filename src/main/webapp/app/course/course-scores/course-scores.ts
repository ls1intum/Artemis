export class StudentScores {
    absoluteScore: number;
    relativeScore: number;
    currentRelativeScore: number;
    presentationScore: number;
}

export class CourseScores {
    maxPoints: number;
    reachablePoints: number;
    reachablePresentationPoints: number;
    studentScores: StudentScores;

    constructor(maxPoints: number, reachablePoints: number, reachablePresentationPoints: number, studentScores: StudentScores) {
        this.maxPoints = maxPoints;
        this.reachablePoints = reachablePoints;
        this.reachablePresentationPoints = reachablePresentationPoints;
        this.studentScores = studentScores;
    }
}
