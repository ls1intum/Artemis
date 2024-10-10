import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { Observable, Subscription, filter, switchMap } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { ExerciseHintService } from '../shared/exercise-hint.service';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { faBan, faCircleNotch, faSave } from '@fortawesome/free-solid-svg-icons';
import { ExerciseHint, HintType } from 'app/entities/hestia/exercise-hint.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExerciseSolutionEntry } from 'app/entities/hestia/programming-exercise-solution-entry.model';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { ProgrammingExerciseServerSideTask } from 'app/entities/hestia/programming-exercise-task.model';
import { ManualSolutionEntryCreationModalComponent } from 'app/exercises/programming/hestia/generation-overview/manual-solution-entry-creation-modal/manual-solution-entry-creation-modal.component';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { CodeHint } from 'app/entities/hestia/code-hint-model';
import { CodeHintService } from 'app/exercises/shared/exercise-hint/services/code-hint.service';
import { onError } from 'app/shared/util/global.utils';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { IrisSettings } from 'app/entities/iris/settings/iris-settings.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ButtonType } from 'app/shared/components/button.component';
import { PROFILE_IRIS } from 'app/app.constants';
import { FormulaAction } from 'app/shared/monaco-editor/model/actions/formula.action';
import { MarkdownEditorHeight } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';

const DEFAULT_DISPLAY_THRESHOLD = 3;

@Component({
    selector: 'jhi-exercise-hint-update',
    templateUrl: './exercise-hint-update.component.html',
})
export class ExerciseHintUpdateComponent implements OnInit, OnDestroy {
    MarkdownEditorHeight = MarkdownEditorHeight;

    courseId: number;
    exercise: ProgrammingExercise;
    exerciseHint = new ExerciseHint();
    solutionEntries: ProgrammingExerciseSolutionEntry[];

    programmingExercise: ProgrammingExercise;
    tasks: ProgrammingExerciseServerSideTask[];
    irisSettings?: IrisSettings;

    isSaving: boolean;
    isGeneratingDescription: boolean;
    paramSub: Subscription;

    domainActions = [new FormulaAction()];

    // Icons
    faCircleNotch = faCircleNotch;
    faBan = faBan;
    faSave = faSave;

    // Enums
    readonly HintType = HintType;
    readonly ButtonType = ButtonType;

    constructor(
        private route: ActivatedRoute,
        protected alertService: AlertService,
        private modalService: NgbModal,
        protected codeHintService: CodeHintService,
        protected exerciseHintService: ExerciseHintService,
        private programmingExerciseService: ProgrammingExerciseService,
        private navigationUtilService: ArtemisNavigationUtilService,
        protected irisSettingsService: IrisSettingsService,
        private profileService: ProfileService,
    ) {}

    /**
     * Fetches the exercise from the server and assigns it on the exercise hint
     */
    ngOnInit() {
        this.paramSub = this.route.params.subscribe((params) => {
            this.courseId = params['courseId'];
            this.isSaving = false;
            this.isGeneratingDescription = false;
        });
        this.route.data.subscribe(({ exerciseHint, exercise }) => {
            this.exercise = exercise;
            exerciseHint.exercise = exercise;
            this.exerciseHint = exerciseHint;
            this.exerciseHint.displayThreshold = this.exerciseHint.displayThreshold ?? DEFAULT_DISPLAY_THRESHOLD;

            this.programmingExerciseService.getTasksAndTestsExtractedFromProblemStatement(this.exercise.id!).subscribe((tasks) => {
                this.tasks = tasks;

                const selectedTask = this.tasks.find((task) => task.id === this.exerciseHint.programmingExerciseTask?.id);
                if (selectedTask) {
                    this.exerciseHint.programmingExerciseTask = selectedTask;
                } else if (tasks.length) {
                    this.exerciseHint.programmingExerciseTask = this.tasks[0];
                }
            });

            this.profileService
                .getProfileInfo()
                .pipe(
                    filter((profileInfo) => profileInfo?.activeProfiles?.includes(PROFILE_IRIS)),
                    switchMap(() => this.irisSettingsService.getCombinedExerciseSettings(this.exercise.id!)),
                )
                .subscribe((settings) => {
                    this.irisSettings = settings;
                });
        });
    }

    openManualEntryCreationModal() {
        const codeHint = this.exerciseHint as CodeHint;
        const testCasesForCurrentTask = codeHint.programmingExerciseTask?.testCases ?? [];
        const modalRef: NgbModalRef = this.modalService.open(ManualSolutionEntryCreationModalComponent as Component, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.exerciseId = this.exercise.id!;
        modalRef.componentInstance.codeHint = codeHint;
        modalRef.componentInstance.testCases = testCasesForCurrentTask;
        modalRef.componentInstance.onEntryCreated.subscribe((createdEntry: ProgrammingExerciseSolutionEntry) => {
            codeHint!.solutionEntries!.push(createdEntry);
        });
    }

    /**
     * Unsubscribes from the param subscription
     */
    ngOnDestroy(): void {
        if (this.paramSub) {
            this.paramSub.unsubscribe();
        }
    }

    /**
     * Setter to update the exercise hint content
     * @param newContent New value to set
     */
    updateHintContent(newContent: string) {
        this.exerciseHint.content = newContent;
    }

    /**
     * Navigate to the previous page when the user cancels the update process
     * Returns to the detail page if there is no previous state and we edited an existing hint
     * Returns to the overview page if there is no previous state and we created a new hint
     */
    previousState() {
        this.navigationUtilService.navigateBackWithOptional(
            ['course-management', this.courseId.toString(), 'programming-exercises', this.exercise.id!.toString(), 'hints'],
            this.exerciseHint.id?.toString(),
        );
    }

    /**
     * Saves the exercise hint by creating or updating it on the server
     */
    save() {
        this.isSaving = true;
        if (this.exerciseHint.id !== undefined) {
            this.subscribeToSaveResponse(this.exerciseHintService.update(this.exercise.id!, this.exerciseHint));
        } else {
            this.subscribeToSaveResponse(this.exerciseHintService.create(this.exercise.id!, this.exerciseHint));
        }
    }

    generateDescriptionForCodeHint() {
        if (((this.exerciseHint as CodeHint).solutionEntries?.length ?? 0) === 0) {
            return;
        }
        this.isGeneratingDescription = true;
        this.codeHintService.generateDescriptionForCodeHint(this.exercise.id!, this.exerciseHint.id!).subscribe({
            next: (response) => {
                this.exerciseHint.description = response.body!.description;
                this.exerciseHint.content = response.body!.content;
                this.isGeneratingDescription = false;
            },
            error: (error) => {
                this.isGeneratingDescription = false;
                onError(this.alertService, error);
            },
        });
    }

    protected subscribeToSaveResponse(result: Observable<HttpResponse<ExerciseHint>>) {
        result.subscribe({
            next: () => this.onSaveSuccess(),
            error: () => this.onSaveError(),
        });
    }

    protected onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    protected onSaveError() {
        this.isSaving = false;
    }
}
