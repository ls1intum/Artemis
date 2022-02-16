import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { forkJoin, of, Subscription } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import dayjs from 'dayjs/esm';
import { sum } from 'lodash-es';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ExportToCsv } from 'export-to-csv';
import { Exercise, ExerciseType, IncludedInOverallScore } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from '../manage/course-management.service';
import { SortService } from 'app/shared/service/sort.service';
import { LocaleConversionService } from 'app/shared/service/locale-conversion.service';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { ParticipantScoresService, ScoresDTO } from 'app/shared/participant-scores/participant-scores.service';
import { round, roundScorePercentSpecifiedByCourseSettings, roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { captureException } from '@sentry/browser';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { GradeType, GradingScale } from 'app/entities/grading-scale.model';
import { catchError } from 'rxjs/operators';
import { HttpResponse } from '@angular/common/http';
import { faDownload, faSort, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { CourseScoresCsvRow, CourseScoresCsvRowBuilder } from 'app/course/course-scores/course-scores-csv-row-builder';
import { CourseScoresStudentStatistics } from 'app/course/course-scores/course-scores-student-statistics';
import { mean, median, standardDeviation } from 'simple-statistics';

export const PRESENTATION_SCORE_KEY = 'Presentation Score';
export const NAME_KEY = 'Name';
export const USERNAME_KEY = 'Username';
export const EMAIL_KEY = 'Email';
export const REGISTRATION_NUMBER_KEY = 'Registration Number';
export const OVERALL_COURSE_POINTS_KEY = 'Overall Course Points';
export const OVERALL_COURSE_SCORE_KEY = 'Overall Course Score';
export const POINTS_KEY = 'Points';
export const SCORE_KEY = 'Score';
export const GRADE_KEY = 'Grades';
export const BONUS_KEY = 'Bonus Points';

export enum HighlightType {
    AVERAGE = 'average',
    MEDIAN = 'median',
    NONE = 'none',
}

@Component({
    selector: 'jhi-course-scores',
    templateUrl: './course-scores.component.html',
    styleUrls: ['./course-scores.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourseScoresComponent implements OnInit, OnDestroy {
    // supported exercise type

    readonly exerciseTypes = [ExerciseType.QUIZ, ExerciseType.PROGRAMMING, ExerciseType.MODELING, ExerciseType.TEXT, ExerciseType.FILE_UPLOAD];
    private exerciseTypesWithExercises: ExerciseType[];

    // Expose the functions to the template
    readonly roundScorePercentSpecifiedByCourseSettings = roundScorePercentSpecifiedByCourseSettings;
    readonly roundValueSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;

    course: Course;
    allParticipationsOfCourse: StudentParticipation[] = [];
    exercisesOfCourseThatAreIncludedInScoreCalculation: Exercise[] = [];
    students: CourseScoresStudentStatistics[] = [];

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

    // note: these represent the course scores using the participation score table. We might switch to this new
    // calculation method completely if it is confirmed that it produces correct results
    studentIdToCourseScoreDTOs: Map<number, ScoresDTO> = new Map<number, ScoresDTO>();

    gradingScaleExists = false;
    gradingScale?: GradingScale;
    isBonus?: boolean;
    maxGrade?: string;
    averageGrade?: string;
    scoresToDisplay: number[];
    valueToHighlight: number | undefined;
    highlightedType = HighlightType.NONE;

    numberOfReleasedExercises: number;
    averageScoreIncluded = 0;
    medianScoreIncluded = 0;
    medianPointsIncluded = 0;

    averageScoreTotal = 0;
    averagePointsTotal = 0;
    medianScoreTotal = 0;
    medianPointsTotal = 0;

    standardDeviationPointsIncluded = 0;
    standardDeviationPointsTotal = 0;

    readonly highlightType = HighlightType;

    private languageChangeSubscription?: Subscription;

    // Icons
    faSort = faSort;
    faDownload = faDownload;
    faSpinner = faSpinner;

    constructor(
        private route: ActivatedRoute,
        private courseService: CourseManagementService,
        private sortService: SortService,
        private changeDetector: ChangeDetectorRef,
        private languageHelper: JhiLanguageHelper,
        private localeConversionService: LocaleConversionService,
        private participantScoresService: ParticipantScoresService,
        private gradingSystemService: GradingSystemService,
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
                this.initializeWithCourse(findWithExercisesResult.body!);
            });
        });

        // Update the view if the language was changed
        this.languageChangeSubscription = this.languageHelper.language.subscribe(() => {
            this.changeDetector.detectChanges();
        });
    }

    /**
     * On destroy unsubscribe.
     */
    ngOnDestroy() {
        if (this.paramSub) {
            this.paramSub.unsubscribe();
        }
        if (this.languageChangeSubscription) {
            this.languageChangeSubscription.unsubscribe();
        }
    }

    sortRows() {
        this.sortService.sortByProperty(this.students, this.predicate, this.reverse);
    }

    /**
     * Initialize the component with the given course.
     * @param course The course which should be displayed.
     * @private
     */
    private initializeWithCourse(course: Course) {
        this.course = course;
        this.initializeExerciseTitles();
        this.exercisesOfCourseThatAreIncludedInScoreCalculation = this.determineExercisesIncludedInScore(this.course);
        this.numberOfReleasedExercises = this.determineReleasedExercises(this.course).length;
        this.calculateCourseStatistics(this.course.id!);
    }

    /**
     * Makes sure the exercise titles are unique.
     * @private
     */
    private initializeExerciseTitles() {
        if (!this.course.exercises) {
            return;
        }

        const titleMap = new Map<string, number>();
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

    /**
     * Determines the exercises of the course that are included in the score calculation.
     * @private
     */
    private determineExercisesIncludedInScore(course: Course): Array<Exercise> {
        return course
            .exercises!.filter((exercise) => {
                const isReleasedExercise = !exercise.releaseDate || exercise.releaseDate.isBefore(dayjs());
                const isExerciseThatCounts = exercise.includedInOverallScore !== IncludedInOverallScore.NOT_INCLUDED;
                return isReleasedExercise && isExerciseThatCounts;
            })
            .sort(CourseScoresComponent.compareExercises);
    }

    /**
     * Returns all exercise types for which the course has at least one exercise.
     * @private
     */
    private filterExercisesTypesWithExercises(): Array<ExerciseType> {
        return this.exerciseTypes.filter((exerciseType) => {
            const exercisesWithType = this.exerciseTitlesPerType.get(exerciseType)?.length ?? 0;
            return exercisesWithType !== 0;
        });
    }

    /**
     * Fetch all participations with results from the server for the specified course and calculate the corresponding course statistics
     * @param courseId Id of the course
     */
    private calculateCourseStatistics(courseId: number) {
        const findParticipationsObservable = this.courseService.findAllParticipationsWithResults(courseId);
        // alternative course scores calculation using participant scores table
        const courseScoresObservable = this.participantScoresService.findCourseScores(courseId);
        // find grading scale if it exists for course
        const gradingScaleObservable = this.gradingSystemService.findGradingScaleForCourse(courseId).pipe(catchError(() => of(new HttpResponse<GradingScale>())));
        forkJoin([findParticipationsObservable, courseScoresObservable, gradingScaleObservable]).subscribe(([participationsOfCourse, courseScoresResult, gradingScaleResponse]) => {
            this.allParticipationsOfCourse = participationsOfCourse;

            this.calculateExerciseLevelStatistics();
            this.exerciseTypesWithExercises = this.filterExercisesTypesWithExercises();

            this.calculateStudentLevelStatistics();

            // if grading scale exists set properties
            if (gradingScaleResponse.body) {
                this.calculateGradingScaleInformation(gradingScaleResponse.body);
            }

            // comparing with calculation from course scores (using new participation score table)
            const courseScoreDTOs = courseScoresResult.body!;
            this.compareNewCourseScoresCalculationWithOldCalculation(courseScoreDTOs);
            this.calculateAverageAndMedianScores();
            this.scoresToDisplay = this.students.map((student) => roundScorePercentSpecifiedByCourseSettings(student.overallPoints / this.maxNumberOfOverallPoints, this.course));
            this.highlightBar(HighlightType.AVERAGE);
        });
    }

    /**
     * This method compares the course scores computed on the client side with the ones on the server side
     * using the participations score table. In the future we might switch to the server side method, so we use
     * this method to detect discrepancies.
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
            this.checkStudentScoreCalculation(student);
        }
    }

    /**
     * Checks that the score calculated on the server for the student matches the score calculated in the client.
     * @param student The student for which the score should be checked.
     * @private
     */
    private checkStudentScoreCalculation(student: CourseScoresStudentStatistics) {
        const overAllPoints = roundValueSpecifiedByCourseSettings(student.overallPoints, this.course);
        const overallScore = roundScorePercentSpecifiedByCourseSettings(student.overallPoints / this.maxNumberOfOverallPoints, this.course);
        const regularCalculation = {
            scoreAchieved: overallScore,
            pointsAchieved: overAllPoints,
            userId: student.user.id,
            userLogin: student.user.login,
            regularPointsAchievable: this.maxNumberOfOverallPoints,
        };

        // checking if the same as in the course scores map
        const courseScoreDTO = this.studentIdToCourseScoreDTOs.get(student.user.id!);
        if (!courseScoreDTO) {
            const errorMessage = `User scores not included in new calculation: ${JSON.stringify(regularCalculation)}`;
            this.logErrorOnSentry(errorMessage);
        } else {
            courseScoreDTO.scoreAchieved = roundValueSpecifiedByCourseSettings(courseScoreDTO.scoreAchieved, this.course);
            courseScoreDTO.pointsAchieved = roundValueSpecifiedByCourseSettings(courseScoreDTO.pointsAchieved, this.course);

            if (Math.abs(courseScoreDTO.pointsAchieved - regularCalculation.pointsAchieved) > 0.1) {
                const errorMessage = `Different course points in new calculation. Regular Calculation: ${JSON.stringify(regularCalculation)}. New Calculation: ${JSON.stringify(
                    courseScoreDTO,
                )}`;
                this.logErrorOnSentry(errorMessage);
            }
            if (Math.abs(courseScoreDTO.scoreAchieved - regularCalculation.scoreAchieved) > 0.1) {
                const errorMessage = `Different course score in new calculation. Regular Calculation: ${JSON.stringify(regularCalculation)}. New Calculation : ${JSON.stringify(
                    courseScoreDTO,
                )}`;
                this.logErrorOnSentry(errorMessage);
            }
        }
    }

    logErrorOnSentry(errorMessage: string) {
        captureException(new Error(errorMessage));
    }

    /**
     * Group the exercises by type and gather statistics for each type (titles, max points, accumulated max points).
     */
    private calculateExerciseLevelStatistics() {
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
    private calculateStudentLevelStatistics() {
        const studentsMap = this.mapStudentIdToStudentStatistics();

        // prepare exercises
        for (const exercise of this.exercisesOfCourseThatAreIncludedInScoreCalculation) {
            exercise.numberOfParticipationsWithRatedResult = 0;
            exercise.numberOfSuccessfulParticipations = 0;
        }

        studentsMap.forEach((student) => {
            this.students.push(student);
            // We need the information of not included exercises as well in order to compute the total average and median
            for (const exercise of this.determineReleasedExercises(this.course)) {
                this.updateStudentStatisticsWithExerciseResults(student, exercise);
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
     * Goes through all participations and collects the found students.
     * @return A map of the student`s id to the student.
     * @private
     */
    private mapStudentIdToStudentStatistics(): Map<number, CourseScoresStudentStatistics> {
        const studentsMap = new Map<number, CourseScoresStudentStatistics>();

        for (const participation of this.allParticipationsOfCourse) {
            participation.results?.forEach((result) => (result.participation = participation));

            // find all students by iterating through the participations
            const participationStudents = participation.student ? [participation.student] : participation.team!.students!;
            for (const participationStudent of participationStudents) {
                let student = studentsMap.get(participationStudent.id!);
                if (!student) {
                    student = new CourseScoresStudentStatistics(participationStudent);
                    studentsMap.set(participationStudent.id!, student);
                }
                student.participations.push(participation);
                if (participation.presentationScore) {
                    student.presentationScore += participation.presentationScore;
                }
            }
        }
        return studentsMap;
    }

    /**
     * Updates the student statistics with their result in the given exercise.
     * @param student The student that should be updated.
     * @param exercise The exercise that should be included in the statistics.
     * @private
     */
    private updateStudentStatisticsWithExerciseResults(student: CourseScoresStudentStatistics, exercise: Exercise) {
        const relevantMaxPoints = exercise.maxPoints!;
        const participation = student.participations.find((part) => part.exercise!.id === exercise.id);
        if (participation && participation.results && participation.results.length > 0) {
            // we found a result, there should only be one
            const result = participation.results[0];
            if (participation.results.length > 1) {
                console.warn('found more than one result for student ' + student.user.login + ' and exercise ' + exercise.title);
            }

            // Note: It is important that we round on the individual exercise level first and then sum up.
            // This is necessary so that the student arrives at the same overall result when doing his own recalculation.
            // Let's assume that the student achieved 1.05 points in each of 5 exercises.
            // In the client, these are now displayed rounded as 1.1 points.
            // If the student adds up the displayed points, he gets a total of 5.5 points.
            // In order to get the same total result as the student, we have to round before summing.
            const pointsAchievedByStudentInExercise = roundValueSpecifiedByCourseSettings((result.score! * relevantMaxPoints) / 100, this.course);
            student.pointsPerExercise.set(exercise.id!, pointsAchievedByStudentInExercise);
            const includedIDs = this.exercisesOfCourseThatAreIncludedInScoreCalculation.map((includedExercise) => includedExercise.id);
            // We only include this exercise if it is included in the exercise score
            if (includedIDs.includes(exercise.id)) {
                student.overallPoints += pointsAchievedByStudentInExercise;
                student.sumPointsPerExerciseType.set(exercise.type!, student.sumPointsPerExerciseType.get(exercise.type!)! + pointsAchievedByStudentInExercise);
                student.numberOfParticipatedExercises += 1;
                exercise.numberOfParticipationsWithRatedResult! += 1;
                if (result.score! >= 100) {
                    student.numberOfSuccessfulExercises += 1;
                    exercise.numberOfSuccessfulParticipations! += 1;
                }

                student.pointsPerExerciseType.get(exercise.type!)!.push(pointsAchievedByStudentInExercise);
            }
        } else {
            // there is no result, the student has not participated or submitted too late
            student.pointsPerExercise.set(exercise.id!, 0);
            student.pointsPerExerciseType.get(exercise.type!)!.push(Number.NaN);
        }
    }

    /**
     * Sets grading scale related properties
     * @param gradingScale the grading scale for the course
     */
    calculateGradingScaleInformation(gradingScale: GradingScale) {
        this.gradingScaleExists = true;
        this.gradingScale = gradingScale;
        this.gradingScale.gradeSteps = this.gradingSystemService.sortGradeSteps(this.gradingScale.gradeSteps);
        this.isBonus = this.gradingScale.gradeType === GradeType.BONUS;
        this.maxGrade = this.gradingSystemService.maxGrade(this.gradingScale.gradeSteps);

        if (this.maxNumberOfOverallPoints >= 0) {
            const overallPercentage = this.maxNumberOfOverallPoints > 0 ? (this.averageNumberOfOverallPoints / this.maxNumberOfOverallPoints) * 100 : 0;
            this.averageGrade = this.gradingSystemService.findMatchingGradeStep(this.gradingScale.gradeSteps, overallPercentage)!.gradeName;
            for (const student of this.students) {
                const overallPercentageForStudent =
                    student.overallPoints > 0 && this.maxNumberOfOverallPoints > 0 ? (student.overallPoints / this.maxNumberOfOverallPoints) * 100 : 0;
                student.gradeStep = this.gradingSystemService.findMatchingGradeStep(this.gradingScale.gradeSteps, overallPercentageForStudent);
            }
        }

        this.changeDetector.detectChanges();
    }

    /**
     * Localizes a number, e.g. switching the decimal separator
     */
    localize(numberToLocalize: number): string {
        return this.localeConversionService.toLocaleString(numberToLocalize, this.course.accuracyOfScores);
    }

    /**
     * Localizes a percent number, e.g. switching the decimal separator
     */
    localizePercent(numberToLocalize: number): string {
        return this.localeConversionService.toLocalePercentageString(numberToLocalize, this.course.accuracyOfScores);
    }

    /**
     * Method for exporting the csv with the needed data
     */
    exportResults() {
        if (!this.exportReady || this.students.length === 0) {
            return;
        }

        const rows: CourseScoresCsvRow[] = [];
        const keys = this.generateCsvColumnNames();

        this.students.forEach((student) => rows.push(this.generateStudentStatisticsCsvRow(student)));

        // empty row as separator
        rows.push(this.prepareEmptyCsvRow('').build());

        rows.push(this.generateCsvRowMaxValues());
        rows.push(this.generateCsvRowAverageValues());
        rows.push(this.generateCsvRowParticipation());
        rows.push(this.generateCsvRowSuccessfulParticipation());

        this.exportAsCsv(keys, rows);
    }

    /**
     * Builds the CSV from the rows and starts the download.
     * @param keys The column names of the CSV.
     * @param rows The data rows that should be part of the CSV.
     */
    exportAsCsv(keys: string[], rows: CourseScoresCsvRow[]) {
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
     * Constructs a new builder for a new CSV row.
     * @private
     */
    private newCsvRowBuilder(): CourseScoresCsvRowBuilder {
        const localizer = this.localize.bind(this);
        const percentageLocalizer = this.localizePercent.bind(this);
        return new CourseScoresCsvRowBuilder(localizer, percentageLocalizer);
    }

    /**
     * Generates the list of columns that should be part of the exported CSV file.
     * @private
     */
    private generateCsvColumnNames(): Array<string> {
        const keys = [NAME_KEY, USERNAME_KEY, EMAIL_KEY, REGISTRATION_NUMBER_KEY];

        for (const exerciseType of this.exerciseTypesWithExercises) {
            keys.push(...this.exerciseTitlesPerType.get(exerciseType)!);
            keys.push(CourseScoresCsvRowBuilder.getExerciseTypeKey(exerciseType, POINTS_KEY));
            keys.push(CourseScoresCsvRowBuilder.getExerciseTypeKey(exerciseType, SCORE_KEY));
        }

        keys.push(OVERALL_COURSE_POINTS_KEY, OVERALL_COURSE_SCORE_KEY);

        if (this.course.presentationScore) {
            keys.push(PRESENTATION_SCORE_KEY);
        }

        if (this.gradingScaleExists) {
            keys.push(this.isBonus ? BONUS_KEY : GRADE_KEY);
        }

        return keys;
    }

    /**
     * Generates a row for the exported csv with the statistics for the given student.
     * @param student The student for which a row in the CSV should be created.
     * @private
     */
    private generateStudentStatisticsCsvRow(student: CourseScoresStudentStatistics): CourseScoresCsvRow {
        const rowData = this.newCsvRowBuilder();
        rowData.setUserInformation(student);

        for (const exerciseType of this.exerciseTypesWithExercises) {
            const exercisePointsPerType = student.sumPointsPerExerciseType.get(exerciseType)!;

            let exerciseScoresPerType = 0;
            if (this.maxNumberOfPointsPerExerciseType.get(exerciseType)! > 0) {
                exerciseScoresPerType = roundScorePercentSpecifiedByCourseSettings(
                    student.sumPointsPerExerciseType.get(exerciseType)! / this.maxNumberOfPointsPerExerciseType.get(exerciseType)!,
                    this.course,
                );
            }
            const exerciseTitleKeys = this.exerciseTitlesPerType.get(exerciseType)!;
            const exercisePointValues = student.pointsPerExerciseType.get(exerciseType)!;
            exerciseTitleKeys.forEach((title, index) => {
                const points = roundValueSpecifiedByCourseSettings(exercisePointValues[index], this.course);
                rowData.setLocalized(title, points);
            });

            rowData.setExerciseTypePoints(exerciseType, exercisePointsPerType);
            rowData.setExerciseTypeScore(exerciseType, exerciseScoresPerType);
        }

        const overallScore = roundScorePercentSpecifiedByCourseSettings(student.overallPoints / this.maxNumberOfOverallPoints, this.course);
        rowData.setLocalized(OVERALL_COURSE_POINTS_KEY, student.overallPoints);
        rowData.setLocalizedPercent(OVERALL_COURSE_SCORE_KEY, overallScore);

        if (this.course.presentationScore) {
            rowData.setLocalized(PRESENTATION_SCORE_KEY, student.presentationScore);
        }

        this.setCsvRowGradeValue(rowData, student.gradeStep?.gradeName);

        return rowData.build();
    }

    /**
     * Generates a row for the exported csv with the maximum values of the various statistics.
     * @private
     */
    private generateCsvRowMaxValues(): CourseScoresCsvRow {
        const rowData = this.prepareEmptyCsvRow('Max');

        for (const exerciseType of this.exerciseTypesWithExercises) {
            const exerciseTitleKeys = this.exerciseTitlesPerType.get(exerciseType)!;
            const exerciseMaxPoints = this.exerciseMaxPointsPerType.get(exerciseType)!;

            exerciseTitleKeys.forEach((title, index) => {
                rowData.setLocalized(title, exerciseMaxPoints[index]);
            });
            rowData.setExerciseTypePoints(exerciseType, this.maxNumberOfPointsPerExerciseType.get(exerciseType)!);
            rowData.setExerciseTypeScore(exerciseType, 100);
        }

        rowData.setLocalized(OVERALL_COURSE_POINTS_KEY, this.maxNumberOfOverallPoints);
        rowData.setLocalizedPercent(OVERALL_COURSE_SCORE_KEY, 100);

        if (this.course.presentationScore) {
            rowData.set(PRESENTATION_SCORE_KEY, '');
        }

        this.setCsvRowGradeValue(rowData, this.maxGrade);

        return rowData.build();
    }

    /**
     * Generates a row for the exported csv with the average values of the various statistics.
     * @private
     */
    private generateCsvRowAverageValues(): CourseScoresCsvRow {
        const rowData = this.prepareEmptyCsvRow('Average');

        for (const exerciseType of this.exerciseTypesWithExercises) {
            const exerciseTitleKeys = this.exerciseTitlesPerType.get(exerciseType)!;
            const exerciseAveragePoints = this.exerciseAveragePointsPerType.get(exerciseType)!;

            exerciseTitleKeys.forEach((title, index) => {
                const points = roundValueSpecifiedByCourseSettings(exerciseAveragePoints[index], this.course);
                rowData.setLocalized(title, points);
            });

            const averageScore = roundScorePercentSpecifiedByCourseSettings(
                this.averageNumberOfPointsPerExerciseTypes.get(exerciseType)! / this.maxNumberOfPointsPerExerciseType.get(exerciseType)!,
                this.course,
            );

            rowData.setExerciseTypePoints(exerciseType, this.averageNumberOfPointsPerExerciseTypes.get(exerciseType)!);
            rowData.setExerciseTypeScore(exerciseType, averageScore);
        }

        const averageOverallScore = roundScorePercentSpecifiedByCourseSettings(this.averageNumberOfOverallPoints / this.maxNumberOfOverallPoints, this.course);
        rowData.setLocalized(OVERALL_COURSE_POINTS_KEY, this.averageNumberOfOverallPoints);
        rowData.setLocalizedPercent(OVERALL_COURSE_SCORE_KEY, averageOverallScore);

        if (this.course.presentationScore) {
            rowData.set(PRESENTATION_SCORE_KEY, '');
        }

        this.setCsvRowGradeValue(rowData, this.averageGrade);

        return rowData.build();
    }

    /**
     * Generates a row for the exported Csv with information about the number of participants.
     * @private
     */
    private generateCsvRowParticipation(): CourseScoresCsvRow {
        const rowData = this.prepareEmptyCsvRow('Number of Participations');

        for (const exerciseType of this.exerciseTypesWithExercises) {
            const exerciseTitleKeys = this.exerciseTitlesPerType.get(exerciseType)!;
            const exerciseParticipations = this.exerciseParticipationsPerType.get(exerciseType)!;

            exerciseTitleKeys.forEach((title, index) => {
                rowData.setLocalized(title, exerciseParticipations[index]);
            });
            rowData.setExerciseTypePoints(exerciseType, '');
            rowData.setExerciseTypeScore(exerciseType, '');
        }
        this.setCsvRowGradeValue(rowData, '');

        return rowData.build();
    }

    /**
     * Generates a row for the exported Csv with information about the number of successful participants.
     * @private
     */
    private generateCsvRowSuccessfulParticipation(): CourseScoresCsvRow {
        const rowData = this.prepareEmptyCsvRow('Number of Successful Participations');

        for (const exerciseType of this.exerciseTypesWithExercises) {
            const exerciseTitleKeys = this.exerciseTitlesPerType.get(exerciseType)!;
            const exerciseParticipationsSuccessful = this.exerciseSuccessfulPerType.get(exerciseType)!;

            exerciseTitleKeys.forEach((title, index) => {
                rowData.setLocalized(title, exerciseParticipationsSuccessful[index]);
            });
            rowData.setExerciseTypePoints(exerciseType, '');
            rowData.setExerciseTypeScore(exerciseType, '');
        }
        this.setCsvRowGradeValue(rowData, '');

        return rowData.build();
    }

    /**
     * Prepares an empty row (except for the first column) with an empty column for each exercise type.
     * @param firstValue The value that should be placed in the first column of the row.
     */
    private prepareEmptyCsvRow(firstValue: string): CourseScoresCsvRowBuilder {
        const emptyLine = this.newCsvRowBuilder();

        emptyLine.set(NAME_KEY, firstValue);
        emptyLine.set(USERNAME_KEY, '');
        emptyLine.set(EMAIL_KEY, '');
        emptyLine.set(REGISTRATION_NUMBER_KEY, '');

        emptyLine.set(OVERALL_COURSE_POINTS_KEY, '');
        emptyLine.set(OVERALL_COURSE_SCORE_KEY, '');

        for (const exerciseType of this.exerciseTypesWithExercises) {
            const exerciseTitleKeys = this.exerciseTitlesPerType.get(exerciseType)!;
            exerciseTitleKeys.forEach((title) => {
                emptyLine.set(title, '');
            });
            emptyLine.setExerciseTypePoints(exerciseType, '');
            emptyLine.setExerciseTypeScore(exerciseType, '');
        }

        if (this.course.presentationScore) {
            emptyLine.set(PRESENTATION_SCORE_KEY, '');
        }
        this.setCsvRowGradeValue(emptyLine, '');

        return emptyLine;
    }

    /**
     * Puts the given value into the grading scale column of the CSV row.
     * @param csvRow The row in which the value should be stored.
     * @param value The value that should be stored in the row.
     * @private
     */
    private setCsvRowGradeValue(csvRow: CourseScoresCsvRowBuilder, value: string | number | undefined) {
        if (this.gradingScaleExists) {
            if (this.isBonus) {
                csvRow.set(BONUS_KEY, value);
            } else {
                csvRow.set(GRADE_KEY, value);
            }
        }
    }

    /**
     * Compares two exercises to determine which should be first in a sorted list.
     *
     * Compares them by due date first, then title.
     * @param e1 Some exercise.
     * @param e2 Another exercise.
     * @private
     */
    private static compareExercises(e1: Exercise, e2: Exercise): number {
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
    }

    /**
     * Filters the course exercises and returns the exercises that are already released or do not have a release date
     * @param course the course whose exercises are filtered
     * @private
     */
    private determineReleasedExercises(course: Course): Exercise[] {
        return course.exercises!.filter((exercise) => !exercise.releaseDate || exercise.releaseDate.isBefore(dayjs()));
    }

    /**
     * Computes the average of given scores and returns it rounded based on course settings
     * @param scores the scores the average should be computed of
     * @private
     */
    private calculateAverageScore(scores: number[]): number {
        return roundScorePercentSpecifiedByCourseSettings(mean(scores), this.course);
    }

    /**
     * Computes the average of given points and returns it rounded based on course settings
     * @param points the points the average should be computed of
     * @private
     */
    private calculateAveragePoints(points: number[]): number {
        return roundValueSpecifiedByCourseSettings(mean(points), this.course);
    }

    /**
     * Computes the median of given scores and returns it rounded based on course settings
     * @param scores the scores the median should be computed of
     * @private
     */
    private calculateMedianScore(scores: number[]): number {
        return roundScorePercentSpecifiedByCourseSettings(median(scores), this.course);
    }

    /**
     * Computes the median of given points and returns it rounded based on course settings
     * @param points the points the median should be computed of
     * @private
     */
    private calculateMedianPoints(points: number[]): number {
        return roundValueSpecifiedByCourseSettings(median(points), this.course);
    }

    /**
     * Sets the statistical values displayed in the table next to the distribution chart
     * @private
     */
    private calculateAverageAndMedianScores(): void {
        const allCoursePoints = this.course.exercises!.map((exercise) => exercise.maxPoints ?? 0).reduce((points1, points2) => points1 + points2, 0);
        const includedPointsPerStudent = this.students.map((student) => student.overallPoints);
        // average points and score included
        const scores = includedPointsPerStudent.map((point) => point / this.maxNumberOfOverallPoints);
        this.averageScoreIncluded = roundScorePercentSpecifiedByCourseSettings(this.averageNumberOfOverallPoints / this.maxNumberOfOverallPoints, this.course);

        // average points and score total
        const achievedPointsTotal = this.students.map((student) => {
            return Array.from(student.pointsPerExercise.values()).reduce((points1, points2) => points1 + points2, 0);
        });
        const averageScores = achievedPointsTotal.map((totalPoints) => totalPoints / allCoursePoints);

        this.averagePointsTotal = this.calculateAveragePoints(achievedPointsTotal);
        this.averageScoreTotal = this.calculateAverageScore(averageScores);

        // median points and score included
        this.medianPointsIncluded = this.calculateMedianPoints(includedPointsPerStudent);
        this.medianScoreIncluded = this.calculateMedianScore(scores);

        // median points and score total
        this.medianPointsTotal = this.calculateMedianPoints(achievedPointsTotal);
        this.medianScoreTotal = this.calculateMedianScore(averageScores);

        // Since these two values are only statistical details, there is no need to make the rounding dependent of the course settings
        // standard deviation points included
        this.standardDeviationPointsIncluded = round(standardDeviation(includedPointsPerStudent), 2);

        // standard deviation points total
        this.standardDeviationPointsTotal = round(standardDeviation(achievedPointsTotal), 2);
    }

    /**
     * Handles the case if the user selects either the average or the median in the table next to the chart
     * @param type the statistical type that is selected by the user
     */
    highlightBar(type: HighlightType) {
        if (this.highlightedType === type) {
            this.valueToHighlight = undefined;
            this.highlightedType = HighlightType.NONE;
            this.changeDetector.detectChanges();
            return;
        }
        switch (type) {
            case HighlightType.AVERAGE:
                this.valueToHighlight = this.averageScoreIncluded;
                this.highlightedType = HighlightType.AVERAGE;
                break;
            case HighlightType.MEDIAN:
                this.valueToHighlight = this.medianScoreIncluded;
                this.highlightedType = HighlightType.MEDIAN;
                break;
        }
        this.changeDetector.detectChanges();
    }
}
