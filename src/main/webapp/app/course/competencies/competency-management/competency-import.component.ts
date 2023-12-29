import { Component, Input } from '@angular/core';
import { Router } from '@angular/router';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { CompetencyPagingService } from 'app/course/competencies/competency-paging.service';
import { Competency } from 'app/entities/competency.model';
import { ImportComponent } from 'app/shared/import/import.component';
import { SortService } from 'app/shared/service/sort.service';

@Component({
    selector: 'jhi-competency-import',
    templateUrl: './competency-import.component.html',
})
export class CompetencyImportComponent extends ImportComponent<Competency> {
    @Input() public disabledIds: number[];

    constructor(router: Router, pagingService: CompetencyPagingService, sortService: SortService, activeModal: NgbActiveModal) {
        super(router, pagingService, sortService, activeModal);
    }
}
