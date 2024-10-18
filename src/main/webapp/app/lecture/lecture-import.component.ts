import { Component } from '@angular/core';
import { Lecture } from 'app/entities/lecture.model';
import { Column, ImportComponent } from 'app/shared/import/import.component';

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
    constructor() {
        super();
        this.columns = tableColumns;
        this.entityName = 'lecture';
    }
}
