import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { CompetencyImportComponent } from 'app/course/competencies/competency-management/competency-import.component';
import { CompetencyPagingService } from 'app/course/competencies/competency-paging.service';
import { SortService } from 'app/shared/service/sort.service';

@Component({
    selector: 'jhi-prerequisite-import',
    templateUrl: '../../../shared/import/import.component.html',
})
export class PrerequisiteImportComponent extends CompetencyImportComponent implements OnInit {
    constructor(router: Router, sortService: SortService, activeModal: NgbActiveModal, pagingService: CompetencyPagingService) {
        super(router, sortService, activeModal, pagingService);
        this.entityName = 'competency.prerequisite';
    }
}
