import { Component, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/core/util/alert.service';
import { UpdatingResultComponent } from 'app/exercises/shared/result/updating-result.component';
import { CodeEditorInstructorBaseContainerComponent, REPOSITORY } from 'app/exercises/programming/manage/code-editor/code-editor-instructor-base-container.component';
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
    // Buffer for changes to repositories that are not currently open in the UI
    private bufferedRepositoryChanges = new Map<REPOSITORY, FileChange[]>();

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
        codeEditorWebsocketService.onCodeChanges().subscribe((changes: IrisExerciseComponentChangeSet) => this.handleIrisChangeSet(changes));
    }

    onResizeEditorInstructions() {
        if (this.editableInstructions.markdownEditor && this.editableInstructions.markdownEditor.aceEditorContainer) {
            this.editableInstructions.markdownEditor.aceEditorContainer.getEditor().resize();
        }
    }

    /**
     * Accept the changes suggested by Iris.
     * Changes to the problem statement or to the repository currently open in the UI are applied immediately.
     * Changes to other repositories are buffered and applied when the user switches to the corresponding repository.
     * @param changeSet
     * @private
     */
    private handleIrisChangeSet(changeSet: IrisExerciseComponentChangeSet) {
        if (changeSet.component === IrisExerciseComponent.PROBLEM_STATEMENT) {
            changeSet.changes.forEach((change: FileChange) => {
                if (change.type === FileChangeType.MODIFY) {
                    const psContent = this.editableInstructions.programmingExercise.problemStatement;
                    psContent?.replace(change.original!, change.updated!);
                    this.editableInstructions.updateProblemStatement(psContent!);
                }
            });
            return;
        }
        const targetRepo = this.toRepository(changeSet.component);
        if (targetRepo === this.selectedRepository) {
            this.applyCodeChange(changeSet.changes); // Apply changes immediately
        } else {
            const alreadyBufferedChanges = this.bufferedRepositoryChanges.get(targetRepo) || [];
            alreadyBufferedChanges.push(...changeSet.changes);
            this.bufferedRepositoryChanges.set(targetRepo, alreadyBufferedChanges);
        }
    }

    /**
     * Convert the given component to the corresponding repository.
     * Throws an error if the component is the problem statement.
     * @param component The component to convert
     */
    private toRepository(component: IrisExerciseComponent) {
        switch (component) {
            case IrisExerciseComponent.SOLUTION_REPOSITORY:
                return REPOSITORY.SOLUTION;
            case IrisExerciseComponent.TEMPLATE_REPOSITORY:
                return REPOSITORY.TEMPLATE;
            case IrisExerciseComponent.TEST_REPOSITORY:
                return REPOSITORY.TEST;
            default:
                throw new Error('Invalid component');
        }
    }

    /**
     * Called when the user switches to another repository. Applies any buffered changes to the new repository.
     */
    protected onRepositoryChanged() {
        this.applyBufferedChangesToCurrentRepo();
    }

    /**
     * Apply any changes to this repository that have been buffered while the user was working on another repository.
     * Does nothing if there are no buffered changes.
     */
    private applyBufferedChangesToCurrentRepo() {
        const changesToApply = this.bufferedRepositoryChanges.get(this.selectedRepository);
        if (!changesToApply) return;
        this.bufferedRepositoryChanges.delete(this.selectedRepository);
        this.applyCodeChange(changesToApply);
    }

    /**
     * Apply the given changes to the current repository (i.e. the one that is currently selected in the Ace Editor).
     * @param changes The changes to apply
     */
    private applyCodeChange(changes: FileChange[]) {
        changes.forEach((change) => {
            if (change.type === FileChangeType.MODIFY) {
                const fileContent = this.codeEditorContainer.aceEditor.getFileContent(change.file!);
                fileContent.replace(change.original!, change.updated!);
                this.codeEditorContainer.aceEditor.updateFileText(change.file!, fileContent).then((file) => console.log(file));
            }
            if (change.type === FileChangeType.CREATE) {
                const fileChange = new CreateFileChange(FileType.FILE, change.file!);
                //aceEditor needs this.selectedFile === fileChange.fileName
                this.codeEditorContainer.selectedFile = fileChange.fileName;
                this.codeEditorContainer.onFileChange([[change.updated!], fileChange]);
            }
        });
    }
}
