import { Component, HostListener, OnInit } from '@angular/core';
import { faBan, faFileImport, faSave } from '@fortawesome/free-solid-svg-icons';
import { ButtonType } from 'app/shared/components/button.component';
import { CompetencyFilter } from 'app/course/competencies/import-competencies/competency-search.component';
import { ActivatedRoute, Router } from '@angular/router';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { TranslateService } from '@ngx-translate/core';
import { CompetencyService } from 'app/course/competencies/competency.service';

@Component({
    selector: 'jhi-import-competencies',
    templateUrl: './import-competencies.component.html',
})
export class ImportCompetenciesComponent implements OnInit, ComponentCanDeactivate {
    isLoading = false;
    importRelations = true;
    showAdvancedSearch = false;
    courseId: number;

    //Icons
    protected readonly faBan = faBan;
    protected readonly faSave = faSave;
    protected readonly faFileImport = faFileImport;
    //Other constants
    protected readonly ButtonType = ButtonType;

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private translateService: TranslateService,
        private competencyService: CompetencyService,
    ) {}

    ngOnInit(): void {
        this.activatedRoute.params.subscribe((params) => {
            this.courseId = Number(params['courseId']);
        });
    }

    search(filter: CompetencyFilter) {
        console.log(filter);
        //TODO: service call to get competencies.
    }

    isSubmitPossible() {
        //TODO: submit will be possible if: at least 1 competency is selected.
        return true;
    }

    onSubmit() {
        //TODO: service call to save.
    }

    /**
     * Cancels the import and navigates back
     */
    onCancel() {
        this.router.navigate(['../'], { relativeTo: this.activatedRoute });
    }

    canDeactivate() {
        //TODO: implement canDeactivate logic. > if loading || competencies selected || search input(?)
        return true;
    }

    get canDeactivateWarning(): string {
        return this.translateService.instant('pendingChanges');
    }

    /**
     * Displays the alert for confirming refreshing or closing the page if there are unsaved changes
     */
    @HostListener('window:beforeunload', ['$event'])
    unloadNotification(event: any) {
        if (!this.canDeactivate()) {
            event.returnValue = this.canDeactivateWarning;
        }
    }
}
