import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { ImportCourseCompetenciesComponent } from 'app/course/competencies/import/import-course-competencies.component';
import { onError } from 'app/shared/util/global.utils';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';
import { CourseCompetencyType } from 'app/entities/competency.model';

@Component({
    selector: 'jhi-import-competencies',
    templateUrl: './import-course-competencies.component.html',
    standalone: true,
    imports: [ArtemisSharedCommonModule, ArtemisSharedComponentModule, ArtemisCompetenciesModule],
})
export class ImportCompetenciesComponent extends ImportCourseCompetenciesComponent {
    entityType = CourseCompetencyType.COMPETENCY;
    allowRelationImport = true;

    protected readonly competencyService: CompetencyService = inject(CompetencyService);

    onSubmit() {
        this.competencyService.importBulk(this.selectedCourseCompetencies.resultsOnPage, this.courseId, this.importRelations).subscribe({
            next: (res) => {
                this.alertService.success('artemisApp.competency.import.success', { numCompetencies: res.body?.length ?? 0 });
                this.isSubmitted = true;
                this.router.navigate(['../'], { relativeTo: this.activatedRoute });
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }
}
