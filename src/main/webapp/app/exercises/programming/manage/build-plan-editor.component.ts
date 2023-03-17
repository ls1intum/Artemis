import { UndoManager } from 'brace';
import 'brace/ext/language_tools';
import 'brace/ext/modelist';
import 'brace/mode/java';
import 'brace/mode/markdown';
import 'brace/mode/haskell';
import 'brace/mode/ocaml';
import 'brace/mode/c_cpp';
import 'brace/mode/python';
import 'brace/mode/swift';
import 'brace/mode/yaml';
import 'brace/mode/makefile';
import 'brace/mode/kotlin';
import 'brace/mode/assembly_x86';
import 'brace/mode/vhdl';
import 'brace/theme/dreamweaver';
import 'brace/theme/dracula';
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

@Component({
    selector: 'jhi-build-plan-editor',
    templateUrl: './build-plan-editor.component.html',
    styleUrls: ['./build-plan-editor.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class BuildPlanEditorComponent implements AfterViewInit, OnInit {
    @ViewChild('editor', { static: true }) editor: AceEditorComponent;
    selectedFile = 'pipeline.groovy';
    buildPlan: BuildPlan;
    buildPlanId: number;
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
    @Output()
    onFileLoad = new EventEmitter<string>();

    /** Ace Editor Options **/
    isLoading = false;
    fileSession: { [id: number]: { code: string; cursor: { column: number; row: number } } } = {};

    tabSize = 4;

    // Icons
    readonly faCircleNotch = faCircleNotch;
    readonly farPlayCircle = faPlayCircle;

    constructor(
        private repositoryFileService: CodeEditorRepositoryFileService,
        protected localStorageService: LocalStorageService,
        private buildPlanService: BuildPlanService,
        private activatedRoute: ActivatedRoute,
    ) {}

    ngOnInit(): void {
        this.exerciseId = this.activatedRoute.snapshot.params.exerciseId;
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

    /**
     * Fetches the requested file by filename and opens a new editor session for it (if not yet done)
     */
    loadBuildPlan(exerciseId: number) {
        this.isLoading = true;
        this.exerciseId = exerciseId;
        this.buildPlanService
            .getBuildPlan(exerciseId)
            .pipe(
                tap((buildPlanObj) => {
                    if (buildPlanObj.body !== null && buildPlanObj.body.buildPlan !== undefined && buildPlanObj.body.id !== undefined) {
                        this.buildPlan = buildPlanObj.body;
                        this.buildPlanId = buildPlanObj.body.id;
                        this.fileSession[buildPlanObj.body.id] = { code: buildPlanObj.body.buildPlan, cursor: { column: 0, row: 0 } };
                        this.initEditor();
                    } else {
                        throw new Error('No build plan found.');
                    }
                }),
                catchError(() => {
                    return of(null);
                }),
            )
            .subscribe(() => {
                this.isLoading = false;
            });
    }

    initEditor() {
        this.editor.getEditor().getSession().setValue(this.fileSession[this.buildPlanId].code);
        this.editor.getEditor().resize();
        this.editor.getEditor().focus();
        this.editor.getEditor().setShowPrintMargin(false);
        // Reset the undo stack after file change, otherwise the user can undo back to the old file
        this.editor.getEditor().getSession().setUndoManager(new UndoManager());

        this.onFileLoad.emit(this.selectedFile);
    }

    submit() {
        const buildPlanToSave = new BuildPlan();
        buildPlanToSave.id = this.buildPlanId;
        buildPlanToSave.buildPlan = this.fileSession[this.buildPlanId].code;
        buildPlanToSave.programmingExercises = this.buildPlan.programmingExercises;
        this.buildPlanService.putBuildPlan(this.exerciseId, buildPlanToSave).subscribe(() => {
            console.log(buildPlanToSave.buildPlan);
        });
    }

    onTextChanged(event: any) {
        const code = event as string;
        const cursor = this.editor.getEditor().getCursorPosition();
        this.fileSession[this.buildPlanId] = { code, cursor };
    }

    updateTabSize(tabSize: number) {
        this.tabSize = tabSize;
    }
}
