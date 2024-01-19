import { Component } from '@angular/core';

import { Course, CourseForImportDTO } from 'app/entities/course.model';
import { SortService } from 'app/shared/service/sort.service';
import { Router } from '@angular/router';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { CourseForImportDTOPagingService } from 'app/course/course-for-import-dto-paging-service';
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
    courseForImportDTO: CourseForImportDTO;
    importRelations: boolean;
};

@Component({
    selector: 'jhi-competency-import',
    templateUrl: './competency-import-course.component.html',
})
export class CompetencyImportCourseComponent extends ImportComponent<CourseForImportDTO> {
    //import relations by default
    protected importRelations = true;

    constructor(router: Router, sortService: SortService, activeModal: NgbActiveModal, pagingService: CourseForImportDTOPagingService) {
        super(router, sortService, activeModal, pagingService);
        super.columns = tableColumns;
    }

    override selectImport(item: CourseForImportDTO) {
        this.activeModal.close({ courseForImportDTO: item, importRelations: this.importRelations } as ImportAllFromCourseResult);
    }
}
