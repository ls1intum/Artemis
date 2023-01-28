class StudentScores {
    absoluteScore: number;
    relativeScore: number;
    currentRelativeScore: number;
    presentationScore: number;
}

export class CourseScoresDTO {
    maxPoints: number;
    reachablePoints: number;
    studentScores: StudentScores;
}
