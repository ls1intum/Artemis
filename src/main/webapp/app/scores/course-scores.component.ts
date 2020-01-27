import { JhiAlertService, JhiLanguageService } from 'ng-jhipster';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { ActivatedRoute } from '@angular/router';
import { Course } from '../entities/course';
import { CourseService } from 'app/entities/course/course.service';
import { Exercise, ExerciseType } from '../entities/exercise';
import { User } from 'app/core/user/user.model';
import * as moment from 'moment';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ExportToCsv } from 'export-to-csv';
import { JhiLanguageHelper } from 'app/core/language/language.helper';

const PRESENTATION_SCORE_KEY = 'Presentation Score';
const NAME_KEY = 'Name';
const USERNAME_KEY = 'Username';
const EMAIL_KEY = 'Email';
const REGISTRATION_NUMBER_KEY = 'Registration Number';
const TOTAL_COURSE_POINTS_KEY = 'Total Course Points';
const TOTAL_COURSE_SCORE_KEY = 'Total Course Score';
const POINTS_KEY = 'Points';
const SCORE_KEY = 'Score';

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

    exerciseSuccessfulPerType = new Map<ExerciseType, number[]>();
    exerciseParticipationsPerType = new Map<ExerciseType, number[]>();
    exerciseAveragePointsPerType = new Map<ExerciseType, number[]>();
    exerciseMaxPointsPerType = new Map<ExerciseType, number[]>();
    exerciseTitlesPerType = new Map<ExerciseType, string[]>();
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

    options: Intl.NumberFormatOptions = { maximumFractionDigits: 1 }; // TODO: allow user to customize
    locale = getLang(); // default value, will be overridden by the current language of Artemis

    constructor(private route: ActivatedRoute, private courseService: CourseService, private languageHelper: JhiLanguageHelper, private languageService: JhiLanguageService) {
        this.reverse = false;
        this.predicate = 'id';
        this.locale = this.languageService.currentLang;
        this.languageHelper.language.subscribe((languageKey: string) => {
            this.locale = languageKey;
        });
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
            this.exerciseTitlesPerType.set(
                exerciseType,
                exercisesPerType.map(exercise => exercise.title),
            );
            this.exerciseMaxPointsPerType.set(
                exerciseType,
                exercisesPerType.map(exercise => exercise.maxScore),
            );
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
                    student.overallPoints += studentExerciseResultPoints;
                    student.pointsPerExercise.set(exercise.id, studentExerciseResultPoints);
                    student.sumPointsPerExerciseType.set(exercise.type, student.sumPointsPerExerciseType.get(exercise.type)! + studentExerciseResultPoints);
                    student.numberOfParticipatedExercises += 1;
                    exercise.numberOfParticipationsWithRatedResult += 1;
                    if (result.score >= 100) {
                        student.numberOfSuccessfulExercises += 1;
                        exercise.numberOfSuccessfulParticipations += 1;
                    }

                    student.pointsPerExerciseType.get(exercise.type)!.push(studentExerciseResultPoints);
                } else {
                    // there is no result, the student has not participated or submitted too late
                    student.pointsPerExercise.set(exercise.id, 0);
                    student.pointsPerExerciseType.get(exercise.type)!.push(Number.NaN);
                }
            }
            for (const exerciseType of this.exerciseTypes) {
                if (this.maxNumberOfPointsPerExerciseType.get(exerciseType)! > 0) {
                    student.scorePerExerciseType.set(
                        exerciseType,
                        (student.sumPointsPerExerciseType.get(exerciseType)! / this.maxNumberOfPointsPerExerciseType.get(exerciseType)!) * 100,
                    );
                }
            }
        });

        for (const exerciseType of this.exerciseTypes) {
            // TODO: can we calculate this average only with students who participated in the exercise?
            this.averageNumberOfPointsPerExerciseTypes.set(
                exerciseType,
                this.students.reduce((total, student) => total + student.sumPointsPerExerciseType.get(exerciseType)!, 0) / this.students.length,
            );
        }

        this.averageNumberOfOverallPoints = this.students.reduce((total, student) => total + student.overallPoints, 0) / this.students.length;
        this.averageNumberOfSuccessfulExercises = this.students.reduce((total, student) => total + student.numberOfSuccessfulExercises, 0) / this.students.length;
        this.averageNumberOfParticipatedExercises = this.students.reduce((total, student) => total + student.numberOfParticipatedExercises, 0) / this.students.length;

        for (const exerciseType of this.exerciseTypes) {
            this.exerciseAveragePointsPerType.set(exerciseType, []); // initialize with empty array
            this.exerciseParticipationsPerType.set(exerciseType, []); // initialize with empty array
            this.exerciseSuccessfulPerType.set(exerciseType, []); // initialize with empty array

            for (const exercise of this.exercisesPerType.get(exerciseType)!) {
                exercise.averagePoints = this.students.reduce((total, student) => total + student.pointsPerExercise.get(exercise.id)!, 0) / this.students.length;
                this.exerciseAveragePointsPerType.get(exerciseType)!.push(exercise.averagePoints);
                this.exerciseParticipationsPerType.get(exerciseType)!.push(exercise.numberOfParticipationsWithRatedResult);
                this.exerciseSuccessfulPerType.get(exerciseType)!.push(exercise.numberOfSuccessfulParticipations);
            }
        }

        this.exportReady = true;
    }

    exportResults() {
        // method for exporting the csv with the needed data

        if (this.exportReady && this.students.length > 0) {
            const rows = [];
            const keys = [NAME_KEY, USERNAME_KEY, EMAIL_KEY, REGISTRATION_NUMBER_KEY];
            for (const exerciseType of this.exerciseTypes) {
                const exerciseTypeName = capitalizeFirstLetter(exerciseType);

                // only add it if there are actually exercises in this type
                if (this.exerciseTitlesPerType.get(exerciseType) && this.exerciseTitlesPerType.get(exerciseType)!.length !== 0) {
                    keys.push(...this.exerciseTitlesPerType.get(exerciseType)!);
                    keys.push(exerciseTypeName + ' ' + POINTS_KEY);
                    keys.push(exerciseTypeName + ' ' + SCORE_KEY);
                }
            }
            keys.push(TOTAL_COURSE_POINTS_KEY, TOTAL_COURSE_SCORE_KEY);
            if (this.course.presentationScore) {
                keys.push(PRESENTATION_SCORE_KEY);
            }

            for (const student of this.students.values()) {
                const rowData = {};
                rowData[NAME_KEY] = student.user.name!.trim();
                rowData[USERNAME_KEY] = student.user.login!.trim();
                rowData[EMAIL_KEY] = student.user.email!.trim();
                rowData[REGISTRATION_NUMBER_KEY] = student.user.visibleRegistrationNumber ? student.user.visibleRegistrationNumber!.trim() : '';

                for (const exerciseType of this.exerciseTypes) {
                    // only add it if there are actually exercises in this type
                    if (this.exerciseTitlesPerType.get(exerciseType) && this.exerciseTitlesPerType.get(exerciseType)!.length !== 0) {
                        const exerciseTypeName = capitalizeFirstLetter(exerciseType);
                        const exercisePointsPerType = student.sumPointsPerExerciseType.get(exerciseType)!;
                        let exerciseScoresPerType = 0;
                        if (this.maxNumberOfPointsPerExerciseType.get(exerciseType)! > 0) {
                            exerciseScoresPerType = (student.sumPointsPerExerciseType.get(exerciseType)! / this.maxNumberOfPointsPerExerciseType.get(exerciseType)!) * 100;
                        }
                        const exerciseTitleKeys = this.exerciseTitlesPerType.get(exerciseType)!;
                        const exercisePointValues = student.pointsPerExerciseType.get(exerciseType)!;
                        exerciseTitleKeys.forEach((title, index) => {
                            rowData[title] = this.toLocaleString(exercisePointValues[index]);
                        });
                        rowData[exerciseTypeName + ' ' + POINTS_KEY] = this.toLocaleString(exercisePointsPerType);
                        rowData[exerciseTypeName + ' ' + SCORE_KEY] = this.toLocalePercentageString(exerciseScoresPerType);
                    }
                }

                const overallScore = (student.overallPoints / this.maxNumberOfOverallPoints) * 100;
                rowData[TOTAL_COURSE_POINTS_KEY] = this.toLocaleString(student.overallPoints);
                rowData[TOTAL_COURSE_SCORE_KEY] = this.toLocalePercentageString(overallScore);
                if (this.course.presentationScore) {
                    rowData[PRESENTATION_SCORE_KEY] = this.toLocaleString(student.presentationScore);
                }
                rows.push(rowData);
            }

            rows.push(this.emptyLine('')); // empty row as separator

            // max values
            const rowDataMax = this.emptyLine('Max');
            for (const exerciseType of this.exerciseTypes) {
                const exerciseTypeName = capitalizeFirstLetter(exerciseType);
                // only add it if there are actually exercises in this type
                if (this.exerciseTitlesPerType.get(exerciseType) && this.exerciseTitlesPerType.get(exerciseType)!.length !== 0) {
                    const exerciseTitleKeys = this.exerciseTitlesPerType.get(exerciseType)!;
                    const exerciseMaxPoints = this.exerciseMaxPointsPerType.get(exerciseType)!;
                    exerciseTitleKeys.forEach((title, index) => {
                        rowDataMax[title] = this.toLocaleString(exerciseMaxPoints[index]);
                    });
                    rowDataMax[exerciseTypeName + ' ' + POINTS_KEY] = this.toLocaleString(this.maxNumberOfPointsPerExerciseType.get(exerciseType)!);
                    rowDataMax[exerciseTypeName + ' ' + SCORE_KEY] = this.toLocalePercentageString(100);
                }
            }
            rowDataMax[TOTAL_COURSE_POINTS_KEY] = this.toLocaleString(this.maxNumberOfOverallPoints);
            rowDataMax[TOTAL_COURSE_SCORE_KEY] = this.toLocalePercentageString(100);
            if (this.course.presentationScore) {
                rowDataMax[PRESENTATION_SCORE_KEY] = '';
            }
            rows.push(rowDataMax);

            // average values
            const rowDataAverage = this.emptyLine('Average');
            for (const exerciseType of this.exerciseTypes) {
                const exerciseTypeName = capitalizeFirstLetter(exerciseType);
                // only add it if there are actually exercises in this type
                if (this.exerciseTitlesPerType.get(exerciseType) && this.exerciseTitlesPerType.get(exerciseType)!.length !== 0) {
                    const exerciseTitleKeys = this.exerciseTitlesPerType.get(exerciseType)!;
                    const exerciseAveragePoints = this.exerciseAveragePointsPerType.get(exerciseType)!;
                    exerciseTitleKeys.forEach((title, index) => {
                        rowDataAverage[title] = this.toLocaleString(exerciseAveragePoints[index]);
                    });

                    const averageScore = (this.averageNumberOfPointsPerExerciseTypes.get(exerciseType)! / this.maxNumberOfPointsPerExerciseType.get(exerciseType)!) * 100;

                    rowDataAverage[exerciseTypeName + ' ' + POINTS_KEY] = this.toLocaleString(this.averageNumberOfPointsPerExerciseTypes.get(exerciseType)!);
                    rowDataAverage[exerciseTypeName + ' ' + SCORE_KEY] = this.toLocalePercentageString(averageScore);
                }
            }

            const averageOverallScore = (this.averageNumberOfOverallPoints / this.maxNumberOfOverallPoints) * 100;
            rowDataAverage[TOTAL_COURSE_POINTS_KEY] = this.toLocaleString(this.averageNumberOfOverallPoints);
            rowDataAverage[TOTAL_COURSE_SCORE_KEY] = this.toLocalePercentageString(averageOverallScore);
            if (this.course.presentationScore) {
                rowDataAverage[PRESENTATION_SCORE_KEY] = '';
            }
            rows.push(rowDataAverage);

            // participation
            const rowDataParticipation = this.emptyLine('Number of Participations');
            for (const exerciseType of this.exerciseTypes) {
                const exerciseTypeName = capitalizeFirstLetter(exerciseType);
                // only add it if there are actually exercises in this type
                if (this.exerciseTitlesPerType.get(exerciseType) && this.exerciseTitlesPerType.get(exerciseType)!.length !== 0) {
                    const exerciseTitleKeys = this.exerciseTitlesPerType.get(exerciseType)!;
                    const exerciseParticipations = this.exerciseParticipationsPerType.get(exerciseType)!;
                    exerciseTitleKeys.forEach((title, index) => {
                        rowDataParticipation[title] = this.toLocaleString(exerciseParticipations[index]);
                    });
                    rowDataParticipation[exerciseTypeName + ' ' + POINTS_KEY] = '';
                    rowDataParticipation[exerciseTypeName + ' ' + SCORE_KEY] = '';
                }
            }
            rows.push(rowDataParticipation);

            // successful
            const rowDataParticipationSuccuessful = this.emptyLine('Number of Successful Participations');
            for (const exerciseType of this.exerciseTypes) {
                const exerciseTypeName = capitalizeFirstLetter(exerciseType);
                // only add it if there are actually exercises in this type
                if (this.exerciseTitlesPerType.get(exerciseType) && this.exerciseTitlesPerType.get(exerciseType)!.length !== 0) {
                    const exerciseTitleKeys = this.exerciseTitlesPerType.get(exerciseType)!;
                    const exerciseParticipationsSuccessful = this.exerciseSuccessfulPerType.get(exerciseType)!;
                    exerciseTitleKeys.forEach((title, index) => {
                        rowDataParticipationSuccuessful[title] = this.toLocaleString(exerciseParticipationsSuccessful[index]);
                    });
                    rowDataParticipationSuccuessful[exerciseTypeName + ' ' + POINTS_KEY] = '';
                    rowDataParticipationSuccuessful[exerciseTypeName + ' ' + SCORE_KEY] = '';
                }
            }
            rows.push(rowDataParticipationSuccuessful);

            const options = {
                fieldSeparator: ';', // TODO: allow user to customize
                quoteStrings: '"',
                decimalSeparator: 'locale',
                showLabels: true,
                showTitle: false,
                filename: 'Artemis Course ' + this.course.title + ' Scores',
                useTextFile: false,
                useBom: true,
                headers: keys,
            };

            const csvExporter = new ExportToCsv(options);
            csvExporter.generateCsv(rows); // includes download
        }
    }

    private emptyLine(firstValue: string) {
        const emptyLine = {};
        emptyLine[NAME_KEY] = firstValue;
        emptyLine[USERNAME_KEY] = '';
        emptyLine[EMAIL_KEY] = '';
        emptyLine[REGISTRATION_NUMBER_KEY] = '';
        emptyLine[TOTAL_COURSE_POINTS_KEY] = '';
        emptyLine[TOTAL_COURSE_SCORE_KEY] = '';

        for (const exerciseType of this.exerciseTypes) {
            // only add it if there are actually exercises in this type
            if (this.exerciseTitlesPerType.get(exerciseType) && this.exerciseTitlesPerType.get(exerciseType)!.length !== 0) {
                const exerciseTypeName = capitalizeFirstLetter(exerciseType);
                const exerciseTitleKeys = this.exerciseTitlesPerType.get(exerciseType)!;
                exerciseTitleKeys.forEach((title, index) => {
                    emptyLine[title] = '';
                });
                emptyLine[exerciseTypeName + ' ' + POINTS_KEY] = '';
                emptyLine[exerciseTypeName + ' ' + SCORE_KEY] = '';
            }
        }

        if (this.course.presentationScore) {
            emptyLine[PRESENTATION_SCORE_KEY] = '';
        }

        return emptyLine;
    }

    callback() {}

    ngOnDestroy() {
        this.paramSub.unsubscribe();
    }

    toLocaleString(value: number) {
        if (isNaN(value)) {
            return '-';
        } else {
            return value.toLocaleString(this.locale, this.options);
        }
    }

    toLocalePercentageString(value: number) {
        if (isNaN(value)) {
            return '-';
        } else {
            return value.toLocaleString(this.locale, this.options) + '%';
        }
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
    sumPointsPerExerciseType = new Map<ExerciseType, number>(); // the absolute number (sum) of points the students received per exercise type
    scorePerExerciseType = new Map<ExerciseType, number>(); // the relative number of points the students received per exercise type (i.e. divided by the max points per exercise type)
    pointsPerExerciseType = new Map<ExerciseType, number[]>(); // a string containing the points for all exercises of a specific type

    constructor(user: User) {
        this.user = user;
        // initialize with 0 or empty string
        for (const exerciseType of Object.values(ExerciseType)) {
            this.sumPointsPerExerciseType.set(exerciseType, 0);
            this.scorePerExerciseType.set(exerciseType, 0);
            this.pointsPerExerciseType.set(exerciseType, []);
        }
    }
}

function capitalizeFirstLetter(string: String) {
    return string.charAt(0).toUpperCase() + string.slice(1);
}

function getLang() {
    if (navigator.languages !== undefined) {
        return navigator.languages[0];
    } else {
        return navigator.language;
    }
}
