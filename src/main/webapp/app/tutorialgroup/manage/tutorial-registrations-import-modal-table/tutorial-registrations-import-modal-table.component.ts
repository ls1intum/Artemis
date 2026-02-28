import { Component, input } from '@angular/core';

export interface TutorialRegistrationsImportModalTableRow {
    login?: string;
    registrationNumber?: string;
    markFilledCells: boolean;
}

@Component({
    selector: 'jhi-tutorial-registrations-import-modal-table',
    imports: [],
    templateUrl: './tutorial-registrations-import-modal-table.component.html',
    styleUrl: './tutorial-registrations-import-modal-table.component.scss',
})
export class TutorialRegistrationsImportModalTableComponent {
    rows = input.required<TutorialRegistrationsImportModalTableRow[]>();
}
