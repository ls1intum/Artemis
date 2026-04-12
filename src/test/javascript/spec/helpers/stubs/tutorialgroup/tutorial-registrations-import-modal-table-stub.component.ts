import { Component, input } from '@angular/core';
import { TutorialRegistrationsImportModalTableRow } from 'app/tutorialgroup/manage/tutorial-registrations-import-modal-table/tutorial-registrations-import-modal-table.component';

@Component({
    selector: 'jhi-tutorial-registrations-import-modal-table',
    template: '',
})
export class TutorialRegistrationsImportModalTableStubComponent {
    rows = input.required<TutorialRegistrationsImportModalTableRow[]>();
}
