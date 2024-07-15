import { HttpErrorResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { ImportCourseCompetenciesComponent } from 'app/course/competencies/import-competencies/import-course-competencies.component';
import { onError } from 'app/shared/util/global.utils';

@Component({
    selector: 'jhi-import-prerequisites',
    templateUrl: './import-course-competencies.component.html',
})
export class ImportPrerequisitesComponent extends ImportCourseCompetenciesComponent {
    entityType = 'prerequisite';
    allowRelationImport = false;

    onSubmit() {
        this.prerequisiteService.importBulk(this.selectedCourseCompetencies.resultsOnPage, this.courseId, false).subscribe({
            next: (res) => {
                this.alertService.success('artemisApp.prerequisite.import.success', { numPrerequisites: res.body?.length ?? 0 });
                this.isSubmitted = true;
                this.router.navigate(['../'], { relativeTo: this.activatedRoute });
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }
}
