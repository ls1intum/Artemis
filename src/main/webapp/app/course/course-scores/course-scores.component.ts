import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { ActivatedRoute } from '@angular/router';
import { User } from 'app/core/user/user.model';
import * as moment from 'moment';
import { sum } from 'lodash';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ExportToCsv } from 'export-to-csv';
import { Exercise, ExerciseType, IncludedInOverallScore } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from '../manage/course-management.service';
import { SortService } from 'app/shared/service/sort.service';
import { LocaleConversionService } from 'app/shared/service/locale-conversion.service';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { ParticipantScoresService, ScoresDTO } from 'app/shared/participant-scores/participant-scores.service';
import { forkJoin } from 'rxjs';
import { round } from 'app/shared/util/utils';
import * as Sentry from '@sentry/browser';

export const PRESENTATION_SCORE_KEY = 'Presentation Score';
export const NAME_KEY = 'Name';
export const USERNAME_KEY = 'Username';
export const EMAIL_KEY = 'Email';
export const REGISTRATION_NUMBER_KEY = 'Registration Number';
export const OVERALL_COURSE_POINTS_KEY = 'Overall Course Points';
export const OVERALL_COURSE_SCORE_KEY = 'Overall Course Score';
export const POINTS_KEY = 'Points';
export const SCORE_KEY = 'Score';

@Component({
    selector: 'jhi-course-scores',
    templateUrl: './course-scores.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourseScoresComponent implements OnInit, OnDestroy {
    // supported exercise type

    readonly exerciseTypes = [ExerciseType.QUIZ, ExerciseType.PROGRAMMING, ExerciseType.MODELING, ExerciseType.TEXT, ExerciseType.FILE_UPLOAD];

    course: Course;
    allParticipationsOfCourse: StudentParticipation[] = [];
    exercisesOfCourseThatAreIncludedInScoreCalculation: Exercise[] = [];
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

    // course score dtos
    studentIdToCourseScoreDTOs: Map<number, ScoresDTO> = new Map<number, ScoresDTO>();

    private languageChangeSubscription?: Subscription;

    constructor(
        private route: ActivatedRoute,
        private courseService: CourseManagementService,
        private sortService: SortService,
        private changeDetector: ChangeDetectorRef,
        private languageHelper: JhiLanguageHelper,
        private localeConversionService: LocaleConversionService,
        private participantScoresService: ParticipantScoresService,
    ) {
        this.reverse = false;
        this.predicate = 'id';
    }

    /**
     * On init fetch the course, all released exercises and all participations with result for the course from the server.
     */
    ngOnInit() {
        this.paramSub = this.route.params.subscribe((params) => {
            this.courseService.findWithExercises(params['courseId']).subscribe((findWithExercisesResult) => {
                this.course = findWithExercisesResult.body!;
                const titleMap = new Map<string, number>();
                if (this.course.exercises) {
                    for (const exercise of this.course.exercises) {
                        const title = exercise.title!;

                        if (titleMap.has(title)) {
                            const currentValue = titleMap.get(title);
                            titleMap.set(title, currentValue! + 1);
                        } else {
                            titleMap.set(title, 1);
                        }
                    }

                    // this workaround is necessary if the course has exercises with the same title (we add the id to make it unique)
                    for (const exercise of this.course.exercises) {
                        if (titleMap.has(exercise.title!) && titleMap.get(exercise.title!)! > 1) {
                            exercise.title = `${exercise.title} (id=${exercise.id})`;
                        }
                    }
                }

                this.exercisesOfCourseThatAreIncludedInScoreCalculation = this.course
                    .exercises!.filter((exercise) => {
                        const isReleasedExercise = !exercise.releaseDate || exercise.releaseDate.isBefore(moment());
                        const isExerciseThatCounts = exercise.includedInOverallScore !== IncludedInOverallScore.NOT_INCLUDED;
                        return isReleasedExercise && isExerciseThatCounts;
                    })
                    .sort((e1: Exercise, e2: Exercise) => {
                        if (e1.dueDate! > e2.dueDate!) {
                            return 1;
                        }
                        if (e1.dueDate! < e2.dueDate!) {
                            return -1;
                        }
                        if (e1.title! > e2.title!) {
                            return 1;
                        }
                        if (e1.title! < e2.title!) {
                            return -1;
                        }
                        return 0;
                    });
                this.calculateCourseStatistics(this.course.id!);
            });
        });

        // Update the view if the language was changed
        this.languageChangeSubscription = this.languageHelper.language.subscribe(() => {
            this.changeDetector.detectChanges();
        });
    }

    /**
     * Fetch all participations with results from the server for the specified course and calculate the corresponding course statistics
     * @param courseId Id of the course
     */
    calculateCourseStatistics(courseId: number) {
        const findParticipationsObservable = this.courseService.findAllParticipationsWithResults(courseId);
        // alternative course scores calculation using participant scores table
        const courseScoresObservable = this.participantScoresService.findCourseScores(courseId);
        forkJoin([findParticipationsObservable, courseScoresObservable]).subscribe(([participationsOfCourse, courseScoresResult]) => {
            this.allParticipationsOfCourse = participationsOfCourse;
            this.calculateExerciseLevelStatistics();
            this.calculateStudentLevelStatistics();

            // comparing with calculation from course scores
            const courseScoreDTOs = courseScoresResult.body!;
            this.compareNewCourseScoresCalculationWithOldCalculation(courseScoreDTOs);
            this.changeDetector.detectChanges();
        });
    }

    /**
     * This method compares the course scores computed on the client side with the ones on the server side
     * using the participations score table. In the future we might switch to the server side method, so we use
     * this method to detect discrepancys.
     * @param courseScoreDTOs the course scores sent from the server (new calculation method)
     */
    private compareNewCourseScoresCalculationWithOldCalculation(courseScoreDTOs: ScoresDTO[]) {
        if (!this.students || !courseScoreDTOs) {
            return;
        }
        for (const courseScoreDTO of courseScoreDTOs) {
            this.studentIdToCourseScoreDTOs.set(courseScoreDTO.studentId!, courseScoreDTO);
        }
        for (const student of this.students) {
            const overAllPoints = round(student.overallPoints, 1);
            const overallScore = round((student.overallPoints / this.maxNumberOfOverallPoints) * 100, 1);

            const regularCalculation = {
                overallScore,
                overAllPoints,
                userId: student.user.id,
                userLogin: student.user.login,
            };
            // checking if the same as in the course scores map
            const courseScoreDTO = this.studentIdToCourseScoreDTOs.get(student.user.id!);
            if (!courseScoreDTO) {
                const errorMessage = `User scores not included in new calculation: ${JSON.stringify(regularCalculation)}`;
                this.logErrorOnSentry(errorMessage);
            } else {
                if (courseScoreDTO.pointsAchieved !== regularCalculation.overAllPoints) {
                    const errorMessage = `Different course points in new calculation. Regular Calculation: ${JSON.stringify(regularCalculation)}. New Calculation: ${JSON.stringify(
                        courseScoreDTO,
                    )}`;
                    this.logErrorOnSentry(errorMessage);
                }
                if (courseScoreDTO.scoreAchieved !== regularCalculation.overallScore) {
                    const errorMessage = `Different course score in new calculation. Regular Calculation: ${JSON.stringify(regularCalculation)}. New Calculation : ${JSON.stringify(
                        courseScoreDTO,
                    )}`;
                    this.logErrorOnSentry(errorMessage);
                }
            }
        }
    }

    private logErrorOnSentry(errorMessage: string) {
        console.log(errorMessage);
        Sentry.captureException(new Error(errorMessage));
    }

    /**
     * Group the exercises by type and gather statistics for each type (titles, max points, accumulated max points).
     */
    calculateExerciseLevelStatistics() {
        for (const exerciseType of this.exerciseTypes) {
            const exercisesOfType = this.exercisesOfCourseThatAreIncludedInScoreCalculation.filter((exercise) => exercise.type === exerciseType);
            this.exercisesPerType.set(exerciseType, exercisesOfType);
            this.exerciseTitlesPerType.set(
                exerciseType,
                exercisesOfType.map((exercise) => exercise.title!),
            );

            const maxPointsOfAllExercisesOfType = exercisesOfType.map((exercise) => exercise.maxPoints!);

            this.exerciseMaxPointsPerType.set(exerciseType, maxPointsOfAllExercisesOfType);

            const maxPointsOfAllIncludedExercisesOfType = exercisesOfType
                // only exercises marked as included_completely increase the maximum reachable number of points
                .filter((exercise) => exercise.includedInOverallScore === IncludedInOverallScore.INCLUDED_COMPLETELY)
                .map((exercise) => exercise.maxPoints!);

            this.maxNumberOfPointsPerExerciseType.set(exerciseType, sum(maxPointsOfAllIncludedExercisesOfType));
        }
        this.maxNumberOfOverallPoints = 0;
        for (const maxNumberOfPointsPerExerciseTypeElement of this.maxNumberOfPointsPerExerciseType) {
            this.maxNumberOfOverallPoints += maxNumberOfPointsPerExerciseTypeElement[1];
        }
    }

    /**
     * Creates students and calculates the points for each exercise and exercise type.
     */
    calculateStudentLevelStatistics() {
        const studentsMap = new Map<number, Student>();

        for (const participation of this.allParticipationsOfCourse) {
            if (participation.results && participation.results.length > 0) {
                for (const result of participation.results) {
                    // reconnect
                    result.participation = participation;
                }
            }

            // find all students by iterating through the participations
            const participationStudents = participation.student ? [participation.student] : participation.team!.students!;
            for (const participationStudent of participationStudents) {
                let student = studentsMap.get(participationStudent.id!);
                if (!student) {
                    student = new Student(participationStudent);
                    studentsMap.set(participationStudent.id!, student);
                }
                student.participations.push(participation);
                if (participation.presentationScore) {
                    student.presentationScore += participation.presentationScore;
                }
            }
        }

        // prepare exercises
        for (const exercise of this.exercisesOfCourseThatAreIncludedInScoreCalculation) {
            exercise.numberOfParticipationsWithRatedResult = 0;
            exercise.numberOfSuccessfulParticipations = 0;
        }

        studentsMap.forEach((student) => {
            this.students.push(student);

            for (const exercise of this.exercisesOfCourseThatAreIncludedInScoreCalculation) {
                const relevantMaxPoints = exercise.maxPoints!;
                const participation = student.participations.find((part) => part.exercise!.id === exercise.id);
                if (participation && participation.results && participation.results.length > 0) {
                    // we found a result, there should only be one
                    const result = participation.results[0];
                    if (participation.results.length > 1) {
                        console.warn('found more than one result for student ' + student.user.login + ' and exercise ' + exercise.title);
                    }

                    const pointsAchievedByStudentInExercise = (result.score! * relevantMaxPoints) / 100;
                    student.overallPoints += pointsAchievedByStudentInExercise;
                    student.pointsPerExercise.set(exercise.id!, pointsAchievedByStudentInExercise);
                    student.sumPointsPerExerciseType.set(exercise.type!, student.sumPointsPerExerciseType.get(exercise.type!)! + pointsAchievedByStudentInExercise);
                    student.numberOfParticipatedExercises += 1;
                    exercise.numberOfParticipationsWithRatedResult! += 1;
                    if (result.score! >= 100) {
                        student.numberOfSuccessfulExercises += 1;
                        exercise.numberOfSuccessfulParticipations! += 1;
                    }

                    student.pointsPerExerciseType.get(exercise.type!)!.push(pointsAchievedByStudentInExercise);
                } else {
                    // there is no result, the student has not participated or submitted too late
                    student.pointsPerExercise.set(exercise.id!, 0);
                    student.pointsPerExerciseType.get(exercise.type!)!.push(Number.NaN);
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
                exercise.averagePoints = this.students.reduce((total, student) => total + student.pointsPerExercise.get(exercise.id!)!, 0) / this.students.length;
                this.exerciseAveragePointsPerType.get(exerciseType)!.push(exercise.averagePoints);
                this.exerciseParticipationsPerType.get(exerciseType)!.push(exercise.numberOfParticipationsWithRatedResult!);
                this.exerciseSuccessfulPerType.get(exerciseType)!.push(exercise.numberOfSuccessfulParticipations!);
            }
        }

        this.exportReady = true;
    }

    /**
     * Method for exporting the csv with the needed data
     */
    exportResults() {
        if (this.exportReady && this.students.length > 0) {
            const rows: any[] = [];
            const keys = [NAME_KEY, USERNAME_KEY, EMAIL_KEY, REGISTRATION_NUMBER_KEY];
            for (const exerciseType of this.exerciseTypes) {
                const exerciseTypeName = capitalizeFirstLetter(exerciseType);

                // only add it if there are actually exercises in this type
                if (this.exerciseTitlesPerType.get(exerciseType) && this.exerciseTitlesPerType.get(exerciseType)!.length > 0) {
                    keys.push(...this.exerciseTitlesPerType.get(exerciseType)!);
                    keys.push(exerciseTypeName + ' ' + POINTS_KEY);
                    keys.push(exerciseTypeName + ' ' + SCORE_KEY);
                }
            }
            keys.push(OVERALL_COURSE_POINTS_KEY, OVERALL_COURSE_SCORE_KEY);
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
                            rowData[title] = this.localeConversionService.toLocaleString(exercisePointValues[index]);
                        });
                        rowData[exerciseTypeName + ' ' + POINTS_KEY] = this.localeConversionService.toLocaleString(exercisePointsPerType);
                        rowData[exerciseTypeName + ' ' + SCORE_KEY] = this.localeConversionService.toLocalePercentageString(exerciseScoresPerType);
                    }
                }

                const overallScore = (student.overallPoints / this.maxNumberOfOverallPoints) * 100;
                rowData[OVERALL_COURSE_POINTS_KEY] = this.localeConversionService.toLocaleString(student.overallPoints);
                rowData[OVERALL_COURSE_SCORE_KEY] = this.localeConversionService.toLocalePercentageString(overallScore);
                if (this.course.presentationScore) {
                    rowData[PRESENTATION_SCORE_KEY] = this.localeConversionService.toLocaleString(student.presentationScore);
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
                        rowDataMax[title] = this.localeConversionService.toLocaleString(exerciseMaxPoints[index]);
                    });
                    rowDataMax[exerciseTypeName + ' ' + POINTS_KEY] = this.localeConversionService.toLocaleString(this.maxNumberOfPointsPerExerciseType.get(exerciseType)!);
                    rowDataMax[exerciseTypeName + ' ' + SCORE_KEY] = this.localeConversionService.toLocalePercentageString(100);
                }
            }
            rowDataMax[OVERALL_COURSE_POINTS_KEY] = this.localeConversionService.toLocaleString(this.maxNumberOfOverallPoints);
            rowDataMax[OVERALL_COURSE_SCORE_KEY] = this.localeConversionService.toLocalePercentageString(100);
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
                        rowDataAverage[title] = this.localeConversionService.toLocaleString(exerciseAveragePoints[index]);
                    });

                    const averageScore = (this.averageNumberOfPointsPerExerciseTypes.get(exerciseType)! / this.maxNumberOfPointsPerExerciseType.get(exerciseType)!) * 100;

                    rowDataAverage[exerciseTypeName + ' ' + POINTS_KEY] = this.localeConversionService.toLocaleString(
                        this.averageNumberOfPointsPerExerciseTypes.get(exerciseType)!,
                    );
                    rowDataAverage[exerciseTypeName + ' ' + SCORE_KEY] = this.localeConversionService.toLocalePercentageString(averageScore);
                }
            }

            const averageOverallScore = (this.averageNumberOfOverallPoints / this.maxNumberOfOverallPoints) * 100;
            rowDataAverage[OVERALL_COURSE_POINTS_KEY] = this.localeConversionService.toLocaleString(this.averageNumberOfOverallPoints);
            rowDataAverage[OVERALL_COURSE_SCORE_KEY] = this.localeConversionService.toLocalePercentageString(averageOverallScore);
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
                        rowDataParticipation[title] = this.localeConversionService.toLocaleString(exerciseParticipations[index]);
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
                        rowDataParticipationSuccuessful[title] = this.localeConversionService.toLocaleString(exerciseParticipationsSuccessful[index]);
                    });
                    rowDataParticipationSuccuessful[exerciseTypeName + ' ' + POINTS_KEY] = '';
                    rowDataParticipationSuccuessful[exerciseTypeName + ' ' + SCORE_KEY] = '';
                }
            }
            rows.push(rowDataParticipationSuccuessful);
            this.exportAsCsv(rows, keys);
        }
    }

    exportAsCsv(rows: any[], keys: string[]) {
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

    /**
     *an empty line in csv-format with an empty row for each exercise type.
     * @param firstValue The first value/name key of the line
     */
    private emptyLine(firstValue: string) {
        const emptyLine = {};
        emptyLine[NAME_KEY] = firstValue;
        emptyLine[USERNAME_KEY] = '';
        emptyLine[EMAIL_KEY] = '';
        emptyLine[REGISTRATION_NUMBER_KEY] = '';
        emptyLine[OVERALL_COURSE_POINTS_KEY] = '';
        emptyLine[OVERALL_COURSE_SCORE_KEY] = '';

        for (const exerciseType of this.exerciseTypes) {
            // only add it if there are actually exercises in this type
            if (this.exerciseTitlesPerType.get(exerciseType) && this.exerciseTitlesPerType.get(exerciseType)!.length !== 0) {
                const exerciseTypeName = capitalizeFirstLetter(exerciseType);
                const exerciseTitleKeys = this.exerciseTitlesPerType.get(exerciseType)!;
                exerciseTitleKeys.forEach((title) => {
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

    sortRows() {
        this.sortService.sortByProperty(this.students, this.predicate, this.reverse);
    }

    getLocaleConversionService() {
        return this.localeConversionService;
    }

    /**
     * On destroy unsubscribe.
     */
    ngOnDestroy() {
        this.paramSub.unsubscribe();
        if (this.languageChangeSubscription) {
            this.languageChangeSubscription.unsubscribe();
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
    scorePerExerciseType = new Map<ExerciseType, number>(); // the relative number of points the students received per exercise type (divided by the max points per exercise type)
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

/**
 * Capitalize the first letter of a string.
 * @param string
 */
function capitalizeFirstLetter(string: String) {
    return string.charAt(0).toUpperCase() + string.slice(1);
}
