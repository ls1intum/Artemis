import { AceEditorComponent } from 'app/shared/markdown-editor/ace-editor/ace-editor.component';
import { AfterViewInit, Component, OnInit, ViewChild, ViewEncapsulation } from '@angular/core';
import { faCircleNotch, faPlayCircle } from '@fortawesome/free-solid-svg-icons';
import { BuildPlanService } from 'app/exercises/programming/manage/services/build-plan.service';
import { BuildPlan } from 'app/entities/build-plan.model';
import { ActivatedRoute } from '@angular/router';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';

@Component({
    selector: 'jhi-build-plan-editor',
    templateUrl: './build-plan-editor.component.html',
    styleUrls: ['./build-plan-editor.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class BuildPlanEditorComponent implements AfterViewInit, OnInit {
    @ViewChild('editor', { static: true })
    editor: AceEditorComponent;

    /** Ace Editor Options **/
    isLoading = false;

    tabSize = 4;

    // Icons
    readonly faCircleNotch = faCircleNotch;
    readonly farPlayCircle = faPlayCircle;

    exerciseId: number;
    programmingExercise: ProgrammingExercise;
    loadingResults = true;
    selectedFile = 'pipeline.groovy';
    buildPlan: BuildPlan;

    constructor(private buildPlanService: BuildPlanService, private programmingExerciseService: ProgrammingExerciseService, private activatedRoute: ActivatedRoute) {}

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
        this.editor.getEditor().setOptions({
            animatedScroll: true,
            enableBasicAutocompletion: true,
            enableLiveAutocompletion: true,
        });
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
        this.buildPlanService.getBuildPlan(exerciseId).subscribe((buildPlan) => {
            this.buildPlan = buildPlan.body!;
            this.initEditor();
            this.isLoading = false;
        });
    }

    private initEditor() {
        this.onBuildPlanUpdate();
        this.editor.getEditor().resize();
        this.editor.getEditor().focus();
        this.editor.getEditor().setShowPrintMargin(false);
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
