import { Component, inject } from '@angular/core';
import { PrerequisiteService } from 'app/course/competencies/prerequisite.service';
import { CourseImportStandardizedCourseCompetenciesComponent } from 'app/course/competencies/import-standardized-competencies/course-import-standardized-course-competencies.component';

import { StandardizedCompetencyFilterComponent } from 'app/shared/standardized-competencies/standardized-competency-filter.component';
import { StandardizedCompetencyDetailComponent } from 'app/shared/standardized-competencies/standardized-competency-detail.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { KnowledgeAreaTreeComponent } from 'app/shared/standardized-competencies/knowledge-area-tree.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { FormsModule } from '@angular/forms';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ButtonComponent } from 'app/shared/components/button.component';
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';

@Component({
    selector: 'jhi-course-import-standardized-prerequisites',
    templateUrl: './course-import-standardized-prerequisites.component.html',
    imports: [
        ArtemisSharedCommonModule,

        StandardizedCompetencyFilterComponent,
        StandardizedCompetencyDetailComponent,
        KnowledgeAreaTreeComponent,
        FaIconComponent,
        FormsModule,
        NgbTooltipModule,
        HtmlForMarkdownPipe,
        ButtonComponent,
        DocumentationButtonComponent,
    ],
})
export class CourseImportStandardizedPrerequisitesComponent extends CourseImportStandardizedCourseCompetenciesComponent {
    private prerequisiteService = inject(PrerequisiteService);

    protected importCompetencies() {
        super.importCompetencies(this.prerequisiteService);
    }
}
