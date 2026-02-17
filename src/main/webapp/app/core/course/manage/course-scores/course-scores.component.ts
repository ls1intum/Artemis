import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin, of } from 'rxjs';
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
import { GradingScaleDTO, toEntity } from 'app/assessment/shared/entities/grading-scale-dto.model';

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
export class CourseScoresComponent implements OnInit {
    private readonly route = inject(ActivatedRoute);
    private readonly courseManagementService = inject(CourseManagementService);
    private readonly sortService = inject(SortService);
    private readonly changeDetector = inject(ChangeDetectorRef);
    private readonly languageHelper = inject(JhiLanguageHelper);
    private readonly localeConversionService = inject(LocaleConversionService);
    private readonly gradingService = inject(GradingService);
    private readonly plagiarismCasesService = inject(PlagiarismCasesService);
    private readonly profileService = inject(ProfileService);
    private readonly destroyRef = inject(DestroyRef);

    // State signals
    readonly plagiarismEnabled = signal(false);
    readonly course = signal<Course | undefined>(undefined);
    readonly gradeScores = signal<GradeScoreDTO[]>([]);
    readonly students = signal<StudentGradeDTO[]>([]);
    readonly includedExercises = signal<Exercise[]>([]);
    readonly studentStatistics = signal<CourseScoresStudentStatistics[]>([]);
    readonly exportReady = signal(false);
    readonly predicate = signal('id');
    readonly reverse = signal(false);

    // Exercise type statistics (kept as regular properties due to complex Map operations)
    private exerciseTypesWithExercises: ExerciseType[] = [];
    private exerciseSuccessfulPerType = new ExerciseTypeStatisticsMap();
    private exerciseParticipationsPerType = new ExerciseTypeStatisticsMap();
    private exerciseAveragePointsPerType = new ExerciseTypeStatisticsMap();
    exerciseMaxPointsPerType = new ExerciseTypeStatisticsMap();
    private exercisesPerType = new Map<ExerciseType, Exercise[]>();

    // Max values signals
    readonly maxNumberOfPointsPerExerciseType = signal(new Map<ExerciseType, number>());
    readonly maxNumberOfOverallPoints = signal(0);
    readonly maxNumberOfPresentationPoints = signal(0);

    // Average values signals
    readonly averageNumberOfParticipatedExercises = signal(0);
    readonly averageNumberOfSuccessfulExercises = signal(0);
    readonly averageNumberOfPointsPerExerciseTypes = signal(new Map<ExerciseType, number>());
    readonly averageNumberOfOverallPoints = signal(0);
    readonly averageNumberOfPresentationPoints = signal(0);

    // Grading scale state signals
    readonly gradingScaleExists = signal(false);
    readonly gradingScale = signal<GradingScale | undefined>(undefined);
    readonly isBonus = signal<boolean | undefined>(undefined);
    readonly maxGrade = signal<string | undefined>(undefined);
    readonly averageGrade = signal<string | undefined>(undefined);
    readonly scoresToDisplay = signal<number[]>([]);
    readonly valueToHighlight = signal<number | undefined>(undefined);
    readonly highlightedType = signal(HighlightType.NONE);

    // Statistical values signals
    readonly numberOfReleasedExercises = signal(0);
    readonly averageScoreIncluded = signal(0);
    readonly medianScoreIncluded = signal(0);
    readonly medianPointsIncluded = signal(0);

    readonly averageScoreTotal = signal(0);
    readonly averagePointsTotal = signal(0);
    readonly medianScoreTotal = signal(0);
    readonly medianPointsTotal = signal(0);

    readonly standardDeviationPointsIncluded = signal(0);
    readonly standardDeviationPointsTotal = signal(0);

    // Expose the imports to the template
    readonly exerciseTypes = exerciseTypes;
    readonly highlightType = HighlightType;
    readonly roundScorePercentSpecifiedByCourseSettings = roundScorePercentSpecifiedByCourseSettings;
    readonly roundValueSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;
    readonly ButtonSize = ButtonSize;

    // Icons
    readonly faSort = faSort;
    readonly faDownload = faDownload;
    readonly faSpinner = faSpinner;
    readonly faClipboard = faClipboard;

    /**
     * On init fetch the course, all released exercises and all participations with the result for the course from the server.
     */
    ngOnInit() {
        this.route.params.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
            this.plagiarismEnabled.set(this.profileService.isModuleFeatureActive(MODULE_FEATURE_PLAGIARISM));
            this.courseManagementService.findWithExercises(params['courseId']).subscribe((findWithExercisesResult) => {
                this.initializeWithCourse(findWithExercisesResult.body!);
            });
        });

        // Update the view if the language was changed
        this.languageHelper.language.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
            this.changeDetector.detectChanges();
        });
    }

    sortRows() {
        const statistics = [...this.studentStatistics()];
        this.sortService.sortByProperty(statistics, this.predicate(), this.reverse());
        this.studentStatistics.set(statistics);
    }

    /**
     * Initialize the component with the given course.
     * @param course The course which should be displayed.
     */
    private initializeWithCourse(course: Course) {
        this.course.set(course);
        this.initializeExerciseTitles();
        this.includedExercises.set(this.determineExercisesIncludedInScore(course));
        this.numberOfReleasedExercises.set(this.determineReleasedExercises(course).length);
        this.calculateCourseStatistics(course.id!);
    }

    /**
     * Makes sure the exercise titles are unique.
     */
    private initializeExerciseTitles() {
        const course = this.course();
        if (!course?.exercises) {
            return;
        }

        const titleMap = new Map<string, number>();
        for (const exercise of course.exercises) {
            const title = exercise.title!;

            if (titleMap.has(title)) {
                const currentValue = titleMap.get(title);
                titleMap.set(title, currentValue! + 1);
            } else {
                titleMap.set(title, 1);
            }
        }

        // this workaround is necessary if the course has exercises with the same title (we add the id to make it unique)
        for (const exercise of course.exercises) {
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
        const gradingScaleObservable = this.gradingService.findGradingScaleForCourse(courseId).pipe(catchError(() => of(new HttpResponse<GradingScaleDTO>())));

        const plagiarismCasesObservable = this.plagiarismEnabled()
            ? this.plagiarismCasesService.getCoursePlagiarismCasesForScores(courseId)
            : of(new HttpResponse<PlagiarismCaseDTO[]>());

        forkJoin([findGradeScoresObservable, gradingScaleObservable, plagiarismCasesObservable]).subscribe(([courseGradeInformation, gradingScaleResponse, plagiarismCases]) => {
            this.gradeScores.set(courseGradeInformation.gradeScores ?? []);
            this.students.set(courseGradeInformation.students ?? []);
            if (gradingScaleResponse.body) {
                this.setUpGradingScale(gradingScaleResponse.body);
            }

            this.calculateExerciseLevelStatistics();
            this.exerciseTypesWithExercises = this.filterExercisesTypesWithExercises();

            this.calculateStudentLevelStatistics();

            // if grading scale exists set properties
            if (this.gradingScaleExists()) {
                this.calculateGradingScaleInformation(plagiarismCases.body ?? undefined);
            }

            this.calculateAverageAndMedianScores();
            const stats = this.studentStatistics();
            const maxPoints = this.maxNumberOfOverallPoints();
            const course = this.course();
            this.scoresToDisplay.set(stats.map((student) => roundScorePercentSpecifiedByCourseSettings(student.overallPoints / maxPoints, course!)));
            this.highlightBar(HighlightType.AVERAGE);
        });
    }

    /**
     * Group the exercises by type and gather statistics for each type (titles, max points, accumulated max points).
     */
    private calculateExerciseLevelStatistics() {
        const maxPointsPerType = new Map<ExerciseType, number>();
        const includedExercises = this.includedExercises();

        for (const exerciseType of this.exerciseTypes) {
            const exercisesOfType = includedExercises.filter((exercise) => exercise.type === exerciseType);
            this.exercisesPerType.set(exerciseType, exercisesOfType);

            const maxPointsOfAllExercisesOfType = new Map();
            exercisesOfType.forEach((exercise) => maxPointsOfAllExercisesOfType.set(exercise.id!, exercise.maxPoints));
            this.exerciseMaxPointsPerType.set(exerciseType, maxPointsOfAllExercisesOfType);

            const maxPointsOfAllIncludedExercisesOfType = exercisesOfType
                // only exercises marked as included_completely increase the maximum reachable number of points
                .filter((exercise) => exercise.includedInOverallScore === IncludedInOverallScore.INCLUDED_COMPLETELY)
                .map((exercise) => exercise.maxPoints!);

            maxPointsPerType.set(exerciseType, sum(maxPointsOfAllIncludedExercisesOfType));
        }
        this.maxNumberOfPointsPerExerciseType.set(maxPointsPerType);

        let overallPoints = 0;
        for (const maxNumberOfPointsPerExerciseTypeElement of maxPointsPerType) {
            overallPoints += maxNumberOfPointsPerExerciseTypeElement[1];
        }
        this.maxNumberOfOverallPoints.set(overallPoints);

        this.calculateReachablePresentationPoints();
    }

    /**
     * Calculates the reachable presentation points and adds them to the max number of overall points
     */
    private calculateReachablePresentationPoints() {
        const scale = this.gradingScale();
        const presentationsNumber = scale?.presentationsNumber ?? 0;
        const presentationsWeight = scale?.presentationsWeight ?? 0;
        const maxOverall = this.maxNumberOfOverallPoints();

        if (maxOverall > 0 && presentationsNumber > 0 && presentationsWeight > 0 && presentationsWeight < 100) {
            const reachablePointsWithPresentation = (-maxOverall / (presentationsWeight - 100)) * 100;
            const reachablePresentationPoints = (reachablePointsWithPresentation * presentationsWeight) / 100.0;
            const course = this.course();
            this.maxNumberOfPresentationPoints.set(roundValueSpecifiedByCourseSettings(reachablePresentationPoints, course!));
            this.maxNumberOfOverallPoints.set(maxOverall + this.maxNumberOfPresentationPoints());
        }
    }

    /**
     * Creates students and calculates the points for each exercise and exercise type.
     */
    private calculateStudentLevelStatistics() {
        const studentsMap = this.mapStudentIdToStudentStatistics();
        const includedExercises = this.includedExercises();
        const course = this.course()!;

        // prepare exercises
        for (const exercise of includedExercises) {
            exercise.numberOfParticipationsWithRatedResult = 0;
            exercise.numberOfSuccessfulParticipations = 0;
        }

        const statistics: CourseScoresStudentStatistics[] = [];
        const maxPointsPerType = this.maxNumberOfPointsPerExerciseType();

        studentsMap.forEach((student) => {
            statistics.push(student);
            // We need the information of not included exercises as well in order to compute the total average and median
            for (const exercise of this.determineReleasedExercises(course)) {
                this.updateStudentStatisticsWithExerciseResults(student, exercise);
            }

            for (const exerciseType of this.exerciseTypes) {
                if (maxPointsPerType.get(exerciseType)! > 0) {
                    student.scorePerExerciseType.set(exerciseType, (student.sumPointsPerExerciseType.get(exerciseType)! / maxPointsPerType.get(exerciseType)!) * 100);
                }
            }

            this.addPresentationPointsForStudent(student);
        });

        this.studentStatistics.set(statistics);

        const avgPointsPerType = new Map<ExerciseType, number>();
        for (const exerciseType of this.exerciseTypes) {
            // TODO: can we calculate this average only with students who participated in the exercise?
            avgPointsPerType.set(exerciseType, average(statistics.map((student) => student.sumPointsPerExerciseType.get(exerciseType)!)));
        }
        this.averageNumberOfPointsPerExerciseTypes.set(avgPointsPerType);

        this.averageNumberOfOverallPoints.set(average(statistics.map((student) => student.overallPoints)));
        this.averageNumberOfPresentationPoints.set(average(statistics.map((student) => student.presentationPoints)));
        this.averageNumberOfSuccessfulExercises.set(average(statistics.map((student) => student.numberOfSuccessfulExercises)));
        this.averageNumberOfParticipatedExercises.set(average(statistics.map((student) => student.numberOfParticipatedExercises)));

        for (const exerciseType of this.exerciseTypes) {
            for (const exercise of this.exercisesPerType.get(exerciseType)!) {
                exercise.averagePoints = sum(statistics.map((student) => student.pointsPerExercise.get(exercise.id!))) / statistics.length;
                this.exerciseAveragePointsPerType.setValue(exerciseType, exercise, exercise.averagePoints);
                this.exerciseParticipationsPerType.setValue(exerciseType, exercise, exercise.numberOfParticipationsWithRatedResult!);
                this.exerciseSuccessfulPerType.setValue(exerciseType, exercise, exercise.numberOfSuccessfulParticipations!);
            }
        }

        this.exportReady.set(true);
    }

    /**
     * Updates the students' statistics with the presentation points.
     * @param studentStatistics
     */
    private addPresentationPointsForStudent(studentStatistics: CourseScoresStudentStatistics) {
        const scale = this.gradingScale();
        const presentationsNumber = scale?.presentationsNumber ?? 0;
        const maxPresentationPoints = this.maxNumberOfPresentationPoints();
        const course = this.course()!;

        if (studentStatistics.presentationScore > 0 && presentationsNumber > 0 && maxPresentationPoints > 0) {
            const presentationPointAvg = studentStatistics.presentationScore / presentationsNumber!;
            const presentationPoints = (maxPresentationPoints * presentationPointAvg) / 100.0;

            studentStatistics.presentationPoints = roundValueSpecifiedByCourseSettings(presentationPoints, course);
            studentStatistics.overallPoints += studentStatistics.presentationPoints;
        }
    }

    /**
     * Goes through all grade scores, collects the found students and adds all grade scores.
     * @return A map of the student's id to the student statistic.
     */
    private mapStudentIdToStudentStatistics(): Map<number, CourseScoresStudentStatistics> {
        // student user id --> CourseScoresStudentStatistics
        const studentsMap = new Map<number, CourseScoresStudentStatistics>();
        const gradeScores = this.gradeScores();
        const students = this.students();

        for (const gradeScore of gradeScores) {
            let studentStatistic = studentsMap.get(gradeScore.userId);
            if (!studentStatistic) {
                studentStatistic = new CourseScoresStudentStatistics(students.find((student) => student.id === gradeScore.userId)!);
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
        const course = this.course()!;
        const includedExercises = this.includedExercises();
        const gradeScore = student.gradeScores.find((gradeScore) => gradeScore.exerciseId === exercise.id);

        if (gradeScore) {
            // Note: It is important that we round on the individual exercise level first and then sum up.
            // This is necessary so that the student arrives at the same overall result when doing his own recalculation.
            // Let's assume that the student achieved 1.05 points in each of 5 exercises.
            // In the client, these are now displayed rounded as 1.1 points.
            // If the student adds up the displayed points, he gets a total of 5.5 points.
            // In order to get the same total result as the student, we have to round before summing.
            const pointsAchievedByStudentInExercise = roundValueSpecifiedByCourseSettings((gradeScore.score * relevantMaxPoints) / 100, course);
            student.pointsPerExercise.set(exercise.id!, pointsAchievedByStudentInExercise);
            const includedIDs = includedExercises.map((includedExercise) => includedExercise.id);
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
     * @param gradingScaleDTO
     */
    setUpGradingScale(gradingScaleDTO: GradingScaleDTO) {
        this.gradingScaleExists.set(true);
        gradingScaleDTO.gradeSteps.gradeSteps = this.gradingService.sortGradeSteps(gradingScaleDTO.gradeSteps.gradeSteps);
        this.gradingScale.set(toEntity(gradingScaleDTO, this.course()!));
        this.isBonus.set(gradingScaleDTO.gradeSteps.gradeType === GradeType.BONUS);
        this.maxGrade.set(this.gradingService.maxGrade(gradingScaleDTO.gradeSteps.gradeSteps));
    }

    /**
     * Sets grading scale related properties
     * @param plagiarismCases the list of plagiarism cases involving the students of the course
     */
    calculateGradingScaleInformation(plagiarismCases?: PlagiarismCaseDTO[]) {
        const maxOverall = this.maxNumberOfOverallPoints();
        const avgOverall = this.averageNumberOfOverallPoints();
        const scale = this.gradingScale();
        const statistics = this.studentStatistics();

        if (maxOverall >= 0 && scale) {
            const plagiarismMap = this.createStudentPlagiarismMap(plagiarismCases);
            const overallPercentage = maxOverall > 0 ? (avgOverall / maxOverall) * 100 : 0;
            const matchingGradeStep = this.gradingService.findMatchingGradeStep(scale.gradeSteps, overallPercentage);
            if (matchingGradeStep) {
                this.averageGrade.set(matchingGradeStep.gradeName);
            }

            const updatedStatistics = statistics.map((student) => {
                student.gradeStep = this.findStudentGradeStep(student, scale, plagiarismMap);
                return student;
            });
            this.studentStatistics.set(updatedStatistics);
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
        const maxOverall = this.maxNumberOfOverallPoints();

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
            const overallPercentageForStudent = studentStatistics.overallPoints && maxOverall ? (studentStatistics.overallPoints / maxOverall) * 100 : 0;
            return this.gradingService.findMatchingGradeStep(gradingScale.gradeSteps, overallPercentageForStudent);
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
        const course = this.course();
        return this.localeConversionService.toLocaleString(numberToLocalize, course?.accuracyOfScores);
    }

    /**
     * Method for exporting the csv with the needed data
     */
    exportResults(customCsvOptions?: CsvExportOptions) {
        const statistics = this.studentStatistics();
        if (!this.exportReady() || statistics.length === 0) {
            return;
        }

        const rows: ExportRow[] = [];

        const keys = this.generateExportColumnNames();

        statistics.forEach((student) => rows.push(this.generateStudentStatisticsExportRow(student, customCsvOptions)));

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
        const course = this.course()!;
        const workbook = XLSX.utils.book_new();
        const ws = XLSX.utils.json_to_sheet(rows, { header: keys });
        const worksheetName = 'Course Scores';
        XLSX.utils.book_append_sheet(workbook, ws, worksheetName);

        const workbookProps = {
            Title: `${course.title} Scores`,
            Author: `Artemis ${VERSION ?? ''}`,
        };
        const fileName = `${course.title} Scores.xlsx`;
        XLSX.writeFile(workbook, fileName, { Props: workbookProps, compression: true });
    }

    /**
     * Builds the CSV from the rows and starts the download.
     * @param keys The column names of the CSV.
     * @param rows The data rows that should be part of the CSV.
     * @param customOptions Custom csv options that should be used for export.
     */
    exportAsCsv(keys: string[], rows: ExportRow[], customOptions: CsvExportOptions) {
        const course = this.course()!;
        const generalExportOptions = {
            showLabels: true,
            showTitle: false,
            filename: `${course.title} Scores`,
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
        const course = this.course()!;
        if (csvExportOptions) {
            return new CsvExportRowBuilder(csvExportOptions.decimalSeparator, course.accuracyOfScores);
        } else {
            return new ExcelExportRowBuilder(course.accuracyOfScores);
        }
    }

    /**
     * Generates the list of columns that should be part of the exported CSV or Excel file.
     */
    private generateExportColumnNames(): Array<string> {
        const keys = [NAME_KEY, USERNAME_KEY, EMAIL_KEY, REGISTRATION_NUMBER_KEY];
        const maxPresentationPoints = this.maxNumberOfPresentationPoints();
        const course = this.course()!;

        for (const exerciseType of this.exerciseTypesWithExercises) {
            keys.push(...this.exercisesPerType.get(exerciseType)!.map((exercise) => exercise.title!));
            keys.push(ExportRowBuilder.getExerciseTypeKey(exerciseType, POINTS_KEY));
            keys.push(ExportRowBuilder.getExerciseTypeKey(exerciseType, SCORE_KEY));
        }

        if (maxPresentationPoints > 0) {
            keys.push(PRESENTATION_POINTS_KEY, PRESENTATION_SCORE_KEY);
        }

        keys.push(COURSE_OVERALL_POINTS_KEY, COURSE_OVERALL_SCORE_KEY);

        if (course.presentationScore) {
            keys.push(PRESENTATION_SCORE_KEY);
        }

        if (this.gradingScaleExists()) {
            keys.push(this.isBonus() ? BONUS_KEY : GRADE_KEY);
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
        const maxPointsPerType = this.maxNumberOfPointsPerExerciseType();
        const maxPresentationPoints = this.maxNumberOfPresentationPoints();
        const maxOverall = this.maxNumberOfOverallPoints();
        const course = this.course()!;

        rowData.setUserInformation(studentStatistics.student.name, studentStatistics.student.login, studentStatistics.student.email, studentStatistics.student.registrationNumber);

        for (const exerciseType of this.exerciseTypesWithExercises) {
            const exercisePointsPerType = studentStatistics.sumPointsPerExerciseType.get(exerciseType)!;

            let exerciseScoresPerType = 0;
            if (maxPointsPerType.get(exerciseType)! > 0) {
                exerciseScoresPerType = roundScorePercentSpecifiedByCourseSettings(
                    studentStatistics.sumPointsPerExerciseType.get(exerciseType)! / maxPointsPerType.get(exerciseType)!,
                    course,
                );
            }
            const exercisesForType = this.exercisesPerType.get(exerciseType)!;
            exercisesForType.forEach((exercise) => {
                const points = roundValueSpecifiedByCourseSettings(studentStatistics.pointsPerExerciseType.getValue(exerciseType, exercise), course);
                rowData.setPoints(exercise.title!, points);
            });

            rowData.setExerciseTypePoints(exerciseType, exercisePointsPerType);
            rowData.setExerciseTypeScore(exerciseType, exerciseScoresPerType);
        }

        if (maxPresentationPoints > 0) {
            const presentationScore = roundScorePercentSpecifiedByCourseSettings(studentStatistics.presentationPoints / maxPresentationPoints, course);
            rowData.setPoints(PRESENTATION_POINTS_KEY, studentStatistics.presentationPoints);
            rowData.setScore(PRESENTATION_SCORE_KEY, presentationScore);
        }

        const overallScore = roundScorePercentSpecifiedByCourseSettings(studentStatistics.overallPoints / maxOverall, course);
        rowData.setPoints(COURSE_OVERALL_POINTS_KEY, studentStatistics.overallPoints);
        rowData.setScore(COURSE_OVERALL_SCORE_KEY, overallScore);

        if (course.presentationScore) {
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
        const maxPointsPerType = this.maxNumberOfPointsPerExerciseType();
        const maxPresentationPoints = this.maxNumberOfPresentationPoints();
        const maxOverall = this.maxNumberOfOverallPoints();

        for (const exerciseType of this.exerciseTypesWithExercises) {
            const exercisesForType = this.exercisesPerType.get(exerciseType)!;
            exercisesForType.forEach((exercise) => {
                rowData.setPoints(exercise.title!, this.exerciseMaxPointsPerType.getValue(exerciseType, exercise) ?? 0);
            });
            rowData.setExerciseTypePoints(exerciseType, maxPointsPerType.get(exerciseType)!);
            rowData.setExerciseTypeScore(exerciseType, 100);
        }

        if (maxPresentationPoints > 0) {
            rowData.setPoints(PRESENTATION_POINTS_KEY, maxPresentationPoints);
            rowData.setScore(PRESENTATION_SCORE_KEY, 100);
        }

        rowData.setPoints(COURSE_OVERALL_POINTS_KEY, maxOverall);
        rowData.setScore(COURSE_OVERALL_SCORE_KEY, 100);

        const course = this.course()!;
        if (course.presentationScore) {
            rowData.set(PRESENTATION_SCORE_KEY, '');
        }

        this.setExportRowGradeValue(rowData, this.maxGrade());

        return rowData.build();
    }

    /**
     * Generates a row for the exported csv with the average values of the various statistics.
     * @param csvExportOptions If present, generates a CSV row with these options, otherwise an Excel row is generated.
     */
    private generateExportRowAverageValues(csvExportOptions?: CsvExportOptions): ExportRow {
        const rowData = this.prepareEmptyExportRow('Average', csvExportOptions);
        const avgPointsPerType = this.averageNumberOfPointsPerExerciseTypes();
        const maxPointsPerType = this.maxNumberOfPointsPerExerciseType();
        const avgPresentationPoints = this.averageNumberOfPresentationPoints();
        const maxPresentationPoints = this.maxNumberOfPresentationPoints();
        const avgOverall = this.averageNumberOfOverallPoints();
        const maxOverall = this.maxNumberOfOverallPoints();
        const course = this.course()!;

        for (const exerciseType of this.exerciseTypesWithExercises) {
            const exercisesForType = this.exercisesPerType.get(exerciseType)!;
            exercisesForType.forEach((exercise) => {
                const points = roundValueSpecifiedByCourseSettings(this.exerciseAveragePointsPerType.getValue(exerciseType, exercise), course);
                rowData.setPoints(exercise.title!, points);
            });

            const averageScore = roundScorePercentSpecifiedByCourseSettings(avgPointsPerType.get(exerciseType)! / maxPointsPerType.get(exerciseType)!, course);

            rowData.setExerciseTypePoints(exerciseType, avgPointsPerType.get(exerciseType)!);
            rowData.setExerciseTypeScore(exerciseType, averageScore);
        }

        if (maxPresentationPoints > 0) {
            const averagePresentationScore = roundScorePercentSpecifiedByCourseSettings(avgPresentationPoints / maxPresentationPoints, course);
            rowData.setPoints(PRESENTATION_POINTS_KEY, avgPresentationPoints);
            rowData.setScore(PRESENTATION_SCORE_KEY, averagePresentationScore);
        }

        const averageOverallScore = roundScorePercentSpecifiedByCourseSettings(avgOverall / maxOverall, course);
        rowData.setPoints(COURSE_OVERALL_POINTS_KEY, avgOverall);
        rowData.setScore(COURSE_OVERALL_SCORE_KEY, averageOverallScore);

        if (course.presentationScore) {
            rowData.set(PRESENTATION_SCORE_KEY, '');
        }

        this.setExportRowGradeValue(rowData, this.averageGrade());

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
        const maxPresentationPoints = this.maxNumberOfPresentationPoints();
        const course = this.course()!;

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

        if (maxPresentationPoints > 0) {
            emptyLine.set(PRESENTATION_POINTS_KEY, '');
            emptyLine.set(PRESENTATION_SCORE_KEY, '');
        }

        emptyLine.set(COURSE_OVERALL_POINTS_KEY, '');
        emptyLine.set(COURSE_OVERALL_SCORE_KEY, '');

        if (course.presentationScore) {
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
        if (this.gradingScaleExists()) {
            if (this.isBonus()) {
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
        const course = this.course()!;
        return roundScorePercentSpecifiedByCourseSettings(mean(scores), course);
    }

    /**
     * Computes the average of given points and returns it rounded based on course settings
     * @param points the points the average should be computed of
     */
    private calculateAveragePoints(points: number[]): number {
        const course = this.course()!;
        return roundValueSpecifiedByCourseSettings(mean(points), course);
    }

    /**
     * Computes the median of given scores and returns it rounded based on course settings
     * @param scores the scores the median should be computed of
     */
    private calculateMedianScore(scores: number[]): number {
        const course = this.course()!;
        return roundScorePercentSpecifiedByCourseSettings(median(scores), course);
    }

    /**
     * Computes the median of given points and returns it rounded based on course settings
     * @param points the points the median should be computed of
     */
    private calculateMedianPoints(points: number[]): number {
        const course = this.course()!;
        return roundValueSpecifiedByCourseSettings(median(points), course);
    }

    /**
     * Sets the statistical values displayed in the table next to the distribution chart
     */
    private calculateAverageAndMedianScores(): void {
        const course = this.course()!;
        const statistics = this.studentStatistics();
        const maxOverall = this.maxNumberOfOverallPoints();
        const maxPresentationPoints = this.maxNumberOfPresentationPoints();
        const avgOverall = this.averageNumberOfOverallPoints();

        // Guard against empty statistics array - mean/median require at least one data point
        if (statistics.length === 0) {
            return;
        }

        const allCoursePoints = sum(course.exercises!.map((exercise) => exercise.maxPoints ?? 0)) + maxPresentationPoints;
        const includedPointsPerStudent = statistics.map((student) => student.overallPoints);
        // average points and score included
        const scores = includedPointsPerStudent.map((point) => point / maxOverall);
        this.averageScoreIncluded.set(roundScorePercentSpecifiedByCourseSettings(avgOverall / maxOverall, course));

        // average points and score total
        const achievedPointsTotal = statistics.map((student) => sum(Array.from(student.pointsPerExercise.values())) + student.presentationPoints);
        const averageScores = achievedPointsTotal.map((totalPoints) => totalPoints / allCoursePoints);

        this.averagePointsTotal.set(this.calculateAveragePoints(achievedPointsTotal));
        this.averageScoreTotal.set(this.calculateAverageScore(averageScores));

        // median points and score included
        this.medianPointsIncluded.set(this.calculateMedianPoints(includedPointsPerStudent));
        this.medianScoreIncluded.set(this.calculateMedianScore(scores));

        // median points and score total
        this.medianPointsTotal.set(this.calculateMedianPoints(achievedPointsTotal));
        this.medianScoreTotal.set(this.calculateMedianScore(averageScores));

        // Since these two values are only statistical details, there is no need to make the rounding dependent of the course settings
        // standard deviation points included
        this.standardDeviationPointsIncluded.set(round(standardDeviation(includedPointsPerStudent), 2));

        // standard deviation points total
        this.standardDeviationPointsTotal.set(round(standardDeviation(achievedPointsTotal), 2));
    }

    /**
     * Handles the case if the user selects either the average or the median in the table next to the chart
     * @param type the statistical type that is selected by the user
     */
    highlightBar(type: HighlightType) {
        if (this.highlightedType() === type) {
            this.valueToHighlight.set(undefined);
            this.highlightedType.set(HighlightType.NONE);
            this.changeDetector.detectChanges();
            return;
        }
        switch (type) {
            case HighlightType.AVERAGE:
                this.valueToHighlight.set(this.averageScoreIncluded());
                this.highlightedType.set(HighlightType.AVERAGE);
                break;
            case HighlightType.MEDIAN:
                this.valueToHighlight.set(this.medianScoreIncluded());
                this.highlightedType.set(HighlightType.MEDIAN);
                break;
        }
        this.changeDetector.detectChanges();
    }
}
