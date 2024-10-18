import { Component, input, output } from '@angular/core';
import { faHandshakeAngle } from '@fortawesome/free-solid-svg-icons';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

@Component({
    selector: 'jhi-switch-edit-mode-button',
    standalone: true,
    templateUrl: './switch-edit-mode-button.component.html',
    imports: [ArtemisSharedCommonModule, ArtemisSharedComponentModule],
})
export class SwitchEditModeButtonComponent {
    protected readonly faHandShakeAngle = faHandshakeAngle;
    protected readonly ButtonType = ButtonType;

    switchEditMode = output<void>();
    isSimpleMode = input.required<boolean>();
    buttonSize = input<ButtonSize>(ButtonSize.MEDIUM);

    protected toggleEditMode(): void {
        this.switchEditMode.emit();
    }
}
