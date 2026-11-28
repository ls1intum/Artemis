import { Component, inject } from '@angular/core';
import { CompetencyService } from 'app/atlas/manage/services/competency.service';
import { CourseImportStandardizedCourseCompetenciesComponent } from 'app/atlas/manage/import-standardized-competencies/course-import-standardized-course-competencies.component';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { DocumentationButtonComponent } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TumAetUiCheckboxComponent } from '@tumaet/ui-angular/checkbox';
import { TumAetUiProgressSpinnerComponent } from '@tumaet/ui-angular/progress-spinner';
import { TumAetUiTableDirective, TumAetUiTableSortableColumnComponent } from '@tumaet/ui-angular/table-directive';
import { TumAetUiTableLayoutComponent } from '@tumaet/ui-angular/table-layout';
import { TumAetUiTooltipDirective } from '@tumaet/ui-angular/tooltip';
import { CommonModule } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { FormsModule } from '@angular/forms';
import { StandardizedCompetencyFilterComponent } from 'app/atlas/shared/standardized-competencies/standardized-competency-filter.component';
import { StandardizedCompetencyDetailComponent } from 'app/atlas/shared/standardized-competencies/standardized-competency-detail.component';
import { KnowledgeAreaTreeComponent } from 'app/atlas/shared/standardized-competencies/knowledge-area-tree.component';

@Component({
    selector: 'jhi-course-import-standardized-competencies',
    templateUrl: './course-import-standardized-course-competencies.component.html',
    imports: [
        StandardizedCompetencyFilterComponent,
        StandardizedCompetencyDetailComponent,
        KnowledgeAreaTreeComponent,
        MarkdownDirective,
        DocumentationButtonComponent,
        ButtonComponent,
        TranslateDirective,
        TumAetUiTableDirective,
        TumAetUiTableLayoutComponent,
        TumAetUiTableSortableColumnComponent,
        CommonModule,
        ArtemisTranslatePipe,
        FontAwesomeModule,
        FormsModule,
        TumAetUiCheckboxComponent,
        TumAetUiProgressSpinnerComponent,
        TumAetUiTooltipDirective,
    ],
})
export class CourseImportStandardizedCompetenciesComponent extends CourseImportStandardizedCourseCompetenciesComponent {
    private competencyService = inject(CompetencyService);

    protected override importCompetencies() {
        super.importCompetencies(this.competencyService);
    }
}
