import { Component } from '@angular/core';
import { faBan, faSave } from '@fortawesome/free-solid-svg-icons';
import { ButtonType } from 'app/shared/components/button.component';

@Component({
    selector: 'jhi-import-competencies',
    templateUrl: './import-competencies.component.html',
})
export class ImportCompetenciesComponent {
    isLoading = false;

    //Icons
    protected readonly faBan = faBan;
    protected readonly faSave = faSave;
    protected readonly ButtonType = ButtonType;

    constructor() {}

    protected isSubmitPossible() {
        return true;
    }

    protected onSubmit() {}

    protected onCancel() {}
}
