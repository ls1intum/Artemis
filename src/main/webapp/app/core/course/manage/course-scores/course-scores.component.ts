import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Subscription, forkJoin, of } from 'rxjs';
import { ActivatedRoute, RouterLink } from '@angular/router';
import dayjs from 'dayjs/esm';
import { sum } from 'lodash-es';
import { download, generateCsv, mkConfig } from 'export-to-csv';
import { Exercise, ExerciseType, IncludedInOverallScore, exerciseTypes } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { SortService } from 'app/shared/service/sort.service';
import { LocaleConversionService } from 'app/shared/service/locale-conversion.service';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { average, round, roundScorePercentSpecifiedByCourseSettings, roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { GradingService } from 'app/assessment/manage/grading/grading-service';
import { GradeType, GradingScale } from 'app/assessment/shared/entities/grading-scale.model';
import { catchError } from 'rxjs/operators';
import { HttpResponse } from '@angular/common/http';
import { faClipboard, faDownload, faSort, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { CsvExportRowBuilder } from 'app/shared/export/row-builder/csv-export-row-builder';
import { mean, median, standardDeviation } from 'simple-statistics';
import { CsvExportOptions } from 'app/shared/export/modal/export-modal.component';
import { ButtonSize } from 'app/shared/components/buttons/button/button.component';
import * as XLSX from 'xlsx';
import { MODULE_FEATURE_PLAGIARISM, VERSION } from 'app/app.constants';
import { ExcelExportRowBuilder } from 'app/shared/export/row-builder/excel-export-row-builder';
import { ExportRow, ExportRowBuilder } from 'app/shared/export/row-builder/export-row-builder';
import {
    BONUS_KEY,
    COURSE_OVERALL_POINTS_KEY,
    COURSE_OVERALL_SCORE_KEY,
    EMAIL_KEY,
    GRADE_KEY,
    NAME_KEY,
    POINTS_KEY,
    PRESENTATION_POINTS_KEY,
    PRESENTATION_SCORE_KEY,
    REGISTRATION_NUMBER_KEY,
    SCORE_KEY,
    USERNAME_KEY,
} from 'app/shared/export/export-constants';
import { PlagiarismCasesService } from 'app/plagiarism/shared/services/plagiarism-cases.service';
import { GradeStep } from 'app/assessment/shared/entities/grade-step.model';
import { PlagiarismCaseDTO } from 'app/plagiarism/shared/entities/PlagiarismCase';
import { PlagiarismVerdict } from 'app/plagiarism/shared/entities/PlagiarismVerdict';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ParticipantScoresDistributionComponent } from 'app/shared/participant-scores/participant-scores-distribution/participant-scores-distribution.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { NgClass } from '@angular/common';
import { ExportButtonComponent } from 'app/shared/export/button/export-button.component';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CourseScoresStudentStatistics } from 'app/core/course/manage/course-scores/course-scores-student-statistics';
import { ExerciseTypeStatisticsMap } from 'app/core/course/manage/course-scores/exercise-type-statistics-map';
import { CourseManagementService, GradeScoreDTO, StudentGradeDTO } from 'app/core/course/manage/services/course-management.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { CourseTitleBarActionsDirective } from 'app/core/course/shared/directives/course-title-bar-actions.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';

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
    imports: [
        TranslateDirective,
        RouterLink,
        FaIconComponent,
        ParticipantScoresDistributionComponent,
        NgbTooltip,
        NgClass,
        ExportButtonComponent,
        SortDirective,
        SortByDirective,
        ArtemisTranslatePipe,
        CourseTitleBarActionsDirective,
        HelpIconComponent,
    ],
})
export class CourseScoresComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private courseManagementService = inject(CourseManagementService);
    private sortService = inject(SortService);
    private changeDetector = inject(ChangeDetectorRef);
    private languageHelper = inject(JhiLanguageHelper);
    private localeConversionService = inject(LocaleConversionService);
    private gradingSystemService = inject(GradingService);
    private plagiarismCasesService = inject(PlagiarismCasesService);
    private profileService = inject(ProfileService);

    private paramSub: Subscription;
    private languageChangeSubscription?: Subscription;
    plagiarismEnabled = false;

    course: Course;
    gradeScores: GradeScoreDTO[] = [];
    students: StudentGradeDTO[] = [];
    // exercises that are included in score calculation
    includedExercises: Exercise[] = [];
    studentStatistics: CourseScoresStudentStatistics[] = [];

    private exerciseTypesWithExercises: ExerciseType[];
    private exerciseSuccessfulPerType = new ExerciseTypeStatisticsMap();
    private exerciseParticipationsPerType = new ExerciseTypeStatisticsMap();
    private exerciseAveragePointsPerType = new ExerciseTypeStatisticsMap();
    exerciseMaxPointsPerType = new ExerciseTypeStatisticsMap();
    private exercisesPerType = new Map<ExerciseType, Exercise[]>();

    exportReady = false;
    predicate: string;
    reverse: boolean;

    // max values
    maxNumberOfPointsPerExerciseType = new Map<ExerciseType, number>();
    maxNumberOfOverallPoints = 0;
    maxNumberOfPresentationPoints = 0;

    // average values
    averageNumberOfParticipatedExercises = 0;
    averageNumberOfSuccessfulExercises = 0;
    averageNumberOfPointsPerExerciseTypes = new Map<ExerciseType, number>();
    averageNumberOfOverallPoints = 0;
    averageNumberOfPresentationPoints = 0;

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

    // Expose the imports to the template
    readonly exerciseTypes = exerciseTypes;
    readonly highlightType = HighlightType;
    readonly roundScorePercentSpecifiedByCourseSettings = roundScorePercentSpecifiedByCourseSettings;
    readonly roundValueSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;
    readonly ButtonSize = ButtonSize;

    // Icons
    faSort = faSort;
    faDownload = faDownload;
    faSpinner = faSpinner;
    faClipboard = faClipboard;

    constructor() {
        this.reverse = false;
        this.predicate = 'id';
    }

    /**
     * On init fetch the course, all released exercises and all participations with result for the course from the server.
     */
    ngOnInit() {
        this.paramSub = this.route.params.subscribe((params) => {
            this.plagiarismEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_PLAGIARISM);
            this.courseManagementService.findWithExercises(params['courseId']).subscribe((findWithExercisesResult) => {
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
        this.paramSub?.unsubscribe();
        this.languageChangeSubscription?.unsubscribe();
    }

    sortRows() {
        this.sortService.sortByProperty(this.studentStatistics, this.predicate, this.reverse);
    }

    /**
     * Initialize the component with the given course.
     * @param course The course which should be displayed.
     */
    private initializeWithCourse(course: Course) {
        this.course = course;
        this.initializeExerciseTitles();
        this.includedExercises = this.determineExercisesIncludedInScore(this.course);
        this.numberOfReleasedExercises = this.determineReleasedExercises(this.course).length;
        this.calculateCourseStatistics(this.course.id!);
    }

    /**
     * Makes sure the exercise titles are unique.
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
     */
    private filterExercisesTypesWithExercises(): Array<ExerciseType> {
        return this.exerciseTypes.filter((exerciseType) => {
            const exercisesWithType = this.exercisesPerType.get(exerciseType)?.length ?? 0;
            return exercisesWithType !== 0;
        });
    }

    /**
     * Fetch all participations with results from the server for the specified course and calculate the corresponding course statistics
     * @param courseId Id of the course
     */
    private async calculateCourseStatistics(courseId: number) {
        const findGradeScoresObservable = this.courseManagementService.findGradeScores(courseId);
        // alternative course scores calculation using participant scores table
        // find grading scale if it exists for course
        const gradingScaleObservable = this.gradingSystemService.findGradingScaleForCourse(courseId).pipe(catchError(() => of(new HttpResponse<GradingScale>())));

        const plagiarismCasesObservable = this.plagiarismEnabled
            ? this.plagiarismCasesService.getCoursePlagiarismCasesForScores(courseId)
            : of(new HttpResponse<PlagiarismCaseDTO[]>());

        forkJoin([findGradeScoresObservable, gradingScaleObservable, plagiarismCasesObservable]).subscribe(([courseGradeInformation, gradingScaleResponse, plagiarismCases]) => {
            this.gradeScores = courseGradeInformation.gradeScores;
            this.students = courseGradeInformation.students;
            if (gradingScaleResponse.body) {
                this.setUpGradingScale(gradingScaleResponse.body);
            }

            this.calculateExerciseLevelStatistics();
            this.exerciseTypesWithExercises = this.filterExercisesTypesWithExercises();

            this.calculateStudentLevelStatistics();

            // if grading scale exists set properties
            if (this.gradingScaleExists) {
                this.calculateGradingScaleInformation(plagiarismCases.body ?? undefined);
            }

            this.calculateAverageAndMedianScores();
            this.scoresToDisplay = this.studentStatistics.map((student) =>
                roundScorePercentSpecifiedByCourseSettings(student.overallPoints / this.maxNumberOfOverallPoints, this.course),
            );
            this.highlightBar(HighlightType.AVERAGE);
        });
    }

    /**
     * Group the exercises by type and gather statistics for each type (titles, max points, accumulated max points).
     */
    private calculateExerciseLevelStatistics() {
        for (const exerciseType of this.exerciseTypes) {
            const exercisesOfType = this.includedExercises.filter((exercise) => exercise.type === exerciseType);
            this.exercisesPerType.set(exerciseType, exercisesOfType);

            const maxPointsOfAllExercisesOfType = new Map();
            exercisesOfType.forEach((exercise) => maxPointsOfAllExercisesOfType.set(exercise.id!, exercise.maxPoints));
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

        this.calculateReachablePresentationPoints();
    }

    /**
     * Calculates the reachable presentation points and adds them to the max number of overall points
     */
    private calculateReachablePresentationPoints() {
        const presentationsNumber = this.gradingScale?.presentationsNumber ?? 0;
        const presentationsWeight = this.gradingScale?.presentationsWeight ?? 0;
        if (this.maxNumberOfOverallPoints > 0 && presentationsNumber > 0 && presentationsWeight > 0 && presentationsWeight < 100) {
            const reachablePointsWithPresentation = (-this.maxNumberOfOverallPoints / (presentationsWeight - 100)) * 100;
            const reachablePresentationPoints = (reachablePointsWithPresentation * presentationsWeight) / 100.0;
            this.maxNumberOfPresentationPoints = roundValueSpecifiedByCourseSettings(reachablePresentationPoints, this.course);
            this.maxNumberOfOverallPoints += this.maxNumberOfPresentationPoints;
        }
    }

    /**
     * Creates students and calculates the points for each exercise and exercise type.
     */
    private calculateStudentLevelStatistics() {
        const studentsMap = this.mapStudentIdToStudentStatistics();

        // prepare exercises
        for (const exercise of this.includedExercises) {
            exercise.numberOfParticipationsWithRatedResult = 0;
            exercise.numberOfSuccessfulParticipations = 0;
        }

        studentsMap.forEach((student) => {
            this.studentStatistics.push(student);
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

            this.addPresentationPointsForStudent(student);
        });

        for (const exerciseType of this.exerciseTypes) {
            // TODO: can we calculate this average only with students who participated in the exercise?
            this.averageNumberOfPointsPerExerciseTypes.set(exerciseType, average(this.studentStatistics.map((student) => student.sumPointsPerExerciseType.get(exerciseType)!)));
        }

        this.averageNumberOfOverallPoints = average(this.studentStatistics.map((student) => student.overallPoints));
        this.averageNumberOfPresentationPoints = average(this.studentStatistics.map((student) => student.presentationPoints));
        this.averageNumberOfSuccessfulExercises = average(this.studentStatistics.map((student) => student.numberOfSuccessfulExercises));
        this.averageNumberOfParticipatedExercises = average(this.studentStatistics.map((student) => student.numberOfParticipatedExercises));

        for (const exerciseType of this.exerciseTypes) {
            for (const exercise of this.exercisesPerType.get(exerciseType)!) {
                exercise.averagePoints = sum(this.studentStatistics.map((student) => student.pointsPerExercise.get(exercise.id!))) / this.studentStatistics.length;
                this.exerciseAveragePointsPerType.setValue(exerciseType, exercise, exercise.averagePoints);
                this.exerciseParticipationsPerType.setValue(exerciseType, exercise, exercise.numberOfParticipationsWithRatedResult!);
                this.exerciseSuccessfulPerType.setValue(exerciseType, exercise, exercise.numberOfSuccessfulParticipations!);
            }
        }

        this.exportReady = true;
    }

    /**
     * Updates the students statistics with the presentation points.
     * @param studentStatistics
     */
    private addPresentationPointsForStudent(studentStatistics: CourseScoresStudentStatistics) {
        const presentationsNumber = this.gradingScale?.presentationsNumber ?? 0;
        if (studentStatistics.presentationScore > 0 && presentationsNumber > 0 && this.maxNumberOfPresentationPoints > 0) {
            const presentationPointAvg = studentStatistics.presentationScore / presentationsNumber!;
            const presentationPoints = (this.maxNumberOfPresentationPoints * presentationPointAvg) / 100.0;

            studentStatistics.presentationPoints = roundValueSpecifiedByCourseSettings(presentationPoints, this.course);
            studentStatistics.overallPoints += studentStatistics.presentationPoints;
        }
    }

    /**
     * Goes through all grade scores, collects the found students and adds all grade scores.
     * @return A map of the student`s id to the student statistic.
     */
    private mapStudentIdToStudentStatistics(): Map<number, CourseScoresStudentStatistics> {
        // student user id --> CourseScoresStudentStatistics
        const studentsMap = new Map<number, CourseScoresStudentStatistics>();

        for (const gradeScore of this.gradeScores) {
            let studentStatistic = studentsMap.get(gradeScore.userId);
            if (!studentStatistic) {
                studentStatistic = new CourseScoresStudentStatistics(this.students.find((student) => student.id === gradeScore.userId)!);
                studentsMap.set(gradeScore.userId, studentStatistic);
            }
            studentStatistic.gradeScores.push(gradeScore);
            if (gradeScore.presentationScore) {
                studentStatistic.presentationScore += gradeScore.presentationScore;
            }
        }
        return studentsMap;
    }

    /**
     * Updates the student statistics with their result in the given exercise.
     * @param student The student that should be updated.
     * @param exercise The exercise that should be included in the statistics.
     */
    private updateStudentStatisticsWithExerciseResults(student: CourseScoresStudentStatistics, exercise: Exercise) {
        const relevantMaxPoints = exercise.maxPoints!;
        const gradeScore = student.gradeScores.find((gradeScore) => gradeScore.exerciseId === exercise.id);
        if (gradeScore) {
            // Note: It is important that we round on the individual exercise level first and then sum up.
            // This is necessary so that the student arrives at the same overall result when doing his own recalculation.
            // Let's assume that the student achieved 1.05 points in each of 5 exercises.
            // In the client, these are now displayed rounded as 1.1 points.
            // If the student adds up the displayed points, he gets a total of 5.5 points.
            // In order to get the same total result as the student, we have to round before summing.
            const pointsAchievedByStudentInExercise = roundValueSpecifiedByCourseSettings((gradeScore.score * relevantMaxPoints) / 100, this.course);
            student.pointsPerExercise.set(exercise.id!, pointsAchievedByStudentInExercise);
            const includedIDs = this.includedExercises.map((includedExercise) => includedExercise.id);
            // We only include this exercise if it is included in the exercise score
            if (includedIDs.includes(exercise.id)) {
                student.overallPoints += pointsAchievedByStudentInExercise;
                const oldPointsSum = student.sumPointsPerExerciseType.get(exercise.type!)!;
                student.sumPointsPerExerciseType.set(exercise.type!, oldPointsSum + pointsAchievedByStudentInExercise);
                student.numberOfParticipatedExercises += 1;
                exercise.numberOfParticipationsWithRatedResult! += 1;
                if (gradeScore.score >= 100) {
                    student.numberOfSuccessfulExercises += 1;
                    exercise.numberOfSuccessfulParticipations! += 1;
                }

                student.pointsPerExerciseType.setValue(exercise.type!, exercise, pointsAchievedByStudentInExercise);
            }
        } else {
            // there is no result, the student has not participated or submitted too late
            student.pointsPerExercise.set(exercise.id!, 0);
            student.pointsPerExerciseType.setValue(exercise.type!, exercise, Number.NaN);
        }
    }

    /**
     * Sets the grading scale
     * @param gradingScale
     */
    setUpGradingScale(gradingScale: GradingScale) {
        this.gradingScaleExists = true;
        this.gradingScale = gradingScale;
        this.gradingScale.gradeSteps = this.gradingSystemService.sortGradeSteps(this.gradingScale.gradeSteps);
        this.isBonus = this.gradingScale.gradeType === GradeType.BONUS;
        this.maxGrade = this.gradingSystemService.maxGrade(this.gradingScale.gradeSteps);
    }

    /**
     * Sets grading scale related properties
     * @param plagiarismCases the list of plagiarism cases involving the students of the course
     */
    calculateGradingScaleInformation(plagiarismCases?: PlagiarismCaseDTO[]) {
        if (this.maxNumberOfOverallPoints >= 0 && this.gradingScale) {
            const plagiarismMap = this.createStudentPlagiarismMap(plagiarismCases);
            const overallPercentage = this.maxNumberOfOverallPoints > 0 ? (this.averageNumberOfOverallPoints / this.maxNumberOfOverallPoints) * 100 : 0;
            this.averageGrade = this.gradingSystemService.findMatchingGradeStep(this.gradingScale.gradeSteps, overallPercentage)!.gradeName;
            for (const student of this.studentStatistics) {
                student.gradeStep = this.findStudentGradeStep(student, this.gradingScale, plagiarismMap);
            }
        }

        this.changeDetector.detectChanges();
    }

    /**
     * Finds the correct grade step for the student according to the given gradingScale, also handles special grades.
     * @param studentStatistics The student for which the grade should be determined.
     * @param gradingScale The grading scale of the course.
     * @param plagiarismMap An object which has value true for a student id if the student has at least one PlagiarismVerdict.PLAGIARISM verdict assigned in the course.
     */
    findStudentGradeStep(studentStatistics: CourseScoresStudentStatistics, gradingScale: GradingScale, plagiarismMap: { [id: number]: boolean }): GradeStep | undefined {
        if (!studentStatistics.gradeScores.length) {
            // Currently the server does not return CourseScoresStudentStatistics for users without participations,
            // but this should handle noParticipation grade if the server response changes.
            return {
                gradeName: gradingScale.noParticipationGrade || GradingScale.DEFAULT_NO_PARTICIPATION_GRADE,
            } as GradeStep;
        } else if (plagiarismMap[studentStatistics.student.id!]) {
            return {
                gradeName: gradingScale.plagiarismGrade || GradingScale.DEFAULT_PLAGIARISM_GRADE,
            } as GradeStep;
        } else {
            const overallPercentageForStudent =
                studentStatistics.overallPoints && this.maxNumberOfOverallPoints ? (studentStatistics.overallPoints / this.maxNumberOfOverallPoints) * 100 : 0;
            return this.gradingSystemService.findMatchingGradeStep(gradingScale.gradeSteps, overallPercentageForStudent);
        }
    }

    private createStudentPlagiarismMap(plagiarismCases?: PlagiarismCaseDTO[]): { [id: number]: boolean } {
        const plagiarismMap: { [id: number]: boolean } = {};
        plagiarismCases?.forEach((plagiarismCase) => {
            if (plagiarismCase.verdict === PlagiarismVerdict.PLAGIARISM && plagiarismCase.studentId) {
                plagiarismMap[plagiarismCase.studentId] = true;
            }
        });
        return plagiarismMap;
    }

    /**
     * Localizes a number, e.g. switching the decimal separator
     */
    localize(numberToLocalize: number): string {
        return this.localeConversionService.toLocaleString(numberToLocalize, this.course.accuracyOfScores);
    }

    /**
     * Method for exporting the csv with the needed data
     */
    exportResults(customCsvOptions?: CsvExportOptions) {
        if (!this.exportReady || this.studentStatistics.length === 0) {
            return;
        }

        const rows: ExportRow[] = [];

        const keys = this.generateExportColumnNames();

        this.studentStatistics.forEach((student) => rows.push(this.generateStudentStatisticsExportRow(student, customCsvOptions)));

        // empty row as separator
        rows.push(this.prepareEmptyExportRow('', customCsvOptions).build());

        rows.push(this.generateExportRowMaxValues(customCsvOptions));
        rows.push(this.generateExportRowAverageValues(customCsvOptions));
        rows.push(this.generateExportRowParticipation(customCsvOptions));
        rows.push(this.generateExportRowSuccessfulParticipation(customCsvOptions));

        if (customCsvOptions) {
            this.exportAsCsv(keys, rows, customCsvOptions);
        } else {
            this.exportAsExcel(keys, rows);
        }
    }

    /**
     * Builds an Excel workbook and starts the download.
     * @param keys The column names used for the export.
     * @param rows The data rows that should be part of the Excel file.
     */
    exportAsExcel(keys: string[], rows: ExportRow[]) {
        const workbook = XLSX.utils.book_new();
        const ws = XLSX.utils.json_to_sheet(rows, { header: keys });
        const worksheetName = 'Course Scores';
        XLSX.utils.book_append_sheet(workbook, ws, worksheetName);

        const workbookProps = {
            Title: `${this.course.title} Scores`,
            Author: `Artemis ${VERSION ?? ''}`,
        };
        const fileName = `${this.course.title} Scores.xlsx`;
        XLSX.writeFile(workbook, fileName, { Props: workbookProps, compression: true });
    }

    /**
     * Builds the CSV from the rows and starts the download.
     * @param keys The column names of the CSV.
     * @param rows The data rows that should be part of the CSV.
     * @param customOptions Custom csv options that should be used for export.
     */
    exportAsCsv(keys: string[], rows: ExportRow[], customOptions: CsvExportOptions) {
        const generalExportOptions = {
            showLabels: true,
            showTitle: false,
            filename: `${this.course.title} Scores`,
            useTextFile: false,
            useBom: true,
            columnHeaders: keys,
        };
        const csvExportConfig = mkConfig(Object.assign(generalExportOptions, customOptions));
        const csvData = generateCsv(csvExportConfig)(rows);
        download(csvExportConfig)(csvData);
    }

    /**
     * Constructs a new builder for a new CSV row.
     * @param csvExportOptions If present, constructs a CSV row builder with these options, otherwise an Excel row builder is returned.
     */
    private newRowBuilder(csvExportOptions?: CsvExportOptions): ExportRowBuilder {
        if (csvExportOptions) {
            return new CsvExportRowBuilder(csvExportOptions.decimalSeparator, this.course.accuracyOfScores);
        } else {
            return new ExcelExportRowBuilder(this.course.accuracyOfScores);
        }
    }

    /**
     * Generates the list of columns that should be part of the exported CSV or Excel file.
     */
    private generateExportColumnNames(): Array<string> {
        const keys = [NAME_KEY, USERNAME_KEY, EMAIL_KEY, REGISTRATION_NUMBER_KEY];

        for (const exerciseType of this.exerciseTypesWithExercises) {
            keys.push(...this.exercisesPerType.get(exerciseType)!.map((exercise) => exercise.title!));
            keys.push(ExportRowBuilder.getExerciseTypeKey(exerciseType, POINTS_KEY));
            keys.push(ExportRowBuilder.getExerciseTypeKey(exerciseType, SCORE_KEY));
        }

        if (this.maxNumberOfPresentationPoints > 0) {
            keys.push(PRESENTATION_POINTS_KEY, PRESENTATION_SCORE_KEY);
        }

        keys.push(COURSE_OVERALL_POINTS_KEY, COURSE_OVERALL_SCORE_KEY);

        if (this.course.presentationScore) {
            keys.push(PRESENTATION_SCORE_KEY);
        }

        if (this.gradingScaleExists) {
            keys.push(this.isBonus ? BONUS_KEY : GRADE_KEY);
        }

        return keys;
    }

    /**
     * Generates a row used in the export file consisting of statistics for the given student.
     * @param studentStatistics The student for which an export row should be created.
     * @param csvExportOptions If present, generates a CSV row with these options, otherwise an Excel row is generated.
     */
    private generateStudentStatisticsExportRow(studentStatistics: CourseScoresStudentStatistics, csvExportOptions?: CsvExportOptions): ExportRow {
        const rowData = this.newRowBuilder(csvExportOptions);

        rowData.setUserInformation(studentStatistics.student.name, studentStatistics.student.login, studentStatistics.student.email, studentStatistics.student.registrationNumber);

        for (const exerciseType of this.exerciseTypesWithExercises) {
            const exercisePointsPerType = studentStatistics.sumPointsPerExerciseType.get(exerciseType)!;

            let exerciseScoresPerType = 0;
            if (this.maxNumberOfPointsPerExerciseType.get(exerciseType)! > 0) {
                exerciseScoresPerType = roundScorePercentSpecifiedByCourseSettings(
                    studentStatistics.sumPointsPerExerciseType.get(exerciseType)! / this.maxNumberOfPointsPerExerciseType.get(exerciseType)!,
                    this.course,
                );
            }
            const exercisesForType = this.exercisesPerType.get(exerciseType)!;
            exercisesForType.forEach((exercise) => {
                const points = roundValueSpecifiedByCourseSettings(studentStatistics.pointsPerExerciseType.getValue(exerciseType, exercise), this.course);
                rowData.setPoints(exercise.title!, points);
            });

            rowData.setExerciseTypePoints(exerciseType, exercisePointsPerType);
            rowData.setExerciseTypeScore(exerciseType, exerciseScoresPerType);
        }

        if (this.maxNumberOfPresentationPoints > 0) {
            const presentationScore = roundScorePercentSpecifiedByCourseSettings(studentStatistics.presentationPoints / this.maxNumberOfPresentationPoints, this.course);
            rowData.setPoints(PRESENTATION_POINTS_KEY, studentStatistics.presentationPoints);
            rowData.setScore(PRESENTATION_SCORE_KEY, presentationScore);
        }

        const overallScore = roundScorePercentSpecifiedByCourseSettings(studentStatistics.overallPoints / this.maxNumberOfOverallPoints, this.course);
        rowData.setPoints(COURSE_OVERALL_POINTS_KEY, studentStatistics.overallPoints);
        rowData.setScore(COURSE_OVERALL_SCORE_KEY, overallScore);

        if (this.course.presentationScore) {
            rowData.setPoints(PRESENTATION_SCORE_KEY, studentStatistics.presentationScore);
        }

        this.setExportRowGradeValue(rowData, studentStatistics.gradeStep?.gradeName);

        return rowData.build();
    }

    /**
     * Generates a row for the exported csv with the maximum values of the various statistics.
     * @param csvExportOptions If present, generates a CSV row with these options, otherwise an Excel row is generated.
     */
    private generateExportRowMaxValues(csvExportOptions?: CsvExportOptions): ExportRow {
        const rowData = this.prepareEmptyExportRow('Max', csvExportOptions);

        for (const exerciseType of this.exerciseTypesWithExercises) {
            const exercisesForType = this.exercisesPerType.get(exerciseType)!;
            exercisesForType.forEach((exercise) => {
                rowData.setPoints(exercise.title!, this.exerciseMaxPointsPerType.getValue(exerciseType, exercise) ?? 0);
            });
            rowData.setExerciseTypePoints(exerciseType, this.maxNumberOfPointsPerExerciseType.get(exerciseType)!);
            rowData.setExerciseTypeScore(exerciseType, 100);
        }

        if (this.maxNumberOfPresentationPoints > 0) {
            rowData.setPoints(PRESENTATION_POINTS_KEY, this.maxNumberOfPresentationPoints);
            rowData.setScore(PRESENTATION_SCORE_KEY, 100);
        }

        rowData.setPoints(COURSE_OVERALL_POINTS_KEY, this.maxNumberOfOverallPoints);
        rowData.setScore(COURSE_OVERALL_SCORE_KEY, 100);

        if (this.course.presentationScore) {
            rowData.set(PRESENTATION_SCORE_KEY, '');
        }

        this.setExportRowGradeValue(rowData, this.maxGrade);

        return rowData.build();
    }

    /**
     * Generates a row for the exported csv with the average values of the various statistics.
     * @param csvExportOptions If present, generates a CSV row with these options, otherwise an Excel row is generated.
     */
    private generateExportRowAverageValues(csvExportOptions?: CsvExportOptions): ExportRow {
        const rowData = this.prepareEmptyExportRow('Average', csvExportOptions);

        for (const exerciseType of this.exerciseTypesWithExercises) {
            const exercisesForType = this.exercisesPerType.get(exerciseType)!;
            exercisesForType.forEach((exercise) => {
                const points = roundValueSpecifiedByCourseSettings(this.exerciseAveragePointsPerType.getValue(exerciseType, exercise), this.course);
                rowData.setPoints(exercise.title!, points);
            });

            const averageScore = roundScorePercentSpecifiedByCourseSettings(
                this.averageNumberOfPointsPerExerciseTypes.get(exerciseType)! / this.maxNumberOfPointsPerExerciseType.get(exerciseType)!,
                this.course,
            );

            rowData.setExerciseTypePoints(exerciseType, this.averageNumberOfPointsPerExerciseTypes.get(exerciseType)!);
            rowData.setExerciseTypeScore(exerciseType, averageScore);
        }

        if (this.maxNumberOfPresentationPoints > 0) {
            const averagePresentationScore = roundScorePercentSpecifiedByCourseSettings(this.averageNumberOfPresentationPoints / this.maxNumberOfPresentationPoints, this.course);
            rowData.setPoints(PRESENTATION_POINTS_KEY, this.averageNumberOfPresentationPoints);
            rowData.setScore(PRESENTATION_SCORE_KEY, averagePresentationScore);
        }

        const averageOverallScore = roundScorePercentSpecifiedByCourseSettings(this.averageNumberOfOverallPoints / this.maxNumberOfOverallPoints, this.course);
        rowData.setPoints(COURSE_OVERALL_POINTS_KEY, this.averageNumberOfOverallPoints);
        rowData.setScore(COURSE_OVERALL_SCORE_KEY, averageOverallScore);

        if (this.course.presentationScore) {
            rowData.set(PRESENTATION_SCORE_KEY, '');
        }

        this.setExportRowGradeValue(rowData, this.averageGrade);

        return rowData.build();
    }

    /**
     * Generates a row for the exported Csv with information about the number of participants.
     * @param csvExportOptions If present, generates a CSV row with these options, otherwise an Excel row is generated.
     */
    private generateExportRowParticipation(csvExportOptions?: CsvExportOptions): ExportRow {
        const rowData = this.prepareEmptyExportRow('Number of Participations', csvExportOptions);

        for (const exerciseType of this.exerciseTypesWithExercises) {
            const exercisesForType = this.exercisesPerType.get(exerciseType)!;
            exercisesForType.forEach((exercise) => {
                rowData.setPoints(exercise.title!, this.exerciseParticipationsPerType.getValue(exerciseType, exercise) ?? 0);
            });
            rowData.setExerciseTypePoints(exerciseType, '');
            rowData.setExerciseTypeScore(exerciseType, '');
        }
        this.setExportRowGradeValue(rowData, '');

        return rowData.build();
    }

    /**
     * Generates a row for the exported Csv with information about the number of successful participants.
     * @param csvExportOptions If present, generates a CSV row with these options, otherwise an Excel row is generated.
     */
    private generateExportRowSuccessfulParticipation(csvExportOptions?: CsvExportOptions): ExportRow {
        const rowData = this.prepareEmptyExportRow('Number of Successful Participations', csvExportOptions);

        for (const exerciseType of this.exerciseTypesWithExercises) {
            const exercisesForType = this.exercisesPerType.get(exerciseType)!;
            exercisesForType.forEach((exercise) => {
                rowData.setPoints(exercise.title!, this.exerciseSuccessfulPerType.getValue(exerciseType, exercise) ?? 0);
            });
            rowData.setExerciseTypePoints(exerciseType, '');
            rowData.setExerciseTypeScore(exerciseType, '');
        }
        this.setExportRowGradeValue(rowData, '');

        return rowData.build();
    }

    /**
     * Prepares an empty row (except for the first column) with an empty column for each exercise type.
     * @param firstValue The value that should be placed in the first column of the row.
     * @param csvExportOptions If present, generates a CSV row with these options, otherwise an Excel row is generated.
     */
    private prepareEmptyExportRow(firstValue: string, csvExportOptions?: CsvExportOptions): ExportRow {
        const emptyLine = this.newRowBuilder(csvExportOptions);

        emptyLine.set(NAME_KEY, firstValue);
        emptyLine.set(USERNAME_KEY, '');
        emptyLine.set(EMAIL_KEY, '');
        emptyLine.set(REGISTRATION_NUMBER_KEY, '');

        for (const exerciseType of this.exerciseTypesWithExercises) {
            const exercisesForType = this.exercisesPerType.get(exerciseType)!;
            exercisesForType.forEach((exercise) => {
                emptyLine.set(exercise.title!, '');
            });
            emptyLine.setExerciseTypePoints(exerciseType, '');
            emptyLine.setExerciseTypeScore(exerciseType, '');
        }

        if (this.maxNumberOfPresentationPoints > 0) {
            emptyLine.set(PRESENTATION_POINTS_KEY, '');
            emptyLine.set(PRESENTATION_SCORE_KEY, '');
        }

        emptyLine.set(COURSE_OVERALL_POINTS_KEY, '');
        emptyLine.set(COURSE_OVERALL_SCORE_KEY, '');

        if (this.course.presentationScore) {
            emptyLine.set(PRESENTATION_SCORE_KEY, '');
        }
        this.setExportRowGradeValue(emptyLine, '');

        return emptyLine;
    }

    /**
     * Puts the given value into the grading scale column of the Export row.
     * @param exportRow The row in which the value should be stored.
     * @param value The value that should be stored in the row.
     */
    private setExportRowGradeValue(exportRow: ExportRow, value: string | number | undefined) {
        if (this.gradingScaleExists) {
            if (this.isBonus) {
                exportRow.set(BONUS_KEY, value);
            } else {
                exportRow.set(GRADE_KEY, value);
            }
        }
    }

    /**
     * Compares two exercises to determine which should be first in a sorted list.
     *
     * Compares them by due date first, then title.
     * @param e1 Some exercise.
     * @param e2 Another exercise.
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
     */
    private determineReleasedExercises(course: Course): Exercise[] {
        return course.exercises!.filter((exercise) => !exercise.releaseDate || exercise.releaseDate.isBefore(dayjs()));
    }

    /**
     * Computes the average of given scores and returns it rounded based on course settings
     * @param scores the scores the average should be computed of
     */
    private calculateAverageScore(scores: number[]): number {
        return roundScorePercentSpecifiedByCourseSettings(mean(scores), this.course);
    }

    /**
     * Computes the average of given points and returns it rounded based on course settings
     * @param points the points the average should be computed of
     */
    private calculateAveragePoints(points: number[]): number {
        return roundValueSpecifiedByCourseSettings(mean(points), this.course);
    }

    /**
     * Computes the median of given scores and returns it rounded based on course settings
     * @param scores the scores the median should be computed of
     */
    private calculateMedianScore(scores: number[]): number {
        return roundScorePercentSpecifiedByCourseSettings(median(scores), this.course);
    }

    /**
     * Computes the median of given points and returns it rounded based on course settings
     * @param points the points the median should be computed of
     */
    private calculateMedianPoints(points: number[]): number {
        return roundValueSpecifiedByCourseSettings(median(points), this.course);
    }

    /**
     * Sets the statistical values displayed in the table next to the distribution chart
     */
    private calculateAverageAndMedianScores(): void {
        const allCoursePoints = sum(this.course.exercises!.map((exercise) => exercise.maxPoints ?? 0)) + this.maxNumberOfPresentationPoints;
        const includedPointsPerStudent = this.studentStatistics.map((student) => student.overallPoints);
        // average points and score included
        const scores = includedPointsPerStudent.map((point) => point / this.maxNumberOfOverallPoints);
        this.averageScoreIncluded = roundScorePercentSpecifiedByCourseSettings(this.averageNumberOfOverallPoints / this.maxNumberOfOverallPoints, this.course);

        // average points and score total
        const achievedPointsTotal = this.studentStatistics.map((student) => sum(Array.from(student.pointsPerExercise.values())) + student.presentationPoints);
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
