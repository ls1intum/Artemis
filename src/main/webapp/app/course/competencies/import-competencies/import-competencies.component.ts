import { Component } from '@angular/core';
import { faBan, faFileImport, faSave } from '@fortawesome/free-solid-svg-icons';
import { ButtonType } from 'app/shared/components/button.component';

@Component({
    selector: 'jhi-import-competencies',
    templateUrl: './import-competencies.component.html',
})
export class ImportCompetenciesComponent {
    isLoading = false;
    importRelations = true;
    searchTermTitle = '';
    showAdvancedSearch = false;

    //Icons
    protected readonly faBan = faBan;
    protected readonly faSave = faSave;
    protected readonly faFileImport = faFileImport;
    protected readonly ButtonType = ButtonType;

    constructor() {}

    isSubmitPossible() {
        return true;
    }

    onSubmit() {}

    onCancel() {}

    search() {}

    toggleAdvancedSearch() {
        this.showAdvancedSearch = !this.showAdvancedSearch;
    }
}
