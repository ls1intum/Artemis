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

    protected readonly versionOptions = [
        { label: 'Grouped', value: 'grouped' },
        { label: 'Flat list', value: 'flat' },
    ];

    protected readonly groupHeaderOptions = [
        { label: 'Tile', value: 'card' },
        { label: 'Heading', value: 'label' },
        { label: 'Heading + hint', value: 'label-hint' },
        { label: 'Heading + select', value: 'label-select' },
    ];
}
