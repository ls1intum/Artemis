import { Component, inject, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { SelectButtonModule } from 'primeng/selectbutton';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { ExerciseManagementDevSettingsService } from './exercise-management-dev-settings.service';

@Component({
    selector: 'jhi-exercise-management-dev-settings-modal',
    templateUrl: './exercise-management-dev-settings-modal.component.html',
    imports: [FormsModule, DialogModule, SelectButtonModule, ToggleSwitchModule],
})
export class ExerciseManagementDevSettingsModalComponent {
    readonly visible = input.required<boolean>();
    readonly visibleChange = output<boolean>();

    protected readonly devSettings = inject(ExerciseManagementDevSettingsService);

    protected readonly exerciseRowOptions = [
        { label: 'Compact', value: 'compact' },
        { label: 'Columnar', value: 'columnar' },
        { label: 'Table', value: 'table' },
    ];

    protected readonly addExerciseOptions = [
        { label: 'None', value: 'none' },
        { label: 'Inline', value: 'inline' },
        { label: 'Slim', value: 'slim' },
        { label: 'Modal (split)', value: 'modal-split' },
        { label: 'Modal (unified)', value: 'modal-unified' },
    ];

    protected readonly actionButtonOptions = [
        { label: 'Icon only', value: 'icon-only' },
        { label: 'Text + Icon', value: 'text-and-icon' },
        { label: 'Ellipsis', value: 'ellipsis' },
    ];
}
