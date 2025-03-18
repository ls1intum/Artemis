import { Component, inject } from '@angular/core';
import { CompetencyService } from 'app/atlas/manage/competency.service';
import { CourseImportStandardizedCourseCompetenciesComponent } from 'app/atlas/manage/import-standardized-competencies/course-import-standardized-course-competencies.component';
import { StandardizedCompetencyFilterComponent } from 'app/shared/standardized-competencies/standardized-competency-filter.component';
import { StandardizedCompetencyDetailComponent } from 'app/shared/standardized-competencies/standardized-competency-detail.component';
import { KnowledgeAreaTreeComponent } from 'app/shared/standardized-competencies/knowledge-area-tree.component';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { CommonModule } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { FormsModule } from '@angular/forms';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-course-import-standardized-competencies',
    templateUrl: './course-import-standardized-competencies.component.html',
    imports: [
        StandardizedCompetencyFilterComponent,
        StandardizedCompetencyDetailComponent,
        KnowledgeAreaTreeComponent,
        HtmlForMarkdownPipe,
        DocumentationButtonComponent,
        ButtonComponent,
        TranslateDirective,
        SortByDirective,
        SortDirective,
        CommonModule,
        ArtemisTranslatePipe,
        FontAwesomeModule,
        FormsModule,
        NgbTooltipModule,
    ],
})
export class CourseImportStandardizedCompetenciesComponent extends CourseImportStandardizedCourseCompetenciesComponent {
    private competencyService = inject(CompetencyService);

    protected importCompetencies() {
        super.importCompetencies(this.competencyService);
    }
}
