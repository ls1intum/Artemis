import { Component } from '@angular/core';
import { Lecture } from 'app/entities/lecture.model';
import { Column, ImportComponent } from 'app/shared/import/import.component';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from '../shared/language/translate.directive';
import { SortDirective } from '../shared/sort/sort.directive';
import { SortByDirective } from '../shared/sort/sort-by.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbHighlight, NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { ButtonComponent } from '../shared/components/button.component';

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
    imports: [FormsModule, TranslateDirective, SortDirective, SortByDirective, FaIconComponent, NgbHighlight, ButtonComponent, NgbPagination],
})
export class LectureImportComponent extends ImportComponent<Lecture> {
    constructor() {
        super();
        this.columns = tableColumns;
        this.entityName = 'lecture';
    }
}
