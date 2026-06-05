import { Component, inject, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { SelectButtonModule } from 'primeng/selectbutton';
import { StudentExerciseDevSettingsService } from './student-exercise-dev-settings.service';

@Component({
    selector: 'jhi-student-exercise-dev-settings-modal',
    templateUrl: './student-exercise-dev-settings-modal.component.html',
    imports: [FormsModule, DialogModule, SelectButtonModule],
})
export class StudentExerciseDevSettingsModalComponent {
    readonly visible = input.required<boolean>();
    readonly visibleChange = output<boolean>();

    protected readonly devSettings = inject(StudentExerciseDevSettingsService);

    protected readonly groupSidebarOptions = [
        { label: 'Clickable', value: 'clickable' },
        { label: 'Connected', value: 'connected' },
        { label: 'Select', value: 'select' },
    ];

    protected readonly groupClickOptions = [
        { label: 'Rows page', value: 'rows' },
        { label: 'Tiles page', value: 'tiles' },
        { label: 'Exercise page', value: 'exercise-page' },
    ];

    protected readonly tileStyleOptions = [
        { label: 'Hide', value: 'plain' },
        { label: '1 line', value: 'one-line' },
        { label: '2 lines', value: 'two-lines' },
        { label: '3 lines', value: 'three-lines' },
    ];

    protected readonly tileLayoutOptions = [
        { label: 'Stacked', value: 'stacked' },
        { label: 'Flexbox', value: 'flex' },
    ];
}
