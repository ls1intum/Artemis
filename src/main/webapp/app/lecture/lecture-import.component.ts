import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Lecture } from 'app/entities/lecture.model';
import { LecturePagingService } from 'app/lecture/lecture-paging.service';
import { Column, ImportComponent } from 'app/shared/import/import.component';
import { SortService } from 'app/shared/service/sort.service';

const tableColumns: Column<Lecture>[] = [
    {
        name: 'TITLE',
        getProperty(entity: Lecture) {
            return entity.title;
        },
    },
    {
        name: 'COURSE_TITLE',
        getProperty(entity: Lecture) {
            return entity.course?.title;
        },
    },
    {
        name: 'SEMESTER',
        getProperty(entity: Lecture) {
            return entity.course?.semester;
        },
    },
];

@Component({
    selector: 'jhi-lecture-import',
    templateUrl: '../shared/import/import.component.html',
})
export class LectureImportComponent extends ImportComponent<Lecture> {
    constructor(router: Router, sortService: SortService, activeModal: NgbActiveModal, pagingService: LecturePagingService) {
        super(router, sortService, activeModal, pagingService);
        super.columns = tableColumns;
        super.entityName = 'lecture';
    }
}
