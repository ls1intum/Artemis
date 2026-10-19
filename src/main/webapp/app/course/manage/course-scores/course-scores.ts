/** Score container populated from server data (or built via object literal); fields are always present once assigned, hence the definite-assignment (!) markers. */
export class StudentScores {
    absoluteScore!: number;
    // The total points the student achieved if the points of exercise variants were not capped at their group's maxPoints.
    // Equals absoluteScore when no variant group exceeds its cap.
    absoluteScoreTotal!: number;
    relativeScore!: number;
    currentRelativeScore!: number;
    presentationScore!: number;
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
