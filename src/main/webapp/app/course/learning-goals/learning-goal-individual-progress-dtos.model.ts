export class IndividualLearningGoalProgress {
    public studentId: number;
    public learningGoalId: number;
    public learningGoalTitle: string;
    public pointsAchievedByStudentInLearningGoal: number;
    public totalPointsAchievableByStudentsInLearningGoal: number;

    public progressInLectureUnits: IndividualLectureUnitProgress[];
}

export class IndividualLectureUnitProgress {
    public lectureUnitId: number;
    public scoreAchievedByStudentInLectureUnit: number;
    public totalPointsAchievableByStudentsInLectureUnit: number;
}
