import { TumAetUiTableDirective, TumAetUiTableLayoutComponent } from '@tumaet/ui-angular';
import { Component, input } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

export type TutorialRegistrationsImportModalTableRow =
    | { login: undefined; registrationNumber: string; markFilledCells: boolean }
    | { login: string; registrationNumber: undefined; markFilledCells: boolean }
    | { login: string; registrationNumber: string; markFilledCells: boolean };

@Component({
    selector: 'jhi-tutorial-registrations-import-modal-table',
    imports: [TumAetUiTableDirective, TumAetUiTableLayoutComponent, TranslateDirective],
    templateUrl: './tutorial-registrations-import-modal-table.component.html',
})
export class TutorialRegistrationsImportModalTableComponent {
    rows = input.required<TutorialRegistrationsImportModalTableRow[]>();
}
