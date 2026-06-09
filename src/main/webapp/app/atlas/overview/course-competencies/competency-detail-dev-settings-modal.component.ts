import { Component, inject, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { SelectButtonModule } from 'primeng/selectbutton';
import { CompetencyDetailDevSettingsService } from './competency-detail-dev-settings.service';

@Component({
    selector: 'jhi-competency-detail-dev-settings-modal',
    templateUrl: './competency-detail-dev-settings-modal.component.html',
    imports: [FormsModule, DialogModule, SelectButtonModule],
})
export class CompetencyDetailDevSettingsModalComponent {
    readonly visible = input.required<boolean>();
    readonly visibleChange = output<boolean>();

    protected readonly devSettings = inject(CompetencyDetailDevSettingsService);

    protected readonly exerciseViewOptions = [
        { label: 'Default', value: 'default' },
        { label: 'Group Card', value: 'group-card' },
        { label: 'Group Card v2', value: 'group-card-v2' },
        { label: 'Grouped', value: 'grouped' },
    ];
}
