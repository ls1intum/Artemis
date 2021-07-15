import { Injectable } from '@angular/core';
import { Result } from 'app/entities/result.model';
import { Course } from 'app/entities/course.model';
import { Exercise, IncludedInOverallScore } from 'app/entities/exercise.model';
import * as moment from 'moment';
import { Moment } from 'moment';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Participation } from 'app/entities/participation/participation.model';
import { round } from 'app/shared/util/utils';

export const ABSOLUTE_SCORE = 'absoluteScore';
export const RELATIVE_SCORE = 'relativeScore';
export const MAX_POINTS = 'maxPoints';
export const PRESENTATION_SCORE = 'presentationScore';
export const REACHABLE_POINTS = 'reachableScore';
export const CURRENT_RELATIVE_SCORE = 'currentRelativeScore';

@Injectable({ providedIn: 'root' })
export class CourseScoreCalculationService {
    private SCORE_NORMALIZATION_VALUE = 0.01;
    private courses: Course[] = [];

    constructor() {}

    calculateTotalScores(courseExercises: Exercise[]): Map<string, number> {
        const scores = new Map<string, number>();
        let pointsAchievedByStudentInCourse = 0.0;
        let maxPointsInCourse = 0;
        let reachableMaxPointsInCourse = 0;
        let presentationScore = 0;
        for (const exercise of courseExercises) {
            const isExerciseFinished = !exercise.dueDate || exercise.dueDate.isBefore(moment());
            const isAssessmentOver = !exercise.assessmentDueDate || exercise.assessmentDueDate.isBefore(moment());
            const isExerciseIncluded = exercise.includedInOverallScore !== IncludedInOverallScore.NOT_INCLUDED;

            if (isExerciseFinished && isExerciseIncluded) {
                const maxPointsReachableInExercise = exercise.maxPoints!;
                if (exercise.includedInOverallScore === IncludedInOverallScore.INCLUDED_COMPLETELY) {
                    maxPointsInCourse += maxPointsReachableInExercise;
                    // points are reachable if the exercise is released and the assessment is over --> It was possible for the student to get points
                    if (isAssessmentOver) {
                        reachableMaxPointsInCourse += maxPointsReachableInExercise;
                    }
                }
                const participation = this.getParticipationForExercise(exercise);
                if (participation) {
                    const result = this.getResultForParticipation(participation, exercise.dueDate!);
                    if (result && result.rated) {
                        let score = result.score;
                        // this should cover score is undefined and score is null
                        if (score == undefined) {
                            score = 0;
                        }
                        // Note: It is important that we round on the individual exercise level first and then sum up.
                        // This is necessary so that the student arrives at the same overall result when doing his own recalculation.
                        // Let's assume that the student achieved 1.05 points in each of 5 exercises.
                        // In the client, these are now displayed rounded as 1.1 points.
                        // If the student adds up the displayed points, he gets a total of 5.5 points.
                        // In order to get the same total result as the student, we have to round before summing.
                        pointsAchievedByStudentInCourse += round(score * this.SCORE_NORMALIZATION_VALUE * maxPointsReachableInExercise, 1);
                    }
                    presentationScore += participation.presentationScore ? participation.presentationScore : 0;
                }
            }
        }
        scores.set(ABSOLUTE_SCORE, round(pointsAchievedByStudentInCourse, 1));
        if (maxPointsInCourse > 0) {
            scores.set(RELATIVE_SCORE, round((pointsAchievedByStudentInCourse / maxPointsInCourse) * 100, 1));
        } else {
            scores.set(RELATIVE_SCORE, 0);
        }
        if (reachableMaxPointsInCourse > 0) {
            scores.set(CURRENT_RELATIVE_SCORE, round((pointsAchievedByStudentInCourse / reachableMaxPointsInCourse) * 100, 1));
        } else {
            scores.set(CURRENT_RELATIVE_SCORE, 0);
        }
        scores.set(MAX_POINTS, maxPointsInCourse);
        scores.set(PRESENTATION_SCORE, presentationScore);
        scores.set(REACHABLE_POINTS, reachableMaxPointsInCourse);
        return scores;
    }

    updateCourse(course: Course) {
        // filter out the old course object with the same id
        this.courses = this.courses.filter((existingCourses) => existingCourses.id !== existingCourses.id);
        this.courses.push(course);
    }

    setCourses(courses: Course[]) {
        this.courses = courses;
    }

    getCourse(courseId: number) {
        return this.courses.find((course) => course.id === courseId);
    }

    getParticipationForExercise(exercise: Exercise) {
        if (exercise.studentParticipations != undefined && exercise.studentParticipations.length > 0) {
            const exerciseParticipation: StudentParticipation = exercise.studentParticipations[0];
            return CourseScoreCalculationService.convertDateForParticipationFromServer(exerciseParticipation);
        }
    }

    getResultForParticipation(participation: Participation | undefined, dueDate: Moment) {
        if (!participation) {
            return undefined;
        }
        const results = participation.results;
        const resultsArray: Result[] = [];
        let chosenResult: Result;

        if (results) {
            for (let i = 0; i < results.length; i++) {
                resultsArray.push(CourseScoreCalculationService.convertDateForResultFromServer(results[i]));
            }

            if (resultsArray.length <= 0) {
                chosenResult = new Result();
                chosenResult.score = 0;
                return chosenResult;
            }

            const ratedResults = resultsArray.filter((el) => el.rated);

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
            if (dueDate == undefined || dueDate.add(gracePeriodInSeconds, 'seconds') >= resultsArray[0].completionDate!) {
                // find the first result that is before the due date
                chosenResult = resultsArray[0];
            } else if (dueDate.add(gracePeriodInSeconds, 'seconds') < resultsArray[0].completionDate!) {
                chosenResult = new Result();
                chosenResult.score = 0;
            } else {
                chosenResult = resultsArray.last()!;
            }
        } else {
            chosenResult = new Result();
            chosenResult.score = 0;
        }

        return chosenResult;
    }

    private static convertDateForResultFromServer(result: Result): Result {
        result.completionDate = result.completionDate ? moment(result.completionDate) : undefined;
        return result;
    }

    private static convertDateForParticipationFromServer(participation: Participation): Participation {
        participation.initializationDate = participation.initializationDate ? moment(participation.initializationDate) : undefined;
        return participation;
    }
}
