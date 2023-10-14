import { Component, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/core/util/alert.service';
import { UpdatingResultComponent } from 'app/exercises/shared/result/updating-result.component';
import { CodeEditorInstructorBaseContainerComponent } from 'app/exercises/programming/manage/code-editor/code-editor-instructor-base-container.component';
import { DomainService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain.service';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/exercises/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';
import { IncludedInOverallScore } from 'app/entities/exercise.model';
import { faCircleNotch, faPlus, faTimes, faTimesCircle } from '@fortawesome/free-solid-svg-icons';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { FileChange, FileChangeType, IrisCodeEditorWebsocketService, IrisExerciseComponent, IrisExerciseComponentChangeSet } from 'app/iris/code-editor-websocket.service';
import { CreateFileChange, FileType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { IrisStateStore } from 'app/iris/state-store.service';

@Component({
    selector: 'jhi-code-editor-instructor',
    templateUrl: './code-editor-instructor-and-editor-container.component.html',
    providers: [IrisCodeEditorWebsocketService, IrisStateStore],
})
export class CodeEditorInstructorAndEditorContainerComponent extends CodeEditorInstructorBaseContainerComponent {
    @ViewChild(UpdatingResultComponent, { static: false }) resultComp: UpdatingResultComponent;
    @ViewChild(ProgrammingExerciseEditableInstructionComponent, { static: false }) editableInstructions: ProgrammingExerciseEditableInstructionComponent;

    readonly IncludedInOverallScore = IncludedInOverallScore;

    // Icons
    faPlus = faPlus;
    faTimes = faTimes;
    faCircleNotch = faCircleNotch;
    faTimesCircle = faTimesCircle;

    constructor(
        router: Router,
        exerciseService: ProgrammingExerciseService,
        courseExerciseService: CourseExerciseService,
        domainService: DomainService,
        programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
        location: Location,
        participationService: ParticipationService,
        translateService: TranslateService,
        route: ActivatedRoute,
        alertService: AlertService,
        private codeEditorWebsocketService: IrisCodeEditorWebsocketService,
    ) {
        super(router, exerciseService, courseExerciseService, domainService, programmingExerciseParticipationService, location, participationService, route, alertService);
        codeEditorWebsocketService.onCodeChanges().subscribe((changes: IrisExerciseComponentChangeSet) => this.applyChanges(changes));
    }

    onResizeEditorInstructions() {
        if (this.editableInstructions.markdownEditor && this.editableInstructions.markdownEditor.aceEditorContainer) {
            this.editableInstructions.markdownEditor.aceEditorContainer.getEditor().resize();
        }
    }

    private applyChanges(componentChanges: IrisExerciseComponentChangeSet) {
        switch (componentChanges.component) {
            case IrisExerciseComponent.PROBLEM_STATEMENT:
                componentChanges.changes.forEach((change) => {
                    if (change.type === FileChangeType.MODIFY) {
                        const psContent = this.editableInstructions.programmingExercise.problemStatement;
                        psContent?.replace(change.original!, change.updated!);
                        this.editableInstructions.updateProblemStatement(psContent!);
                    }
                });
                break;
            case IrisExerciseComponent.SOLUTION_REPOSITORY:
                //this.selectedRepository = REPOSITORY.SOLUTION;
                this.applyCodeChange(componentChanges.changes);
                break;
            case IrisExerciseComponent.TEMPLATE_REPOSITORY:
                //this.selectedRepository = REPOSITORY.TEMPLATE;
                this.applyCodeChange(componentChanges.changes);
                break;
            case IrisExerciseComponent.TEST_REPOSITORY:
                //this.selectedRepository = REPOSITORY.TEST;
                this.applyCodeChange(componentChanges.changes);
                break;
        }
    }

    private applyCodeChange(changes: FileChange[]) {
        changes.forEach((change) => {
            if (change.type === FileChangeType.MODIFY) {
                const fileContent = this.codeEditorContainer.aceEditor.getFileContent(change.file!);
                fileContent.replace(change.original!, change.updated!);
                this.codeEditorContainer.aceEditor.updateFileText(change.file!, fileContent).then((file) => console.log(file));
            }
            if (change.type === FileChangeType.CREATE) {
                const fileChange = new CreateFileChange(FileType.FILE, change.file!);
                this.codeEditorContainer.onFileChange([[change.updated!], fileChange]);
                //aceEditor needs this.selectedFile === fileChange.fileName
            }
        });
    }
}
