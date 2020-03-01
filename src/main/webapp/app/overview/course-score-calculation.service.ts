import { Injectable } from '@angular/core';
import { Result } from 'app/entities/result.model';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import * as moment from 'moment';
import { Moment } from 'moment';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Participation } from 'app/entities/participation/participation.model';

export const ABSOLUTE_SCORE = 'absoluteScore';
export const RELATIVE_SCORE = 'relativeScore';
export const MAX_SCORE = 'maxScore';
export const PRESENTATION_SCORE = 'presentationScore';

@Injectable({ providedIn: 'root' })
export class CourseScoreCalculationService {
    private SCORE_NORMALIZATION_VALUE = 0.01;
    private courses: Course[] = [];

    constructor() {}

    calculateTotalScores(courseExercises: Exercise[]): Map<string, number> {
        const scores = new Map<string, number>();
        let absoluteScore = 0.0;
        let maxScore = 0;
        let presentationScore = 0;
        for (const exercise of courseExercises) {
            if (exercise.maxScore != null && (!exercise.dueDate || exercise.dueDate.isBefore(moment()))) {
                maxScore = maxScore + exercise.maxScore;
                const participation = this.getParticipationForExercise(exercise);
                if (participation !== null) {
                    const result = this.getResultForParticipation(participation, exercise.dueDate!);
                    if (result !== null && result.rated) {
                        let score = result.score;
                        if (score === null) {
                            score = 0;
                        }
                        absoluteScore = absoluteScore + score * this.SCORE_NORMALIZATION_VALUE * exercise.maxScore;
                    }
                    presentationScore += participation.presentationScore !== undefined ? participation.presentationScore : 0;
                }
            }
        }
        scores.set(ABSOLUTE_SCORE, CourseScoreCalculationService.round(absoluteScore, 1));
        if (maxScore > 0) {
            scores.set(RELATIVE_SCORE, CourseScoreCalculationService.round((absoluteScore / maxScore) * 100, 1));
        } else {
            scores.set(RELATIVE_SCORE, 0);
        }
        scores.set(MAX_SCORE, maxScore);
        scores.set(PRESENTATION_SCORE, presentationScore);
        return scores;
    }

    private static round(value: any, exp: number) {
        // helper function to make actually rounding possible
        if (typeof exp === 'undefined' || +exp === 0) {
            return Math.round(value);
        }

        value = +value;
        exp = +exp;

        if (isNaN(value) || !(exp % 1 === 0)) {
            return NaN;
        }

        // Shift
        value = value.toString().split('e');
        value = Math.round(+(value[0] + 'e' + (value[1] ? +value[1] + exp : exp)));

        // Shift back
        value = value.toString().split('e');
        return +(value[0] + 'e' + (value[1] ? +value[1] - exp : -exp));
    }

    setCourses(courses: Course[]) {
        this.courses = courses;
    }

    getCourse(courseId: number): Course | null {
        return this.courses.find(course => course.id === courseId) || null;
    }

    getParticipationForExercise(exercise: Exercise): Participation | null {
        if (exercise.studentParticipations != null && exercise.studentParticipations.length > 0) {
            const exerciseParticipation: StudentParticipation = exercise.studentParticipations[0];
            return CourseScoreCalculationService.convertDateForParticipationFromServer(exerciseParticipation);
        } else {
            return null;
        }
    }

    getResultForParticipation(participation: Participation, dueDate: Moment): Result | null {
        if (participation === null) {
            return null;
        }
        const results: Result[] = participation.results;
        const resultsArray: Result[] = [];
        let chosenResult: Result;

        if (results !== undefined) {
            for (let i = 0; i < results.length; i++) {
                resultsArray.push(CourseScoreCalculationService.convertDateForResultFromServer(results[i]));
            }

            if (resultsArray.length <= 0) {
                chosenResult = new Result();
                chosenResult.score = 0;
                return chosenResult;
            }

            const ratedResults = resultsArray.filter(el => el.rated);

            if (ratedResults.length === 1) {
                return ratedResults[0];
            }

            // sorting in descending order to have the last result at the beginning
            resultsArray.sort((result1, result2): number => {
                if (result1.completionDate! > result2.completionDate!) {
                    return -1;
                }
                if (result1.completionDate! < result2.completionDate!) {
                    return 1;
                }
                return 0;
            });

            const gracePeriodInSeconds = 10;
            if (dueDate === null || dueDate.add(gracePeriodInSeconds, 'seconds') >= resultsArray[0].completionDate!) {
                // find the first result that is before the due date
                chosenResult = resultsArray[0];
            } else if (dueDate.add(gracePeriodInSeconds, 'seconds') < resultsArray[0].completionDate!) {
                chosenResult = new Result();
                chosenResult.score = 0;
            } else {
                chosenResult = resultsArray[resultsArray.length - 1];
            }
        } else {
            chosenResult = new Result();
            chosenResult.score = 0;
        }

        return chosenResult;
    }

    private static convertDateForResultFromServer(result: Result): Result {
        result.completionDate = result.completionDate != null ? moment(result.completionDate) : null;
        return result;
    }

    private static convertDateForParticipationFromServer(participation: Participation): Participation {
        participation.initializationDate = participation.initializationDate != null ? moment(participation.initializationDate) : null;
        return participation;
    }
}
