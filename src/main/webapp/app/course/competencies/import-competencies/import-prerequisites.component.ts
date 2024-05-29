import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { ImportCourseCompetenciesComponent } from 'app/course/competencies/import-competencies/import-course-competencies.component';
import { onError } from 'app/shared/util/global.utils';
import { PrerequisiteService } from '../prerequisite.service';

@Component({
    selector: 'jhi-import-prerequisites',
    templateUrl: './import-course-competencies.component.html',
})
export class ImportPrerequisitesComponent extends ImportCourseCompetenciesComponent {
    entityType = 'prerequisite';
    allowRelationImport = false;

    private readonly prerequisiteService: PrerequisiteService = inject(PrerequisiteService);

    onSubmit() {
        const idsToImport = this.selectedCourseCompetencies.resultsOnPage.map((c) => c.id).filter((c): c is number => c !== undefined);
        this.prerequisiteService.importPrerequisites(idsToImport, this.courseId).subscribe({
            next: (res) => {
                this.alertService.success('artemisApp.prerequisite.import.success', { numPrerequisites: res.length });
                this.isSubmitted = true;
                this.router.navigate(['../'], { relativeTo: this.activatedRoute });
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }
}
