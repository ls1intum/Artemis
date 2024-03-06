import { Component, EventEmitter, Output } from '@angular/core';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { faArrowsLeftRight } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-programming-exercise-switch-editor-button',
    templateUrl: 'programming-exercise-switch-editor-button.component.html',
})
export class ProgrammingExerciseSwitchEditorButtonComponent {
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;
    faArrowsLeftRight = faArrowsLeftRight;

    usingMonaco: boolean = false;

    @Output()
    usingMonacoChange = new EventEmitter<boolean>();

    toggle() {
        this.usingMonaco = !this.usingMonaco;
        this.usingMonacoChange.emit(this.usingMonaco);
    }
}
