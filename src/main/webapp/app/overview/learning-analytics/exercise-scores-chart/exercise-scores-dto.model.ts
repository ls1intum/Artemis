import { Moment } from 'moment';

export class ExerciseScoresDTO {
    public exerciseId: number;
    public exerciseTitle: string;
    public studentScore: number;
    public averageScore: number;
    public releaseDate: Moment;

    constructor(exerciseId: number, exerciseTitle: string, studentScore: number, averageScore: number, releaseDate: Moment) {
        this.exerciseId = exerciseId;
        this.exerciseTitle = exerciseTitle;
        this.studentScore = studentScore;
        this.averageScore = averageScore;
        this.releaseDate = releaseDate;
    }
}
