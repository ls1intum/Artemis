import { Component, inject } from '@angular/core';
import { PrerequisiteService } from 'app/course/competencies/prerequisite.service';
import { CourseImportStandardizedCourseCompetenciesComponent } from 'app/course/competencies/import-standardized-competencies/course-import-standardized-course-competencies.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { StandardizedCompetencyFilterComponent } from 'app/shared/standardized-competencies/standardized-competency-filter.component';
import { StandardizedCompetencyDetailComponent } from 'app/shared/standardized-competencies/standardized-competency-detail.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { KnowledgeAreaTreeComponent } from 'app/shared/standardized-competencies/knowledge-area-tree.component';

@Component({
    selector: 'jhi-course-import-standardized-prerequisites',
    templateUrl: './course-import-standardized-prerequisites.component.html',
    standalone: true,
    imports: [
        ArtemisSharedCommonModule,
        ArtemisSharedComponentModule,
        ArtemisMarkdownModule,
        StandardizedCompetencyFilterComponent,
        StandardizedCompetencyDetailComponent,
        KnowledgeAreaTreeComponent,
    ],
})
export class CourseImportStandardizedPrerequisitesComponent extends CourseImportStandardizedCourseCompetenciesComponent {
    private prerequisiteService = inject(PrerequisiteService);

    protected importCompetencies() {
        super.importCompetencies(this.prerequisiteService);
    }
}
