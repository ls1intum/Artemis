import { Component, input, output } from '@angular/core';
import { faHandshakeAngle } from '@fortawesome/free-solid-svg-icons';

import { ButtonComponent, ButtonSize, ButtonType } from 'app/shared/components/button/button.component';

@Component({
    selector: 'jhi-switch-edit-mode-button',
    templateUrl: './switch-edit-mode-button.component.html',
    imports: [ButtonComponent],
})
export class SwitchEditModeButtonComponent {
    protected readonly faHandShakeAngle = faHandshakeAngle;
    protected readonly ButtonType = ButtonType;

    switchEditMode = output<void>();
    isSimpleMode = input.required<boolean>();
    buttonSize = input<ButtonSize>(ButtonSize.MEDIUM);
    disabled = input<boolean>(true);

    protected toggleEditMode(): void {
        this.switchEditMode.emit();
    }
}
