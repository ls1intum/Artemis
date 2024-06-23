import { HttpErrorResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { ImportCourseCompetenciesComponent } from 'app/course/competencies/import-competencies/import-course-competencies.component';
import { onError } from 'app/shared/util/global.utils';

@Component({
    selector: 'jhi-import-competencies',
    templateUrl: './import-course-competencies.component.html',
})
export class ImportCompetenciesComponent extends ImportCourseCompetenciesComponent {
    entityType = 'competency';
    allowRelationImport = true;

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
