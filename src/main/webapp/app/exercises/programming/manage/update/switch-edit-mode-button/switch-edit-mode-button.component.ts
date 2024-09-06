import { Component, Input, input } from '@angular/core';
import { faHandshakeAngle } from '@fortawesome/free-solid-svg-icons';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

@Component({
    selector: 'jhi-switch-edit-mode-button',
    standalone: true,
    templateUrl: './switch-edit-mode-button.component.html',
    imports: [ArtemisSharedCommonModule],
})
export class SwitchEditModeButtonComponent {
    protected readonly faHandShakeAngle = faHandshakeAngle;

    isSimpleMode = input.required<boolean>();
    @Input() switchEditMode: () => void;
}
