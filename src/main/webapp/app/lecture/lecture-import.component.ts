import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Lecture } from 'app/entities/lecture.model';
import { LecturePagingService } from 'app/lecture/lecture-paging.service';
import { ImportComponent } from 'app/shared/import/import.component';
import { SortService } from 'app/shared/service/sort.service';

export enum TableColumn {
    ID = 'ID',
    TITLE = 'TITLE',
    COURSE_TITLE = 'COURSE_TITLE',
    SEMESTER = 'SEMESTER',
}

@Component({
    selector: 'jhi-lecture-import',
    templateUrl: './lecture-import.component.html',
})
export class LectureImportComponent extends ImportComponent<Lecture> {
    readonly column = TableColumn;
    constructor(router: Router, pagingService: LecturePagingService, sortService: SortService, activeModal: NgbActiveModal) {
        super(router, pagingService, sortService, activeModal);
    }
    openLectureInNewTab(lecture: Lecture) {
        const url = this.router.serializeUrl(this.router.createUrlTree(['course-management', lecture.course!.id, 'lectures', lecture.id]));
        window.open(url, '_blank');
    }
}
