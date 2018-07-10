import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import {Result} from '../result/result.model';
import {Course} from './course.model';
import {Exercise} from '../exercise/exercise.model';
import {Participation} from '../participation';

import { JhiDateUtils } from 'ng-jhipster';

@Injectable()
export class CourseScoreCalculationService {

    private SCORE_NORMALIZATION_VALUE = 0.01;
    private courses: Course[] = [];

    constructor(private dateUtils: JhiDateUtils, private http: HttpClient) { }

    calculateTotalScores(courseExercises: Exercise[]): Map<string, number> {
        const scores = new Map<string, number>();
        let absoluteScore = 0.0;
        let maxScore = 0;
        courseExercises.forEach( exercise => {
            if (exercise.maxScore !== null) {
                maxScore = maxScore + exercise.maxScore;
                const participation: Participation = this.getParticipationForExercise(exercise);
                if (participation !== undefined) {
                    const result: Result = this.getResultForParticipation(participation, exercise.dueDate);
                    absoluteScore = absoluteScore + result.score * this.SCORE_NORMALIZATION_VALUE * exercise.maxScore;
                }
            }
        });
        scores.set('absoluteScore', this.round(absoluteScore, 1));
        if (maxScore > 0) {
            scores.set('relativeScore', this.round((absoluteScore / maxScore) * 100, 1));
        } else {
            scores.set('relativeScore', 0);
        }
        scores.set('maxScore', maxScore);
        return scores;
    }

    private round(value, exp) { // helper function to make actually rounding possible
        if (typeof exp === 'undefined' || +exp === 0) {
            return Math.round(value);
        }

        value = +value;
        exp = +exp;

        if (isNaN(value) || !(typeof exp === 'number' && exp % 1 === 0)) {
            return NaN;
        }

        // Shift
        value = value.toString().split('e');
        value = Math.round(+(value[0] + 'e' + (value[1] ? (+value[1] + exp) : exp)));

        // Shift back
        value = value.toString().split('e');
        return +(value[0] + 'e' + (value[1] ? (+value[1] - exp) : -exp));
    }

    setCourses(courses: Course[]) {
        const coursesArray: Course[] = courses;
        for (let i = 0; i < coursesArray.length; i++) {
            this.courses.push(this.convertCourseFromServer(coursesArray[i]));
        }
    }

    getCourses(): Course[] {
        return this.courses;
    }

    getCourse(courseId: number): Course {
        let course: Course;
        if(this.courses.length > 0) {
            for (let i = 0; i < this.courses.length; i++) {
                if (this.courses[i].id == courseId) {
                    course = this.courses[i];
                    return course;
                }
            }
        }
    }

    getExercisesByCourse(courseId: number) {
        const course: Course = this.getCourse(courseId);
        const exercises: Exercise[] = course.exercises;
        let exercisesArray: Exercise[] = [];
        for (let i = 0; i < exercises.length; i++) {
            exercisesArray.push(this.convertExerciseFromServer(exercises[i]));
        }
        return exercisesArray;
    }

    getParticipationForExercise(exercise: Exercise): Participation {
        let exerciseParticipation: Participation = exercise['participation'];
        return this.convertParticipationFromServer(exerciseParticipation);
    }

    getResultForParticipation(participation: Participation, dueDate: Date): Result {
        const results: Result[] = participation.results;
        const resultsArray: Result[] = [];
        let chosenResult: Result;

        if (results !== undefined) {
            for (let i = 0; i < results.length; i++) {
                resultsArray.push(this.convertResultFromServer(results[i]));
            }

            if (resultsArray.length <= 0) {
                chosenResult = new Result();
                chosenResult.score = 0;
                chosenResult.participation = participation;
                return chosenResult;
            }

            // sorting in descending order to have the last result at the beginning
            resultsArray.sort((result1, result2): number => {
                if (result1.completionDate > result2.completionDate) { return -1; }
                if (result1.completionDate < result2.completionDate) { return 1; }
                return 0;
            });

            if (dueDate != null && dueDate > resultsArray[0].completionDate) {
                // find the first result that is before the due date
                chosenResult = resultsArray[0];
            } else if (dueDate < resultsArray[0].completionDate) {
                chosenResult = new Result();
                chosenResult.score = 0;
            } else {
                chosenResult = resultsArray[resultsArray.length - 1];
            }

            // when the db has stored null for score
            if (chosenResult.score == null) {
                chosenResult.score = 0;
            }

            chosenResult.participation = participation;

        } else {
            chosenResult = new Result();
            chosenResult.score = 0;
        }

        return chosenResult;
    }

    private convertResultFromServer(result: Result): Result {
        const copy: Result = Object.assign({}, result);
        copy.completionDate = this.dateUtils.toDate(result.completionDate);
        return copy;
    }

    private convertParticipationFromServer(participation: Participation): Participation {
        const entity: Participation = Object.assign({}, participation);
        return entity;
    }

    private convertExerciseFromServer(exercise: Exercise): Exercise {
        const entity: Exercise = Object.assign({}, exercise);
        entity.releaseDate = this.dateUtils
            .convertDateTimeFromServer(exercise.releaseDate);
        entity.dueDate = this.dateUtils
            .convertDateTimeFromServer(exercise.dueDate);
        return entity;
    }

    private convertCourseFromServer(course: Course): Course {
        const copy: Course = Object.assign({}, course);
        copy.startDate = this.dateUtils
            .convertDateTimeFromServer(course.startDate);
        copy.endDate = this.dateUtils
            .convertDateTimeFromServer(course.endDate);
        return copy;
    }
}
