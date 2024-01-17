import { Component } from '@angular/core';

import { Course } from 'app/entities/course.model';
import { SortService } from 'app/shared/service/sort.service';
import { Router } from '@angular/router';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { CoursePagingService } from 'app/course/course-paging-service';
import { Column, ImportComponent } from 'app/shared/import/import.component';

const tableColumns: Column<Course>[] = [
    {
        name: 'TITLE',
        getProperty(entity: Course) {
            return entity.title;
        },
    },
    {
        name: 'SHORT_NAME',
        getProperty(entity: Course) {
            return entity.shortName;
        },
    },
    {
        name: 'SEMESTER',
        getProperty(entity: Course) {
            return entity.semester;
        },
    },
];

export type ImportAllFromCourseResult = {
    course: Course;
    importRelations: boolean;
};

@Component({
    selector: 'jhi-competency-import',
    templateUrl: './competency-import-course.component.html',
})
export class CompetencyImportCourseComponent extends ImportComponent<Course> {
    //import relations by default
    protected importRelations = true;

    constructor(router: Router, sortService: SortService, activeModal: NgbActiveModal, pagingService: CoursePagingService) {
        super(router, sortService, activeModal, pagingService);
        super.columns = tableColumns;
        super.entityName = 'course';
    }

    override selectImport(item: Course) {
        this.activeModal.close({ course: item, importRelations: this.importRelations } as ImportAllFromCourseResult);
    }
}
