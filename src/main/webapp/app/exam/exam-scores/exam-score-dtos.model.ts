export class ExamScoreDTO {
    constructor(
        public id: number,
        public title: string,
        public maxPoints: number,
        public averagePointsAchieved: number,
        public exerciseGroups: ExerciseGroup[],
        public studentResults: StudentResult[],
    ) {}
}

export class ExerciseGroup {
    constructor(public id: number, public title: string, public maxPoints: number, public averagePointsAchieved: number, public containedExercises: string[]) {}
}

export class StudentResult {
    constructor(
        public id: number,
        public name: string,
        public login: string,
        public eMail: string,
        public registrationNumber: string,
        public overallPointsAchieved: number,
        public overallScoreAchieved: number,
        public exerciseGroupIdToExerciseResult: MapToExerciseResult,
    ) {}
}

export interface MapToExerciseResult {
    [key: number]: ExerciseResult;
}

export class ExerciseResult {
    constructor(public id: number, public title: string, public maxScore: number, public achievedScore: number, public achievedPoints: number) {}
}
