import { Component, inject } from '@angular/core';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { Column, ImportComponent } from 'app/shared/import/import.component';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbHighlight, NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { LecturePagingService } from 'app/lecture/manage/services/lecture-paging.service';

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
    templateUrl: '../../../shared/import/import.component.html',
    imports: [FormsModule, TranslateDirective, SortDirective, SortByDirective, FaIconComponent, NgbHighlight, ButtonComponent, NgbPagination],
})
export class LectureImportComponent extends ImportComponent<Lecture> {
    constructor() {
        const pagingService = inject(LecturePagingService);
        super(pagingService);
        this.columns = tableColumns;
        this.entityName = 'lecture';
    }
}
