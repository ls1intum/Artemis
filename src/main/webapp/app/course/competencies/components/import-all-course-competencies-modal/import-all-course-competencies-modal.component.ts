import { Component, computed, inject, input, signal } from '@angular/core';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ImportListComponent } from 'app/shared/import-list/import-list.component';
import { PagingService } from 'app/exercises/shared/manage/paging.service';
import { CourseForImportDTOPagingService } from 'app/course/course-for-import-dto-paging-service';
import { Column } from 'app/shared/import/import.component';
import { Course } from 'app/entities/course.model';
import {
    CourseCompetencyImportSettings,
    ImportCourseCompetenciesSettingsComponent,
} from 'app/course/competencies/components/import-course-competencies-settings/import-course-competencies-settings.component';
import { CourseCompetencyImportOptionsDTO } from 'app/entities/competency.model';

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

@Component({
    selector: 'jhi-import-all-course-competencies-modal',
    standalone: true,
    imports: [ArtemisSharedCommonModule, ImportListComponent, ImportCourseCompetenciesSettingsComponent],
    providers: [
        {
            provide: PagingService,
            useClass: CourseForImportDTOPagingService,
        },
    ],
    templateUrl: './import-all-course-competencies-modal.component.html',
    styleUrl: './import-all-course-competencies-modal.component.scss',
})
export class ImportAllCourseCompetenciesModalComponent {
    protected readonly tableColumns = tableColumns;

    protected readonly closeIcon = faXmark;

    private readonly activeModal = inject(NgbActiveModal);

    readonly courseId = input.required<number>();
    readonly disabledIds = computed(() => [+this.courseId()]);

    readonly importSettings = signal<CourseCompetencyImportSettings>(new CourseCompetencyImportSettings());

    public selectCourse(course: Course): void {
        const courseCompetencyImportOptions = <CourseCompetencyImportOptionsDTO>{
            sourceCourseId: course.id,
            importExercises: this.importSettings().importExercises,
            importLectures: this.importSettings().importLectures,
            importRelations: this.importSettings().importRelations,
            referenceDate: this.importSettings().referenceDate,
            isReleaseDate: this.importSettings().isReleaseDate,
        };
        this.activeModal.close(<ImportAllCourseCompetenciesResult>{
            course,
            courseCompetencyImportOptions,
        });
    }

    protected closeModal(): void {
        this.activeModal.close();
    }
}
