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

    exerciseSuccessfulPerType = new Map<ExerciseType, string>();
    exerciseParticipationsPerType = new Map<ExerciseType, string>();
    exerciseAveragePointsPerType = new Map<ExerciseType, string>();
    exerciseMaxPointsPerType = new Map<ExerciseType, string>();
    exerciseTitlesPerType = new Map<ExerciseType, string>();
    exercisesPerType = new Map<ExerciseType, Exercise[]>();

    exportReady = false;
    paramSub: Subscription;
    predicate: string;
    reverse: boolean;

    // max values
    maxNumberOfPointsPerExerciseType = new Map<ExerciseType, number>();
    maxNumberOfOverallPoints = 0;

    // average values
    averageNumberOfParticipatedExercises = 0;
    averageNumberOfSuccessfulExercises = 0;
    averageNumberOfPointsPerExerciseTypes = new Map<ExerciseType, number>();
    averageNumberOfOverallPoints = 0;

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
                }).sort((e1: Exercise, e2: Exercise) => {
                    if (e1.dueDate > e2.dueDate) {
                        return 1;
                    }
                    if (e1.dueDate < e2.dueDate) {
                        return -1;
                    }
                    if (e1.title > e2.title) {
                        return 1;
                    }
                    if (e1.title < e2.title) {
                        return -1;
                    }
                    return 0;
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
            this.exerciseMaxPointsPerType[exerciseType] = exercisesPerType.map(exercise => exercise.maxScore).join(',');
            this.maxNumberOfPointsPerExerciseType[exerciseType] = exercisesPerType.map(exercise => exercise.maxScore ? exercise.maxScore : 0)
                .reduce((total, num) => total + num, 0);
        }
        this.maxNumberOfOverallPoints = this.exercises.map(exercise => exercise.maxScore ? exercise.maxScore : 0).reduce((total, num) => total + num, 0);
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

        // prepare exercises
        for (const exercise of this.exercises) {
            exercise.numberOfParticipationsWithRatedResult = 0;
            exercise.numberOfSuccessfulParticipations = 0;
        }

        studentsMap.forEach(student => {

            this.students.push(student);

            for (const exercise of this.exercises) {

                const participation = student.participations.find(part => part.exercise.id === exercise.id);
                if (participation && participation.results && participation.results.length > 0) {
                    // we found a result, there should only be one
                    const result = participation.results[0];
                    if (participation.results.length > 1) {
                        console.warn('found more than one result for student ' + student.user.login + ' and exercise ' + exercise.title);
                    }

                    const studentExerciseResultPoints = result.score * exercise.maxScore / 100;
                    // round to at most 1 decimal place
                    const roundedPoints = this.round(studentExerciseResultPoints);

                    student.overallPoints += studentExerciseResultPoints;
                    student.pointsPerExercise[exercise.id] = studentExerciseResultPoints;

                    student.pointsPerExerciseType[exercise.type] += studentExerciseResultPoints;
                    student.numberOfParticipatedExercises += 1;
                    exercise.numberOfParticipationsWithRatedResult += 1;
                    if (result.score >= 100) {
                        student.numberOfSuccessfulExercises += 1;
                        exercise.numberOfSuccessfulParticipations += 1;
                    }

                    student.pointsStringPerExerciseType[exercise.type] += roundedPoints + ',';
                } else {
                    student.pointsPerExercise[exercise.id] = 0;
                    student.pointsStringPerExerciseType[exercise.type] += 0 + ',';
                }
            }
            for (const exerciseType of Object.values(ExerciseType)) {
                if (this.maxNumberOfPointsPerExerciseType[exerciseType] > 0) {
                    student.scoresPerExerciseType[exerciseType] = student.pointsPerExerciseType[exerciseType] / this.maxNumberOfPointsPerExerciseType[exerciseType] * 100;
                }
            }
        });

        for (const exerciseType of Object.values(ExerciseType)) {
            this.averageNumberOfPointsPerExerciseTypes[exerciseType] = this.students.map(student => student.pointsPerExerciseType[exerciseType])
                .reduce((total, num) => total + num, 0) / this.students.length;
        }

        this.averageNumberOfOverallPoints = this.students.map(student => student.overallPoints).reduce((total, num) => total + num, 0) / this.students.length;
        this.averageNumberOfSuccessfulExercises = this.students.map(student => student.numberOfSuccessfulExercises).reduce((total, num) => total + num, 0) / this.students.length;
        this.averageNumberOfParticipatedExercises = this.students.map(student => student.numberOfParticipatedExercises).reduce((total, num) => total + num, 0) / this.students.length;

        for (const exerciseType of Object.values(ExerciseType)) {
            this.exerciseAveragePointsPerType[exerciseType] = ''; // initialize with empty string
            this.exerciseParticipationsPerType[exerciseType] = ''; // initialize with empty string
            this.exerciseSuccessfulPerType[exerciseType] = ''; // initialize with empty string

            for (const exercise of this.exercisesPerType[exerciseType]) {
                exercise.averagePoints = this.students.map(student => student.pointsPerExercise[exercise.id]).reduce((total, num) => total + num, 0) / this.students.length;
                const roundedPoints = this.round(exercise.averagePoints);
                this.exerciseAveragePointsPerType[exerciseType] += roundedPoints + ',';
                this.exerciseParticipationsPerType[exerciseType] += exercise.numberOfParticipationsWithRatedResult + ',';
                this.exerciseSuccessfulPerType[exerciseType] += exercise.numberOfSuccessfulParticipations + ',';
            }
        }

        this.exportReady = true;
    }

    exportResults() {
        // method for exporting the csv with the needed data

        if (this.exportReady && this.students.length > 0) {
            const rows: string[] = [];
            // first row with headers
            let firstRowString = 'data:text/csv;charset=utf-8,Name,Username,Email,';
            for (const exerciseType of Object.values(ExerciseType)) {
                const exerciseTypeName = capitalizeFirstLetter(exerciseType);

                // only add it if there are actually exercises in this type
                if (this.exerciseTitlesPerType[exerciseType] && this.exerciseTitlesPerType[exerciseType] !== '') {
                    firstRowString += this.exerciseTitlesPerType[exerciseType] + ',' + exerciseTypeName + ' Points,' + exerciseTypeName + ' Score,';
                }
            }
            rows.push(firstRowString + 'Overall Points,Overall Score');

            for (const student of this.students.values()) {
                let name = student.user.firstName.trim();
                if (student.user.lastName && student.user.lastName !== '') {
                    name += ' ' + student.user.lastName;
                }
                const studentId = student.user.login.trim();
                const email = student.user.email.trim();

                let rowString = name + ',' + studentId + ',' + email + ',';

                const exercisePointsPerType = new Map<ExerciseType, string>();
                const exerciseScoresPerType = new Map<ExerciseType, string>();

                for (const exerciseType of Object.values(ExerciseType)) {
                    // only add it if there are actually exercises in this type
                    if (this.exerciseTitlesPerType[exerciseType] && this.exerciseTitlesPerType[exerciseType] !== '') {
                        exercisePointsPerType[exerciseType] = this.round(student.pointsPerExerciseType[exerciseType]);
                        exerciseScoresPerType[exerciseType] = '';
                        if (this.maxNumberOfPointsPerExerciseType[exerciseType] > 0) {
                            exerciseScoresPerType[exerciseType] = this.round(student.pointsPerExerciseType[exerciseType] / this.maxNumberOfPointsPerExerciseType[exerciseType] * 100) + '%';
                        }
                        rowString += student.pointsStringPerExerciseType[exerciseType] + '' + exercisePointsPerType[exerciseType] + ',' + exerciseScoresPerType[exerciseType] + ',';
                    }
                }

                const overallPoints = this.round(student.overallPoints);
                const overallScore = this.round(student.overallPoints / this.maxNumberOfOverallPoints * 100) + '%';

                rows.push(rowString + overallPoints + ',' + overallScore);
            }

            // max values
            let rowStringMax = 'Max' + ',,,';

            const maxPointsPerType = new Map<ExerciseType, string>();
            const maxScore = '100%';
            for (const exerciseType of Object.values(ExerciseType)) {
                // only add it if there are actually exercises in this type
                if (this.exerciseTitlesPerType[exerciseType] && this.exerciseTitlesPerType[exerciseType] !== '') {
                    maxPointsPerType[exerciseType] = this.round(this.maxNumberOfPointsPerExerciseType[exerciseType]);
                    rowStringMax += this.exerciseMaxPointsPerType[exerciseType] + ',' + maxPointsPerType[exerciseType] + ',' + maxScore + ',';
                }
            }
            const maxOverallPoints = this.round(this.maxNumberOfOverallPoints);
            const maxOverallScore = '100%';

            rows.push(rowStringMax + maxOverallPoints + ',' + maxOverallScore);

            // average values
            let rowStringAverage = 'Average' + ',,,';

            const averagePointsPerType = new Map<ExerciseType, string>();
            const averageScorePerType = new Map<ExerciseType, string>();
            for (const exerciseType of Object.values(ExerciseType)) {
                // only add it if there are actually exercises in this type
                if (this.exerciseTitlesPerType[exerciseType] && this.exerciseTitlesPerType[exerciseType] !== '') {
                    averagePointsPerType[exerciseType] = this.round(this.averageNumberOfPointsPerExerciseTypes[exerciseType]);
                    averageScorePerType[exerciseType] = this.round(this.averageNumberOfPointsPerExerciseTypes[exerciseType] / this.maxNumberOfPointsPerExerciseType[exerciseType] * 100) + '%';
                    rowStringAverage += this.exerciseAveragePointsPerType[exerciseType] + '' + averagePointsPerType[exerciseType] + ',' + averageScorePerType[exerciseType] + ',';
                }
            }
            const averageOverallPoints = this.round(this.averageNumberOfOverallPoints);
            const averageOverallScore = this.round(this.averageNumberOfOverallPoints / this.maxNumberOfOverallPoints * 100) + '%';

            rows.push(rowStringAverage + averageOverallPoints + ',' + averageOverallScore);

            // participation
            let rowStringParticipation = 'Number of participations' + ',,,';

            for (const exerciseType of Object.values(ExerciseType)) {
                // only add it if there are actually exercises in this type
                if (this.exerciseTitlesPerType[exerciseType] && this.exerciseTitlesPerType[exerciseType] !== '') {
                    rowStringParticipation += this.exerciseParticipationsPerType[exerciseType] + ',,';
                }
            }
            rows.push(rowStringParticipation + ',');

            // successful
            let rowStringSuccuessful = 'Number of successful participations' + ',,,';

            for (const exerciseType of Object.values(ExerciseType)) {
                // only add it if there are actually exercises in this type
                if (this.exerciseTitlesPerType[exerciseType] && this.exerciseTitlesPerType[exerciseType] !== '') {
                    rowStringSuccuessful += this.exerciseSuccessfulPerType[exerciseType] + ',,';
                }
            }
            rows.push(rowStringSuccuessful + ',');

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

    round(number: number) {
        return this.decimalPipe.transform(number, '1.0-1');
    }
}

class Student {
    user: User;
    participations: Participation[] = [];
    numberOfParticipatedExercises = 0;
    numberOfSuccessfulExercises = 0;
    overallPoints = 0;
    pointsPerExercise = new Map<number, number>();  // the index is the exercise id
    pointsPerExerciseType = new Map<ExerciseType, number>();  // the absolute number of points the students received per exercise type
    scoresPerExerciseType = new Map<ExerciseType, number>();  // the relative number of points the students received per exercise type (i.e. divided by the max points per exercise type)
    pointsStringPerExerciseType = new Map<ExerciseType, string>();  // a string containing the points for all exercises of a specific type

    constructor(user: User) {
        this.user = user;
        // initialize with 0 or empty string
        for (const exerciseType of Object.values(ExerciseType)) {
            this.pointsPerExerciseType[exerciseType] = 0;
            this.scoresPerExerciseType[exerciseType] = 0;
            this.pointsStringPerExerciseType[exerciseType] = '';
        }
    }
}

function capitalizeFirstLetter(string: String) {
    return string.charAt(0).toUpperCase() + string.slice(1);
}
