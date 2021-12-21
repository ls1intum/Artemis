import { Component } from '@angular/core';
import { faInfoCircle } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-code-editor-repository-is-locked',
    template: `
        <span id="repository-locked-warning" class="badge bg-warning d-flex align-items-center locked-container">
            <fa-icon [icon]="faInfoCircle" class="text-white" size="2x"></fa-icon>
            <span
                class="ms-2 locked-lable"
                jhiTranslate="artemisApp.programmingExercise.repositoryIsLocked.title"
                ngbTooltip="{{ 'artemisApp.programmingExercise.repositoryIsLocked.tooltip' | artemisTranslate }}"
            >
                The due date has passed, your repository is locked. You can still read the code but not make any changes to it.
            </span>
        </span>
    `,
    styles: ['.locked-lable {font-size: 1.2rem; color: white}'],
})
export class CodeEditorRepositoryIsLockedComponent {
    // Icons
    faInfoCircle = faInfoCircle;
}
