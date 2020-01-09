import { JhiAlertService } from 'ng-jhipster';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { ActivatedRoute } from '@angular/router';
import { Course } from '../entities/course';
import { CourseService } from 'app/entities/course/course.service';
import { CourseExerciseService } from 'app/entities/course/course.service';
import { Exercise, ExerciseType } from '../entities/exercise';
import { User } from 'app/core';
import * as moment from 'moment';
import { DecimalPipe } from '@angular/common';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';

@Component({
    selector: 'jhi-course-scores',
    templateUrl: './course-scores.component.html',
    providers: [JhiAlertService],
})
export class CourseScoresComponent implements OnInit, OnDestroy {
    // supported exercise type
    readonly exerciseTypes = [ExerciseType.QUIZ, ExerciseType.PROGRAMMING, ExerciseType.MODELING, ExerciseType.TEXT, ExerciseType.FILE_UPLOAD];

    course: Course;
    participations: StudentParticipation[] = [];
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
                this.course = res.body!;
                this.exercises = this.course.exercises
                    .filter(exercise => {
                        return exercise.releaseDate == null || exercise.releaseDate.isBefore(moment());
                    })
                    .sort((e1: Exercise, e2: Exercise) => {
                        if (e1.dueDate! > e2.dueDate!) {
                            return 1;
                        }
                        if (e1.dueDate! < e2.dueDate!) {
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
        for (const exerciseType of this.exerciseTypes) {
            const exercisesPerType = this.exercises.filter(exercise => exercise.type === exerciseType);
            this.exercisesPerType.set(exerciseType, exercisesPerType);
            this.exerciseTitlesPerType.set(exerciseType, exercisesPerType.map(exercise => exercise.title).join(','));
            this.exerciseMaxPointsPerType.set(exerciseType, exercisesPerType.map(exercise => exercise.maxScore).join(','));
            this.maxNumberOfPointsPerExerciseType.set(
                exerciseType,
                exercisesPerType.reduce((total, exercise) => total + (exercise.maxScore ? exercise.maxScore : 0), 0),
            );
        }
        this.maxNumberOfOverallPoints = this.exercises.reduce((total, exercise) => total + (exercise.maxScore ? exercise.maxScore : 0), 0);
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
            let student = studentsMap.get(participation.student.id!);
            if (student == null) {
                student = new Student(participation.student);
                studentsMap.set(participation.student.id!, student);
            }
            student.participations.push(participation);
            if (participation.presentationScore) {
                student.presentationScore += participation.presentationScore;
            }
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

                    const studentExerciseResultPoints = (result.score * exercise.maxScore) / 100;
                    // round to at most 1 decimal place
                    const roundedPoints = this.round(studentExerciseResultPoints);

                    student.overallPoints += studentExerciseResultPoints;
                    student.pointsPerExercise.set(exercise.id, studentExerciseResultPoints);
                    student.pointsPerExerciseType.set(exercise.type, student.pointsPerExerciseType.get(exercise.type)! + studentExerciseResultPoints);
                    student.numberOfParticipatedExercises += 1;
                    exercise.numberOfParticipationsWithRatedResult += 1;
                    if (result.score >= 100) {
                        student.numberOfSuccessfulExercises += 1;
                        exercise.numberOfSuccessfulParticipations += 1;
                    }

                    student.pointsStringPerExerciseType.set(exercise.type, student.pointsStringPerExerciseType.get(exercise.type)! + roundedPoints + ',');
                } else {
                    // there is no result, the student has not participated or submitted too late
                    student.pointsPerExercise.set(exercise.id, 0);
                    student.pointsStringPerExerciseType.set(exercise.type, student.pointsStringPerExerciseType.get(exercise.type) + '-,');
                }
            }
            for (const exerciseType of this.exerciseTypes) {
                if (this.maxNumberOfPointsPerExerciseType.get(exerciseType)! > 0) {
                    student.scoresPerExerciseType.set(
                        exerciseType,
                        (student.pointsPerExerciseType.get(exerciseType)! / this.maxNumberOfPointsPerExerciseType.get(exerciseType)!) * 100,
                    );
                }
            }
        });

        for (const exerciseType of this.exerciseTypes) {
            // TODO: can we calculate this average only with students who participated in the exercise?
            this.averageNumberOfPointsPerExerciseTypes.set(
                exerciseType,
                this.students.reduce((total, student) => total + student.pointsPerExerciseType.get(exerciseType)!, 0) / this.students.length,
            );
        }

        this.averageNumberOfOverallPoints = this.students.reduce((total, student) => total + student.overallPoints, 0) / this.students.length;
        this.averageNumberOfSuccessfulExercises = this.students.reduce((total, student) => total + student.numberOfSuccessfulExercises, 0) / this.students.length;
        this.averageNumberOfParticipatedExercises = this.students.reduce((total, student) => total + student.numberOfParticipatedExercises, 0) / this.students.length;

        for (const exerciseType of this.exerciseTypes) {
            this.exerciseAveragePointsPerType.set(exerciseType, ''); // initialize with empty string
            this.exerciseParticipationsPerType.set(exerciseType, ''); // initialize with empty string
            this.exerciseSuccessfulPerType.set(exerciseType, ''); // initialize with empty string

            for (const exercise of this.exercisesPerType.get(exerciseType)!) {
                exercise.averagePoints = this.students.reduce((total, student) => total + student.pointsPerExercise.get(exercise.id)!, 0) / this.students.length;
                const roundedPoints = this.round(exercise.averagePoints);
                this.exerciseAveragePointsPerType.set(exerciseType, this.exerciseAveragePointsPerType.get(exerciseType)! + roundedPoints + ',');
                this.exerciseParticipationsPerType.set(exerciseType, this.exerciseParticipationsPerType.get(exerciseType)! + exercise.numberOfParticipationsWithRatedResult + ',');
                this.exerciseSuccessfulPerType.set(exerciseType, this.exerciseSuccessfulPerType.get(exerciseType)! + exercise.numberOfSuccessfulParticipations + ',');
            }
        }

        this.exportReady = true;
    }

    exportResults() {
        // method for exporting the csv with the needed data

        if (this.exportReady && this.students.length > 0) {
            const rows: string[] = [];
            // first row with headers
            let firstRowString = 'data:text/csv;charset=utf-8,Name,Username,Email,Registration Number,';
            for (const exerciseType of this.exerciseTypes) {
                const exerciseTypeName = capitalizeFirstLetter(exerciseType);

                // only add it if there are actually exercises in this type
                if (this.exerciseTitlesPerType.get(exerciseType) && this.exerciseTitlesPerType.get(exerciseType) !== '') {
                    firstRowString += this.exerciseTitlesPerType.get(exerciseType) + ',' + exerciseTypeName + ' Points,' + exerciseTypeName + ' Score,';
                }
            }
            rows.push(firstRowString + 'Overall Points,Overall Score,Presentation Score');

            for (const student of this.students.values()) {
                const name = student.user.name!.trim();
                const login = student.user.login!.trim();
                const registrationNumber = student.user.visibleRegistrationNumber ? student.user.visibleRegistrationNumber!.trim() : '';
                const email = student.user.email!.trim();
                let rowString = name + ',' + login + ',' + email + ',' + registrationNumber + ',';

                for (const exerciseType of this.exerciseTypes) {
                    // only add it if there are actually exercises in this type
                    if (this.exerciseTitlesPerType.get(exerciseType) && this.exerciseTitlesPerType.get(exerciseType) !== '') {
                        const exercisePointsPerType = this.round(student.pointsPerExerciseType.get(exerciseType)!);
                        let exerciseScoresPerType = '';
                        if (this.maxNumberOfPointsPerExerciseType.get(exerciseType)! > 0) {
                            exerciseScoresPerType =
                                this.round((student.pointsPerExerciseType.get(exerciseType)! / this.maxNumberOfPointsPerExerciseType.get(exerciseType)!) * 100) + '%';
                        }
                        rowString += student.pointsStringPerExerciseType.get(exerciseType) + '' + exercisePointsPerType + ',' + exerciseScoresPerType + ',';
                    }
                }

                const overallPoints = this.round(student.overallPoints);
                const overallScore = this.round((student.overallPoints / this.maxNumberOfOverallPoints) * 100) + '%';

                rows.push(rowString + overallPoints + ',' + overallScore + ',' + student.presentationScore);
            }

            // max values
            let rowStringMax = 'Max' + ',,,,';
            for (const exerciseType of this.exerciseTypes) {
                // only add it if there are actually exercises in this type
                if (this.exerciseTitlesPerType.get(exerciseType) && this.exerciseTitlesPerType.get(exerciseType) !== '') {
                    const maxPoints = this.round(this.maxNumberOfPointsPerExerciseType.get(exerciseType)!);
                    rowStringMax += this.exerciseMaxPointsPerType.get(exerciseType) + ',' + maxPoints + ',100%,';
                }
            }
            const maxOverallPoints = this.round(this.maxNumberOfOverallPoints);
            const maxOverallScore = '100%';
            rows.push(rowStringMax + maxOverallPoints + ',' + maxOverallScore + ',');

            // average values
            let rowStringAverage = 'Average' + ',,,,';
            for (const exerciseType of this.exerciseTypes) {
                // only add it if there are actually exercises in this type
                if (this.exerciseTitlesPerType.get(exerciseType) && this.exerciseTitlesPerType.get(exerciseType) !== '') {
                    const averagePoints = this.round(this.averageNumberOfPointsPerExerciseTypes.get(exerciseType)!);
                    const averageScore =
                        this.round((this.averageNumberOfPointsPerExerciseTypes.get(exerciseType)! / this.maxNumberOfPointsPerExerciseType.get(exerciseType)!) * 100) + '%';
                    rowStringAverage += this.exerciseAveragePointsPerType.get(exerciseType) + '' + averagePoints + ',' + averageScore + ',';
                }
            }
            const averageOverallPoints = this.round(this.averageNumberOfOverallPoints);
            const averageOverallScore = this.round((this.averageNumberOfOverallPoints / this.maxNumberOfOverallPoints) * 100) + '%';
            rows.push(rowStringAverage + averageOverallPoints + ',' + averageOverallScore + ',');

            // participation
            let rowStringParticipation = 'Number of participations' + ',,,,';
            for (const exerciseType of this.exerciseTypes) {
                // only add it if there are actually exercises in this type
                if (this.exerciseTitlesPerType.get(exerciseType) && this.exerciseTitlesPerType.get(exerciseType) !== '') {
                    rowStringParticipation += this.exerciseParticipationsPerType.get(exerciseType) + ',,';
                }
            }
            rows.push(rowStringParticipation + ',,');

            // successful
            let rowStringSuccuessful = 'Number of successful participations' + ',,,,';
            for (const exerciseType of this.exerciseTypes) {
                // only add it if there are actually exercises in this type
                if (this.exerciseTitlesPerType.get(exerciseType) && this.exerciseTitlesPerType.get(exerciseType) !== '') {
                    rowStringSuccuessful += this.exerciseSuccessfulPerType.get(exerciseType) + ',,';
                }
            }
            rows.push(rowStringSuccuessful + ',,');

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
    participations: StudentParticipation[] = [];
    presentationScore = 0;
    numberOfParticipatedExercises = 0;
    numberOfSuccessfulExercises = 0;
    overallPoints = 0;
    pointsPerExercise = new Map<number, number>(); // the index is the exercise id
    pointsPerExerciseType = new Map<ExerciseType, number>(); // the absolute number of points the students received per exercise type
    scoresPerExerciseType = new Map<ExerciseType, number>(); // the relative number of points the students received per exercise type (i.e. divided by the max points per exercise type)
    pointsStringPerExerciseType = new Map<ExerciseType, string>(); // a string containing the points for all exercises of a specific type

    constructor(user: User) {
        this.user = user;
        // initialize with 0 or empty string
        for (const exerciseType of Object.values(ExerciseType)) {
            this.pointsPerExerciseType.set(exerciseType, 0);
            this.scoresPerExerciseType.set(exerciseType, 0);
            this.pointsStringPerExerciseType.set(exerciseType, '');
        }
    }
}

function capitalizeFirstLetter(string: String) {
    return string.charAt(0).toUpperCase() + string.slice(1);
}
