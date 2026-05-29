import { Component, computed, inject, signal } from '@angular/core';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';

import { PagingService } from 'app/exercise/services/paging.service';
import { Column } from 'app/shared-ui/import/import.component';
import { Course } from 'app/course/shared/entities/course.model';
import {
    CourseCompetencyImportSettings,
    ImportCourseCompetenciesSettingsComponent,
} from 'app/atlas/manage/import-course-competencies-settings/import-course-competencies-settings.component';
import { CourseCompetencyImportOptionsDTO } from 'app/atlas/shared/entities/competency.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CourseForImportDTOPagingService } from 'app/course/shared/services/course-for-import-dto-paging-service';
import { ImportTableComponent } from 'app/atlas/manage/import-list/import-table.component';

const tableColumns: Column<Course>[] = [
    {
        name: 'TITLE',
        getProperty: (entity: Course) => entity.title,
    },
    {
        name: 'SHORT_NAME',
        getProperty: (entity: Course) => entity.shortName,
    },
    {
        name: 'SEMESTER',
        getProperty: (entity: Course) => entity.semester,
    },
];

export interface ImportAllCourseCompetenciesResult {
    course: Course;
    courseCompetencyImportOptions: CourseCompetencyImportOptionsDTO;
}

export interface ImportAllCourseCompetenciesModalData {
    courseId: number;
}

@Component({
    selector: 'jhi-import-all-course-competencies-modal',
    imports: [ImportTableComponent, ImportCourseCompetenciesSettingsComponent, FaIconComponent, TranslateDirective],
    providers: [
        {
            provide: PagingService,
            useClass: CourseForImportDTOPagingService,
        },
    ],
    templateUrl: './import-all-course-competencies-modal.component.html',
})
export class ImportAllCourseCompetenciesModalComponent {
    protected readonly tableColumns = tableColumns;

    protected readonly closeIcon = faXmark;

    private readonly dialogRef = inject(DynamicDialogRef);
    private readonly dialogConfig = inject(DynamicDialogConfig);

    readonly courseId = signal<number>((this.dialogConfig.data as ImportAllCourseCompetenciesModalData).courseId);
    readonly disabledIds = computed(() => [+this.courseId()]);

    importSettings = signal<CourseCompetencyImportSettings>(new CourseCompetencyImportSettings());

    public selectCourse(course: Course): void {
        const courseCompetencyImportOptions = <CourseCompetencyImportOptionsDTO>{
            sourceCourseId: course.id,
            ...this.importSettings(),
        };
        this.dialogRef.close(<ImportAllCourseCompetenciesResult>{
            course,
            courseCompetencyImportOptions,
        });
    }

    protected closeModal(): void {
        this.dialogRef.close();
    }
}
