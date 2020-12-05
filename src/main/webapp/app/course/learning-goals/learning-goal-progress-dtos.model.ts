export class LearningGoalProgress {
    public learningGoalId: number;
    public learningGoalTitle: string;
    public pointsAchievedByStudentInLearningGoal: number;
    public totalPointsAchievableByStudentsInLearningGoal: number;

    public progressInLectureUnits: LectureUnitProgress[];

    constructor() {}
}

export class LectureUnitProgress {
    public lectureUnitId: number;
    public lectureUnitTitle: string;
    public lectureId: number;
    public lectureTitle: string;

    public pointsAchievedByStudentInLectureUnit: number;
    public totalPointsAchievableByStudentsInLectureUnit: number;

    constructor() {}
}
