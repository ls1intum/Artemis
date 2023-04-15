import { ScoreType } from 'app/shared/constants/score-type.constants';

export class StudentScores {
    absoluteScore: number;
    relativeScore: number;
    currentRelativeScore: number;
    presentationScore: number;
}

export class CourseScores {
    maxPoints: number;
    reachablePoints: number;
    studentScores: StudentScores;

    constructor(maxPoints: number, reachablePoints: number, studentScores: StudentScores) {
        this.maxPoints = maxPoints;
        this.reachablePoints = reachablePoints;
        this.studentScores = studentScores;
    }

    // Retrieve the score for a specific ScoreType from the CourseScores object.
    // The MAX_POINTS and REACHABLE_POINTS belong to the course.
    // All other ScoreTypes inform about the student's personal score and are stored in the StudentScores object.
    getScoreByScoreType(scoreType: ScoreType): number {
        switch (scoreType) {
            case ScoreType.MAX_POINTS:
                return this.maxPoints;
            case ScoreType.REACHABLE_POINTS:
                return this.reachablePoints;
            case ScoreType.ABSOLUTE_SCORE:
                return this.studentScores.absoluteScore;
            case ScoreType.RELATIVE_SCORE:
                return this.studentScores.relativeScore;
            case ScoreType.CURRENT_RELATIVE_SCORE:
                return this.studentScores.currentRelativeScore;
            case ScoreType.PRESENTATION_SCORE:
                return this.studentScores.presentationScore;
        }
    }
}
