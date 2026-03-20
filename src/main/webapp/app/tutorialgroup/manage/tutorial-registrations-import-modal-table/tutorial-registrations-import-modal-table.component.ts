import { Component, input } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';

export type TutorialRegistrationsImportModalTableRow =
    | { login: undefined; registrationNumber: string; markFilledCells: boolean }
    | { login: string; registrationNumber: undefined; markFilledCells: boolean }
    | { login: string; registrationNumber: string; markFilledCells: boolean };

@Component({
    selector: 'jhi-tutorial-registrations-import-modal-table',
    imports: [TranslateDirective],
    templateUrl: './tutorial-registrations-import-modal-table.component.html',
    styleUrl: './tutorial-registrations-import-modal-table.component.scss',
})
export class TutorialRegistrationsImportModalTableComponent {
    rows = input.required<TutorialRegistrationsImportModalTableRow[]>();
}
