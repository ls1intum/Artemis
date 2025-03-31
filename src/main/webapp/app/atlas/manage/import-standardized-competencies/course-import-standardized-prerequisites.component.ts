import { Component, inject } from '@angular/core';
import { PrerequisiteService } from 'app/atlas/manage/prerequisite.service';
import { CourseImportStandardizedCourseCompetenciesComponent } from 'app/atlas/manage/import-standardized-competencies/course-import-standardized-course-competencies.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { FormsModule } from '@angular/forms';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ButtonComponent } from 'app/shared/components/button.component';
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { StandardizedCompetencyFilterComponent } from 'app/atlas/shared/standardized-competencies/standardized-competency-filter.component';
import { StandardizedCompetencyDetailComponent } from 'app/atlas/shared/standardized-competencies/standardized-competency-detail.component';
import { KnowledgeAreaTreeComponent } from 'app/atlas/shared/standardized-competencies/knowledge-area-tree.component';

@Component({
    selector: 'jhi-course-import-standardized-prerequisites',
    templateUrl: './course-import-standardized-prerequisites.component.html',
    imports: [
        StandardizedCompetencyFilterComponent,
        StandardizedCompetencyDetailComponent,
        KnowledgeAreaTreeComponent,
        FaIconComponent,
        FormsModule,
        NgbTooltipModule,
        HtmlForMarkdownPipe,
        ButtonComponent,
        DocumentationButtonComponent,
        TranslateDirective,
        SortByDirective,
        SortDirective,
        ArtemisTranslatePipe,
    ],
})
export class CourseImportStandardizedPrerequisitesComponent extends CourseImportStandardizedCourseCompetenciesComponent {
    private prerequisiteService = inject(PrerequisiteService);

    protected importCompetencies() {
        super.importCompetencies(this.prerequisiteService);
    }
}
