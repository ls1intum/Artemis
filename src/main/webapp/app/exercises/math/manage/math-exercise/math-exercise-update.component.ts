import { Component, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MathExercise } from 'app/entities/math-exercise.model';
import { MathExerciseService } from './math-exercise.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ExerciseMode, IncludedInOverallScore, resetDates } from 'app/entities/exercise.model';
import { EditorMode } from 'app/shared/markdown-editor/markdown-editor.component';
import { KatexCommand } from 'app/shared/markdown-editor/commands/katex.command';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { NgForm } from '@angular/forms';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { cloneDeep } from 'lodash-es';
import { ExerciseUpdateWarningService } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { onError } from 'app/shared/util/global.utils';
import { EditType, SaveExerciseCommand } from 'app/exercises/shared/exercise/exercise.utils';
import { AlertService } from 'app/core/util/alert.service';
import { EventManager } from 'app/core/util/event-manager.service';
import { faBan, faSave } from '@fortawesome/free-solid-svg-icons';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { AthenaService } from 'app/assessment/athena.service';
import { Observable, combineLatest, takeWhile } from 'rxjs';
import { scrollToTopOfPage } from 'app/shared/util/utils';
import { loadCourseExerciseCategories } from 'app/exercises/shared/course-exercises/course-utils';

@Component({
    selector: 'jhi-math-exercise-update',
    templateUrl: './math-exercise-update.component.html',
})
export class MathExerciseUpdateComponent implements OnInit {
    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly documentationType: DocumentationType = 'Math';

    @ViewChild('editForm') editForm: NgForm;

    private componentActive = true;

    examCourseId?: number;
    isExamMode: boolean;
    isImport = false;
    goBackAfterSaving = false;
    EditorMode = EditorMode;
    AssessmentType = AssessmentType;
    isAthenaEnabled$: Observable<boolean> | undefined;

    mathExercise: MathExercise;
    backupExercise: MathExercise;
    isSaving = false;
    exerciseCategories: ExerciseCategory[];
    existingCategories: ExerciseCategory[];
    notificationText?: string;

    domainCommandsProblemStatement = [new KatexCommand()];
    domainCommandsSampleSolution = [new KatexCommand()];

    // Icons
    faSave = faSave;
    faBan = faBan;

    constructor(
        private alertService: AlertService,
        private mathExerciseService: MathExerciseService,
        private modalService: NgbModal,
        private popupService: ExerciseUpdateWarningService,
        private exerciseService: ExerciseService,
        private exerciseGroupService: ExerciseGroupService,
        private courseService: CourseManagementService,
        private eventManager: EventManager,
        private route: ActivatedRoute,
        private navigationUtilService: ArtemisNavigationUtilService,
        private athenaService: AthenaService,
    ) {}

    get editType(): EditType {
        if (this.isImport) {
            return EditType.IMPORT;
        }

        return this.mathExercise.id == undefined ? EditType.CREATE : EditType.UPDATE;
    }

    /**
     * Initializes all relevant data for creating or editing math exercise
     */
    ngOnInit() {
        scrollToTopOfPage();

        combineLatest([this.route.data, this.route.url, this.route.params])
            .pipe(takeWhile(() => this.componentActive))
            .subscribe(([data, segments, params]) => {
                const isImport = segments.some(({ path }) => path === 'import');
                const isExamMode = segments.some(({ path }) => path === 'exercise-groups');

                this.mathExercise = data.mathExercise;
                this.backupExercise = cloneDeep(this.mathExercise);
                this.examCourseId = this.mathExercise.course?.id || this.mathExercise.exerciseGroup?.exam?.course?.id;

                if (isExamMode) {
                    // Lock individual mode for exam exercises
                    this.mathExercise.mode = ExerciseMode.INDIVIDUAL;
                    this.mathExercise.teamAssignmentConfig = undefined;
                    this.mathExercise.teamMode = false;
                    // Exam exercises cannot be not included into the total score
                    if (this.mathExercise.includedInOverallScore === IncludedInOverallScore.NOT_INCLUDED) {
                        this.mathExercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
                    }
                } else {
                    this.exerciseCategories = this.mathExercise.categories || [];
                    if (this.examCourseId) {
                        this.loadCourseExerciseCategories(this.examCourseId);
                    }
                }

                // TODO: the following code has nested subscribers, which is not ideal. We should pipe the observables instead.
                if (isImport) {
                    const courseId = params['courseId'];

                    if (isExamMode) {
                        // The target exerciseId where we want to import into
                        const exerciseGroupId = params['exerciseGroupId'];
                        const examId = params['examId'];

                        this.exerciseGroupService.find(courseId, examId, exerciseGroupId).subscribe((res) => (this.mathExercise.exerciseGroup = res.body!));
                        // We reference exam exercises by their exercise group, not their course. Having both would lead to conflicts on the server
                        this.mathExercise.course = undefined;
                    } else {
                        // The target course where we want to import into
                        this.courseService.find(courseId).subscribe((res) => (this.mathExercise.course = res.body!));
                        // We reference normal exercises by their course, having both would lead to conflicts on the server
                        this.mathExercise.exerciseGroup = undefined;
                    }

                    this.loadCourseExerciseCategories(courseId);
                    resetDates(this.mathExercise);
                }

                this.isImport = isImport;
                this.isExamMode = isExamMode;
            });

        this.route.queryParams.subscribe((params) => {
            if (params.shouldHaveBackButtonToWizard) {
                this.goBackAfterSaving = true;
            }
        });

        this.isAthenaEnabled$ = this.athenaService.isEnabled();
    }

    ngOnDestroy() {
        this.componentActive = false;
    }

    /**
     * Return to the exercise overview page
     */
    previousState() {
        this.navigationUtilService.navigateBackFromExerciseUpdate(this.mathExercise);
    }

    /**
     * Validates if the date is correct
     */
    validateDate() {
        this.exerciseService.validateDate(this.mathExercise);
    }

    /**
     * Updates the exercise categories
     * @param categories list of exercise categories
     */
    updateCategories(categories: ExerciseCategory[]) {
        this.mathExercise.categories = categories;
        this.exerciseCategories = categories;
    }

    save() {
        this.isSaving = true;

        new SaveExerciseCommand(this.modalService, this.popupService, this.mathExerciseService, this.backupExercise, this.editType, this.alertService)
            .save(this.mathExercise, this.isExamMode, this.notificationText)
            .subscribe({
                next: (exercise: MathExercise) => this.onSaveSuccess(exercise),
                error: (error: HttpErrorResponse) => this.onSaveError(error),
                complete: () => void (this.isSaving = false),
            });
    }

    private loadCourseExerciseCategories(courseId: number) {
        loadCourseExerciseCategories(courseId, this.courseService, this.exerciseService, this.alertService).subscribe((existingCategories) => {
            this.existingCategories = existingCategories;
        });
    }

    private onSaveSuccess(exercise: MathExercise) {
        this.eventManager.broadcast({ name: 'mathExerciseListModification', content: 'OK' });
        this.isSaving = false;

        if (this.goBackAfterSaving) {
            this.navigationUtilService.navigateBack();

            return;
        }

        this.navigationUtilService.navigateForwardFromExerciseUpdateOrCreation(exercise);
    }

    private onSaveError(errorRes: HttpErrorResponse) {
        if (errorRes.error && errorRes.error.title) {
            this.alertService.addErrorAlert(errorRes.error.title, errorRes.error.message, errorRes.error.params);
        } else {
            onError(this.alertService, errorRes);
        }
        this.isSaving = false;
    }
}
