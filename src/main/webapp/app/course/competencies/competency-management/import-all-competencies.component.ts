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
    selector: 'jhi-import-all-competencies',
    templateUrl: './import-all-competencies.component.html',
})
export class ImportAllCompetenciesComponent extends ImportComponent<CourseForImportDTO> {
    //import relations by default
    protected importRelations = true;

    constructor(router: Router, sortService: SortService, activeModal: NgbActiveModal, pagingService: CourseForImportDTOPagingService) {
        super(router, sortService, activeModal, pagingService);
        this.columns = tableColumns;
    }

    /**
     * Closes the modal in which the import component is opened. Returns the selected item **and if relations should be imported**
     *
     * @param item The item which was selected by the user for the import.
     */
    override selectImport(item: CourseForImportDTO) {
        this.activeModal.close({ courseForImportDTO: item, importRelations: this.importRelations } as ImportAllFromCourseResult);
    }
}
