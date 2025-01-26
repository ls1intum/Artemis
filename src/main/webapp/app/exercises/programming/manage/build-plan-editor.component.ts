import { AfterViewInit, Component, OnInit, ViewChild, ViewEncapsulation, inject } from '@angular/core';
import { faCircleNotch, faPlayCircle } from '@fortawesome/free-solid-svg-icons';
import { UpdatingResultComponent } from 'app/exercises/shared/result/updating-result.component';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { BuildPlanService } from 'app/exercises/programming/manage/services/build-plan.service';
import { BuildPlan } from 'app/entities/programming/build-plan.model';
import { ActivatedRoute } from '@angular/router';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CodeEditorHeaderComponent } from '../shared/code-editor/header/code-editor-header.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-build-plan-editor',
    templateUrl: './build-plan-editor.component.html',
    styleUrls: ['./build-plan-editor.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [TranslateDirective, UpdatingResultComponent, NgbTooltip, FaIconComponent, CodeEditorHeaderComponent, MonacoEditorComponent, ArtemisTranslatePipe],
})
export class BuildPlanEditorComponent implements AfterViewInit, OnInit {
    private buildPlanService = inject(BuildPlanService);
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private alertService = inject(AlertService);
    private activatedRoute = inject(ActivatedRoute);

    // Icons
    readonly faCircleNotch = faCircleNotch;
    readonly farPlayCircle = faPlayCircle;

    @ViewChild('editor', { static: true })
    editor: MonacoEditorComponent;
    isLoading = false;

    loadingResults = true;
    exerciseId: number;
    programmingExercise: ProgrammingExercise;
    buildPlan: BuildPlan | undefined;

    /**
     * @function ngOnInit
     * Sets the exercise and corresponding build plan based on the exerciseId in the current URL.
     */
    ngOnInit(): void {
        this.activatedRoute.data.subscribe(({ exercise }) => {
            this.programmingExercise = exercise;
            this.loadSolutionAndTemplateParticipation();
        });
    }

    /**
     * @function ngAfterViewInit
     * @desc Sets the theme and other editor options
     */
    ngAfterViewInit(): void {
        this.loadBuildPlan(this.activatedRoute.snapshot.params.exerciseId);
    }

    private loadSolutionAndTemplateParticipation() {
        this.programmingExerciseService.findWithTemplateAndSolutionParticipationAndLatestResults(this.programmingExercise.id!).subscribe((response) => {
            this.programmingExercise = response.body!;
            this.exerciseId = this.programmingExercise.id!;

            this.loadingResults = false;
        });
    }

    /**
     * Fetches the requested file by filename and opens a new editor session for it (if not yet done)
     */
    private loadBuildPlan(exerciseId: number) {
        this.isLoading = true;
        this.buildPlanService.getBuildPlan(exerciseId).subscribe({
            next: (buildPlan) => {
                this.buildPlan = buildPlan.body!;
                this.initEditor();
                this.isLoading = false;
            },
            error: (error) => {
                this.buildPlan = undefined;
                this.isLoading = false;

                if (error.status == 404) {
                    this.alertService.error('artemisApp.programmingExercise.buildPlanFetchError');
                } else {
                    onError(this.alertService, error);
                }
            },
        });
    }

    private initEditor() {
        this.onBuildPlanUpdate();
        this.editor.layout();
    }

    private onBuildPlanUpdate() {
        const text = this.buildPlan?.buildPlan ?? '';
        this.editor.setText(text);
    }

    /**
     * @function submit
     * Replaces the initial build plan with the current content of the editor window, but only if a build plan is
     * currently loaded.
     */
    submit() {
        if (!this.buildPlan) {
            return;
        }

        this.buildPlanService.putBuildPlan(this.exerciseId, this.buildPlan).subscribe((buildPlan) => {
            this.buildPlan = buildPlan.body!;
            this.onBuildPlanUpdate();
        });
    }

    /**
     * @function onTextChanged
     * Replaces the build plan with the current text from the editor window.
     * @param event The text inside the editor window.
     */
    onTextChanged(event: any) {
        this.buildPlan!.buildPlan = event as string;
    }

    /**
     * Updates the tab size of the editor.
     * @param tabSize The new tab size.
     */
    updateTabSize(tabSize: number) {
        this.editor.updateModelIndentationSize(tabSize);
    }
}
