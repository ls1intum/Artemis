import { JhiAlertService } from 'ng-jhipster';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { ActivatedRoute } from '@angular/router';
import { Course, CourseExerciseService, CourseService } from '../entities/course';
import { Exercise, ExerciseType } from '../entities/exercise';
import { User } from 'app/core';
import { Participation } from 'app/entities/participation';
import * as moment from 'moment';
import { DecimalPipe } from '@angular/common';

@Component({
    selector: 'jhi-instructor-course-dashboard',
    templateUrl: './course-dashboard.component.html',
    providers: [JhiAlertService]
})
export class CourseDashboardComponent implements OnInit, OnDestroy {

    // make constants available to html for comparison
    readonly QUIZ = ExerciseType.QUIZ;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly MODELING = ExerciseType.MODELING;

    course: Course;
    participations: Participation[] = [];
    exercises: Exercise[] = [];
    students: Student[] = [];

    exerciseTitlesPerType = new Map<string, string>();
    exerciseMaxPointsPerType = new Map<string, number>();
    overallMaxPoints = 0;
    exercisesPerType = new Map<string, Exercise[]>();

    exportReady: Boolean = false;

    paramSub: Subscription;
    predicate: string;
    reverse: boolean;

    decimalPipe = new DecimalPipe('en');

    constructor(private route: ActivatedRoute, private courseService: CourseService, private courseExerciseService: CourseExerciseService) {
        this.reverse = false;
        this.predicate = 'id';
    }

    ngOnInit() {
        this.paramSub = this.route.params.subscribe(params => {
            this.courseService.findWithExercises(params['courseId']).subscribe(res => {
                this.course = res.body;
                this.exercises = this.course.exercises.filter(exercise => {
                    return exercise.releaseDate == null || exercise.releaseDate.isBefore(moment());
                });
                this.getParticipationsWithResults(this.course.id);
            });
        });
    }

    getParticipationsWithResults(courseId: number) {
        this.courseService.findAllParticipationsWithResults(courseId).subscribe(participations => {
            this.participations = participations;
            this.groupExercises();
            this.calculatePointsPerStudent();
        });
    }

    groupExercises() {

        for (const exerciseType of Object.values(ExerciseType)) {
            const exercisesPerType = this.exercises.filter(exercise => exercise.type === exerciseType);
            this.exercisesPerType[exerciseType] = exercisesPerType;
            this.exerciseTitlesPerType[exerciseType] = exercisesPerType.map(exercise => exercise.title).join(',');
            this.exerciseMaxPointsPerType[exerciseType] = exercisesPerType.map(exercise => exercise.maxScore ? exercise.maxScore : 0)
                .reduce((total, num) => total + num, 0);
        }
        this.overallMaxPoints = this.exercises.map(exercise => exercise.maxScore ? exercise.maxScore : 0).reduce((total, num) => total + num, 0);
    }

    // creates students and calculates the points for each exercise and exercise type
    calculatePointsPerStudent() {

        const studentsMap = new Map<number, Student>();

        for (const participation of this.participations) {
            if (participation.results != null && participation.results.length > 0) {
                for (const result of participation.results) {
                    // reconnect
                    result.participation = participation;
                }
            }

            // find all students by iterating through the participations
            let student = studentsMap.get(participation.student.id);
            if (student == null) {
                student = new Student(participation.student);
                studentsMap.set(participation.student.id, student);
            }
            student.participations.push(participation);
        }

        studentsMap.forEach(student => {

            this.students.push(student);

            for (const exercise of this.exercises) {
                // TODO: do we need a specific order for the exercises here to get the export correct?
                const participation = student.participations.find(part => part.exercise.id === exercise.id);
                if (participation && participation.results && participation.results.length > 0) {
                    // we found a result, there should only be one
                    const result = participation.results[0];
                    if (participation.results.length > 1) {
                        console.warn('found more than one result for student ' + student.user.login + ' and exercise ' + exercise.title);
                    }

                    const points = result.score * exercise.maxScore / 100;
                    // round to at most 1 decimal place
                    const roundedPoints = this.decimalPipe.transform(points, '1.0-1');

                    student.overallPoints += points;

                    student.pointsPerExerciseType[exercise.type] += points;
                    student.numberOfParticipatedExercises += 1;
                    if (result.score >= 100) {
                        student.numberOfSuccessfulExercises += 1;
                    }

                    student.pointsStringPerExerciseType[exercise.type] += roundedPoints + ',';
                } else {
                    student.pointsStringPerExerciseType[exercise.type] += 0 + ',';
                }
            }
            for (const exerciseType of Object.values(ExerciseType)) {
                if (this.exerciseMaxPointsPerType[exerciseType] > 0) {
                    student.relativeScoresPerExerciseType[exerciseType] = student.pointsPerExerciseType[exerciseType] / this.exerciseMaxPointsPerType[exerciseType] * 100;
                }
            }
        });

        this.exportReady = true;
    }

    exportResults() {
        // method for exporting the csv with the needed data

        if (this.exportReady && this.students.length > 0) {
            const rows: string[] = [];
            // first row with headers
            rows.push('data:text/csv;charset=utf-8,Name,TumId,Email,QuizTotalScore,' + this.exerciseTitlesPerType[ExerciseType.QUIZ] +
                ',ProgrammingTotalScore,' + this.exerciseTitlesPerType[ExerciseType.PROGRAMMING] + ',ModelingTotalScore,' + this.exerciseTitlesPerType[ExerciseType.MODELING] + ',OverallScore');

            for (const student of this.students.values()) {
                let name = student.user.firstName.trim();
                if (student.user.lastName && student.user.lastName != '') {
                    name += ' ' + student.user.lastName;
                }
                const studentId = student.user.login.trim();
                const email = student.user.email.trim();
                const quizTotal = this.decimalPipe.transform(student.pointsPerExerciseType[ExerciseType.QUIZ], '1.0-1');
                const programmingTotal = this.decimalPipe.transform(student.pointsPerExerciseType[ExerciseType.PROGRAMMING], '1.0-1');
                const modelingTotal = this.decimalPipe.transform(student.pointsPerExerciseType[ExerciseType.MODELING], '1.0-1');
                const overallPoints = this.decimalPipe.transform(student.overallPoints, '1.0-1');
                const quizString = student.pointsStringPerExerciseType[ExerciseType.QUIZ];
                const modelingString = student.pointsStringPerExerciseType[ExerciseType.MODELING];
                const programmingString = student.pointsStringPerExerciseType[ExerciseType.PROGRAMMING];
                rows.push(name + ',' + studentId + ',' + email + ',' + quizTotal + ',' + quizString + '' + programmingTotal + ',' + programmingString
                    + '' + modelingTotal + ',' + modelingString + '' + overallPoints);
                //TODO also export student.relativeScoresPerExerciseType
            }
            const csvContent = rows.join('\n');
            const encodedUri = encodeURI(csvContent);
            const link = document.createElement('a');
            link.setAttribute('href', encodedUri);
            link.setAttribute('download', 'course_' + this.course.title + '-scores.csv');
            document.body.appendChild(link); // Required for FF
            link.click();
        }
    }

    callback() {}

    ngOnDestroy() {
        this.paramSub.unsubscribe();
    }
}

class Student {
    user: User;
    participations: Participation[] = [];
    numberOfParticipatedExercises = 0;
    numberOfSuccessfulExercises = 0;
    overallPoints = 0;
    pointsPerExerciseType = new Map<string, number>();
    relativeScoresPerExerciseType = new Map<string, number>();
    pointsStringPerExerciseType = new Map<string, string>();

    constructor(user: User) {
        this.user = user;
        // initialize with 0 or empty string
        for (const exerciseType of Object.values(ExerciseType)) {
            this.pointsPerExerciseType[exerciseType] = 0;
            this.relativeScoresPerExerciseType[exerciseType] = 0;
            this.pointsStringPerExerciseType[exerciseType] = '';
        }
    }
}
