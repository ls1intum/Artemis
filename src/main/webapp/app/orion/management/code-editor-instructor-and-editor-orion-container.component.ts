import { Component, OnInit, inject } from '@angular/core';
import { CodeEditorInstructorBaseContainerComponent } from 'app/programming/manage/code-editor/code-editor-instructor-base-container.component';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { OrionBuildAndTestService } from 'app/shared/orion/orion-build-and-test.service';
import { OrionState } from 'app/shared/orion/orion';
import { faCircleNotch, faTimesCircle } from '@fortawesome/free-solid-svg-icons';

import { MarkdownEditorHeight } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { UpdatingResultComponent } from '../../exercise/result/updating-result.component';
import { ProgrammingExerciseInstructorExerciseStatusComponent } from 'app/programming/manage/status/programming-exercise-instructor-exercise-status.component';
import { NgbDropdown, NgbDropdownButtonItem, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseStudentTriggerBuildButtonComponent } from 'app/programming/shared/actions/programming-exercise-student-trigger-build-button.component';
import { OrionButtonComponent } from 'app/shared/orion/orion-button/orion-button.component';
import { ProgrammingExerciseEditableInstructionComponent } from '../../programming/manage/instructions-editor/programming-exercise-editable-instruction.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { RepositoryType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';

@Component({
    selector: 'jhi-code-editor-instructor-orion',
    templateUrl: './code-editor-instructor-and-editor-orion-container.component.html',
    styles: ['.instructions-orion { height: 700px }'],
    imports: [
        FaIconComponent,
        TranslateDirective,
        UpdatingResultComponent,
        ProgrammingExerciseInstructorExerciseStatusComponent,
        NgbDropdown,
        NgbDropdownToggle,
        NgbDropdownMenu,
        NgbDropdownButtonItem,
        NgbDropdownItem,
        ProgrammingExerciseStudentTriggerBuildButtonComponent,
        OrionButtonComponent,
        ProgrammingExerciseEditableInstructionComponent,
        ArtemisTranslatePipe,
    ],
})
export class CodeEditorInstructorAndEditorOrionContainerComponent extends CodeEditorInstructorBaseContainerComponent implements OnInit {
    private orionConnectorService = inject(OrionConnectorService);
    private orionBuildAndTestService = inject(OrionBuildAndTestService);

    orionState: OrionState;
    // Icons
    faCircleNotch = faCircleNotch;
    faTimesCircle = faTimesCircle;

    protected readonly MarkdownEditorHeight = MarkdownEditorHeight;
    protected readonly RepositoryType = RepositoryType;

    /**
     * Calls ngOnInit of its superclass and initialize the subscription to
     * the Orion connector service, on component initialization
     */
    ngOnInit(): void {
        super.ngOnInit();
        if (this.orionConnectorService && this.orionConnectorService.state()) {
            this.orionConnectorService.state().subscribe((state) => (this.orionState = state));
        }
    }

    protected applyDomainChange(domainType: any, domainValue: any) {
        super.applyDomainChange(domainType, domainValue);
        this.orionConnectorService.selectRepository(this.selectedRepository);
    }

    /**
     * Submits the code of the selected repository and tells Orion to listen to any new test results for the selected repo.
     * Submitting means committing all changes and pushing them to the remote.
     */
    submit(): void {
        this.orionConnectorService.submit();
        if (this.selectedRepository !== RepositoryType.TESTS) {
            this.orionConnectorService.isBuilding(true);
            this.orionBuildAndTestService.listenOnBuildOutputAndForwardChanges(this.exercise, this.selectedParticipation);
        }
    }

    /**
     * Tells Orion to build and test the selected repository locally instead of committing and pushing the code to the remote
     */
    buildLocally(): void {
        this.orionConnectorService.isBuilding(true);
        this.orionConnectorService.buildAndTestLocally();
    }
}
