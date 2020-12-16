export class LearningGoalProgress {
    public learningGoalId: number;
    public learningGoalTitle: string;
    public pointsAchievedByStudentInLearningGoal: number;
    public totalPointsAchievableByStudentsInLearningGoal: number;

    public progressInLectureUnits: LectureUnitProgress[];
}

export class LectureUnitProgress {
    public lectureUnitId: number;

    public scoreAchievedByStudentInLectureUnit: number;
    public totalPointsAchievableByStudentsInLectureUnit: number;
}
