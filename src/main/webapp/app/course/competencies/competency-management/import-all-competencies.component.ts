import { Component, Input, inject } from '@angular/core';
import { Course, CourseForImportDTO } from 'app/entities/course.model';
import { Column, ImportComponent } from 'app/shared/import/import.component';

import { CourseCompetencyType } from 'app/entities/competency.model';
import { NgbPagination, NgbTypeaheadModule } from '@ng-bootstrap/ng-bootstrap';
import { CourseForImportDTOPagingService } from 'app/course/course-for-import-dto-paging-service';
import { ButtonComponent } from 'app/shared/components/button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';

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
    imports: [
        NgbPagination,
        ButtonComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        SortByDirective,
        SortDirective,
        FontAwesomeModule,
        FormsModule,
        CommonModule,
        NgbTypeaheadModule,
    ],
})
export class ImportAllCompetenciesComponent extends ImportComponent<CourseForImportDTO> {
    //import relations by default
    protected importRelations = true;

    @Input() public competencyType: CourseCompetencyType | 'courseCompetency' = CourseCompetencyType.COMPETENCY;

    constructor() {
        const pagingService = inject(CourseForImportDTOPagingService);
        super(pagingService);
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
