import { UndoManager } from 'brace';
import { AceEditorComponent } from 'app/shared/markdown-editor/ace-editor/ace-editor.component';
import { AfterViewInit, Component, EventEmitter, Input, OnInit, Output, ViewChild, ViewEncapsulation } from '@angular/core';
import { CommitState, EditorState } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { CodeEditorRepositoryFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { LocalStorageService } from 'ngx-webstorage';
import { Course } from 'app/entities/course.model';
import { faCircleNotch, faPlayCircle } from '@fortawesome/free-solid-svg-icons';
import { BuildPlanService } from 'app/exercises/programming/manage/services/build-plan.service';
import { catchError, of, tap } from 'rxjs';
import { BuildPlan } from 'app/entities/build-plan.model';
import { ActivatedRoute } from '@angular/router';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { Submission } from 'app/entities/submission.model';
import { SortService } from 'app/shared/service/sort.service';

@Component({
    selector: 'jhi-build-plan-editor',
    templateUrl: './build-plan-editor.component.html',
    styleUrls: ['./build-plan-editor.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class BuildPlanEditorComponent implements AfterViewInit, OnInit {
    // ToDo: check which of the attributes are really needed

    @ViewChild('editor', { static: true }) editor: AceEditorComponent;
    selectedFile = 'pipeline.groovy';
    buildPlan: BuildPlan;
    exerciseId: number;
    @Input()
    sessionId: number | string;

    @Input()
    readonly commitState: CommitState;
    @Input()
    readonly editorState: EditorState;
    @Input()
    course?: Course;

    @Output()
    onError = new EventEmitter<string>();

    /** Ace Editor Options **/
    isLoading = false;

    tabSize = 4;

    // Icons
    readonly faCircleNotch = faCircleNotch;
    readonly farPlayCircle = faPlayCircle;

    programmingExercise: ProgrammingExercise;
    loadingTemplateParticipationResults = true;
    loadingSolutionParticipationResults = true;

    constructor(
        private repositoryFileService: CodeEditorRepositoryFileService,
        protected localStorageService: LocalStorageService,
        private buildPlanService: BuildPlanService,
        private programmingExerciseService: ProgrammingExerciseService,
        private sortService: SortService,
        private activatedRoute: ActivatedRoute,
    ) {}

    ngOnInit(): void {
        this.exerciseId = this.activatedRoute.snapshot.params.exerciseId;

        this.activatedRoute.data.subscribe(({ exercise }) => {
            this.programmingExercise = exercise;
            this.exerciseId = this.programmingExercise.id!;

            this.loadSolutionAndTemplateParticipation();
        });
    }

    /**
     * @function ngAfterViewInit
     * @desc Sets the theme and other editor options
     */
    ngAfterViewInit(): void {
        this.editor.getEditor().setOptions({
            animatedScroll: true,
            enableBasicAutocompletion: true,
            enableLiveAutocompletion: true,
        });
        this.loadBuildPlan(this.exerciseId);
    }

    // ToDo: the next two methods are copied from `programming-exercise-detail.component.ts`
    // ToDo: check if they can be moved somewhere else to avoid duplication

    private loadSolutionAndTemplateParticipation() {
        this.programmingExerciseService.findWithTemplateAndSolutionParticipation(this.programmingExercise.id!, true).subscribe((updatedProgrammingExercise) => {
            this.programmingExercise = updatedProgrammingExercise.body!;

            // get the latest results for further processing
            if (this.programmingExercise.templateParticipation) {
                const latestTemplateResult = this.getLatestResult(this.programmingExercise.templateParticipation.submissions);
                if (latestTemplateResult) {
                    this.programmingExercise.templateParticipation.results = [latestTemplateResult];
                }
                // This is needed to access the exercise in the result details
                this.programmingExercise.templateParticipation.programmingExercise = this.programmingExercise;
            }
            if (this.programmingExercise.solutionParticipation) {
                const latestSolutionResult = this.getLatestResult(this.programmingExercise.solutionParticipation.submissions);
                if (latestSolutionResult) {
                    this.programmingExercise.solutionParticipation.results = [latestSolutionResult];
                }
                // This is needed to access the exercise in the result details
                this.programmingExercise.solutionParticipation.programmingExercise = this.programmingExercise;
            }

            this.loadingTemplateParticipationResults = false;
            this.loadingSolutionParticipationResults = false;
        });
    }

    private getLatestResult(submissions?: Submission[]) {
        if (submissions && submissions.length > 0) {
            // important: sort to get the latest submission (the order of the server can be random)
            this.sortService.sortByProperty(submissions, 'submissionDate', true);
            const results = submissions.sort().last()?.results;
            if (results && results.length > 0) {
                return results.last();
            }
        }
    }

    /**
     * Fetches the requested file by filename and opens a new editor session for it (if not yet done)
     */
    private loadBuildPlan(exerciseId: number) {
        this.isLoading = true;
        this.buildPlanService
            .getBuildPlan(exerciseId)
            .pipe(
                tap((buildPlanObj) => {
                    this.buildPlan = buildPlanObj.body!;
                    this.initEditor();
                }),
                catchError(() => {
                    return of(null);
                }),
            )
            .subscribe(() => {
                this.isLoading = false;
            });
    }

    private initEditor() {
        this.onBuildPlanUpdate();
        this.editor.getEditor().resize();
        this.editor.getEditor().focus();
        this.editor.getEditor().setShowPrintMargin(false);
        // Reset the undo stack after file change, otherwise the user can undo back to the old file
        this.editor.getEditor().getSession().setUndoManager(new UndoManager());
    }

    private onBuildPlanUpdate() {
        this.editor.getEditor().getSession().setValue(this.buildPlan.buildPlan);
    }

    submit() {
        this.buildPlanService.putBuildPlan(this.exerciseId, this.buildPlan).subscribe((buildPlan) => {
            this.buildPlan = buildPlan.body!;
            this.onBuildPlanUpdate();
        });
    }

    onTextChanged(event: any) {
        this.buildPlan.buildPlan = event as string;
    }

    updateTabSize(tabSize: number) {
        this.tabSize = tabSize;
    }
}
