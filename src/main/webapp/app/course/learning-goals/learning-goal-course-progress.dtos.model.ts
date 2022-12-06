export class CourseLearningGoalProgress {
    public courseId: number;
    public learningGoalId: number;
    public learningGoalTitle: string;
    public averageScoreAchievedInLearningGoal: number;

    public progressInLectureUnits: CourseLectureUnitProgress[];
}

export class CourseLectureUnitProgress {
    public lectureUnitId: number;
    public averageScoreAchievedByStudentInLectureUnit: number;
    public totalPointsAchievableByStudentsInLectureUnit: number;
    public noOfParticipants: number;
    public participationRate: number;
}
