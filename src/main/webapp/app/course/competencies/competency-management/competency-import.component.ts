import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { CompetencyPagingService } from 'app/course/competencies/competency-paging.service';
import { Competency } from 'app/entities/competency.model';
import { Column, ImportComponent } from 'app/shared/import/import.component';
import { SortService } from 'app/shared/service/sort.service';

const tableColumns: Column<Competency>[] = [
    {
        name: 'TITLE',
        getProperty(entity: Competency) {
            return entity.title;
        },
    },
    {
        name: 'COURSE_TITLE',
        getProperty(entity: Competency) {
            return entity.course?.title;
        },
    },
    {
        name: 'SEMESTER',
        getProperty(entity: Competency) {
            return entity.course?.semester;
        },
    },
];

@Component({
    selector: 'jhi-competency-import',
    templateUrl: '../../../shared/import/import.component.html',
})
export class CompetencyImportComponent extends ImportComponent<Competency> {
    constructor(router: Router, sortService: SortService, activeModal: NgbActiveModal, pagingService: CompetencyPagingService) {
        super(router, sortService, activeModal, pagingService);
        super.columns = tableColumns;
        super.entityName = 'competency';
    }
}
