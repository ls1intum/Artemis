import { Component, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { TextExercise } from 'app/entities/text-exercise.model';
import { TextExerciseService } from './text-exercise.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ExerciseMode, IncludedInOverallScore, resetDates } from 'app/entities/exercise.model';
import { EditorMode } from 'app/shared/markdown-editor/markdown-editor.component';
import { KatexCommand } from 'app/shared/markdown-editor/commands/katex.command';
import { switchMap, tap } from 'rxjs/operators';
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

@Component({
    selector: 'jhi-text-exercise-update',
    templateUrl: './text-exercise-update.component.html',
    styleUrls: ['../../../shared/exercise/_exercise-update.scss'],
})
export class TextExerciseUpdateComponent implements OnInit {
    readonly IncludedInOverallScore = IncludedInOverallScore;

    @ViewChild('editForm') editForm: NgForm;

    examCourseId?: number;
    isExamMode: boolean;
    isImport = false;
    EditorMode = EditorMode;
    AssessmentType = AssessmentType;

    textExercise: TextExercise;
    backupExercise: TextExercise;
    isSaving: boolean;
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
        private textExerciseService: TextExerciseService,
        private modalService: NgbModal,
        private popupService: ExerciseUpdateWarningService,
        private exerciseService: ExerciseService,
        private exerciseGroupService: ExerciseGroupService,
        private courseService: CourseManagementService,
        private eventManager: EventManager,
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private navigationUtilService: ArtemisNavigationUtilService,
    ) {}

    get editType(): EditType {
        if (this.isImport) {
            return EditType.IMPORT;
        }

        return this.textExercise.id == undefined ? EditType.CREATE : EditType.UPDATE;
    }

    /**
     * Initializes all relevant data for creating or editing text exercise
     */
    ngOnInit() {
        // This is used to scroll page to the top of the page, because the routing keeps the position for the
        // new page from previous page.
        window.scroll(0, 0);

        // Get the textExercise
        this.activatedRoute.data.subscribe(({ textExercise }) => {
            this.textExercise = textExercise;
            this.backupExercise = cloneDeep(this.textExercise);
            this.examCourseId = this.textExercise.course?.id || this.textExercise.exerciseGroup?.exam?.course?.id;
        });

        this.activatedRoute.url
            .pipe(
                tap(
                    (segments) =>
                        (this.isImport = segments.some((segment) => segment.path === 'import', (this.isExamMode = segments.some((segment) => segment.path === 'exercise-groups')))),
                ),
                switchMap(() => this.activatedRoute.params),
                tap((params) => {
                    if (!this.isExamMode) {
                        this.exerciseCategories = this.textExercise.categories || [];
                        if (this.examCourseId) {
                            this.courseService.findAllCategoriesOfCourse(this.examCourseId).subscribe({
                                next: (categoryRes: HttpResponse<string[]>) => {
                                    this.existingCategories = this.exerciseService.convertExerciseCategoriesAsStringFromServer(categoryRes.body!);
                                },
                                error: (error: HttpErrorResponse) => onError(this.alertService, error),
                            });
                        }
                    } else {
                        // Lock individual mode for exam exercises
                        this.textExercise.mode = ExerciseMode.INDIVIDUAL;
                        this.textExercise.teamAssignmentConfig = undefined;
                        this.textExercise.teamMode = false;
                        // Exam exercises cannot be not included into the total score
                        if (this.textExercise.includedInOverallScore === IncludedInOverallScore.NOT_INCLUDED) {
                            this.textExercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
                        }
                    }
                    if (this.isImport) {
                        if (this.isExamMode) {
                            // The target exerciseId where we want to import into
                            const exerciseGroupId = params['exerciseGroupId'];
                            const courseId = params['courseId'];
                            const examId = params['examId'];

                            this.exerciseGroupService.find(courseId, examId, exerciseGroupId).subscribe((res) => (this.textExercise.exerciseGroup = res.body!));
                            // We reference exam exercises by their exercise group, not their course. Having both would lead to conflicts on the server
                            this.textExercise.course = undefined;
                        } else {
                            // The target course where we want to import into
                            const targetCourseId = params['courseId'];
                            this.courseService.find(targetCourseId).subscribe((res) => (this.textExercise.course = res.body!));
                            // We reference normal exercises by their course, having both would lead to conflicts on the server
                            this.textExercise.exerciseGroup = undefined;
                        }
                        resetDates(this.textExercise);
                    }
                }),
            )
            .subscribe();
        this.isSaving = false;
        this.notificationText = undefined;
    }

    /**
     * Return to the exercise overview page
     */
    previousState() {
        this.navigationUtilService.navigateBackFromExerciseUpdate(this.textExercise);
    }

    /**
     * Validates if the date is correct
     */
    validateDate() {
        this.exerciseService.validateDate(this.textExercise);
    }

    /**
     * Updates the exercise categories
     * @param categories list of exercise categories
     */
    updateCategories(categories: ExerciseCategory[]) {
        this.textExercise.categories = categories;
    }

    save() {
        this.isSaving = true;

        new SaveExerciseCommand(this.modalService, this.popupService, this.textExerciseService, this.backupExercise, this.editType, this.alertService)
            .save(this.textExercise, this.notificationText)
            .subscribe({
                next: (exercise: TextExercise) => this.onSaveSuccess(exercise),
                error: (error: HttpErrorResponse) => this.onSaveError(error),
                complete: () => {
                    this.isSaving = false;
                },
            });
    }

    private onSaveSuccess(exercise: TextExercise) {
        this.eventManager.broadcast({ name: 'textExerciseListModification', content: 'OK' });
        this.isSaving = false;
        this.navigationUtilService.navigateForwardFromExerciseUpdateOrCreation(exercise);
    }

    private onSaveError(error: HttpErrorResponse) {
        onError(this.alertService, error);
        this.isSaving = false;
    }
}
