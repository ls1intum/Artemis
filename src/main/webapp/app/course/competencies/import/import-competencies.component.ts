import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { ImportCourseCompetenciesComponent } from 'app/course/competencies/import/import-course-competencies.component';
import { onError } from 'app/shared/util/global.utils';
import { CompetencyService } from 'app/course/competencies/competency.service';

import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';
import { CourseCompetencyType } from 'app/entities/competency.model';
import { ButtonComponent } from 'app/shared/components/button.component';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-import-competencies',
    templateUrl: './import-course-competencies.component.html',
    imports: [ArtemisCompetenciesModule, ButtonComponent, CommonModule, FormsModule, TranslateDirective],
})
export class ImportCompetenciesComponent extends ImportCourseCompetenciesComponent {
    entityType = CourseCompetencyType.COMPETENCY;
    allowRelationImport = true;

    private readonly competencyService = inject(CompetencyService);

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
