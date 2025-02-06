import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { ImportCourseCompetenciesComponent } from 'app/course/competencies/import/import-course-competencies.component';
import { onError } from 'app/shared/util/global.utils';
import { PrerequisiteService } from 'app/course/competencies/prerequisite.service';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';
import { CourseCompetencyType } from 'app/entities/competency.model';
import { ButtonComponent } from 'app/shared/components/button.component';

@Component({
    selector: 'jhi-import-prerequisites',
    templateUrl: './import-course-competencies.component.html',
    imports: [ArtemisSharedCommonModule, ArtemisCompetenciesModule, ButtonComponent],
})
export class ImportPrerequisitesComponent extends ImportCourseCompetenciesComponent {
    entityType = CourseCompetencyType.PREREQUISITE;
    allowRelationImport = false;

    protected readonly prerequisiteService: PrerequisiteService = inject(PrerequisiteService);

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
