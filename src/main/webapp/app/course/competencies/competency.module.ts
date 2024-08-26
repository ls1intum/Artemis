import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { CompetencyManagementComponent } from './competency-management/competency-management.component';
import { CompetencyCardComponent } from 'app/course/competencies/competency-card/competency-card.component';
import { CompetenciesPopoverComponent } from './competencies-popover/competencies-popover.component';
import { NgxGraphModule } from '@swimlane/ngx-graph';
import { CompetencyRingsComponent } from 'app/course/competencies/competency-rings/competency-rings.component';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { NgbAccordionModule, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { CompetencyRecommendationDetailComponent } from 'app/course/competencies/generate-competencies/competency-recommendation-detail.component';
import { CourseDescriptionFormComponent } from 'app/course/competencies/generate-competencies/course-description-form.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { IrisModule } from 'app/iris/iris.module';
import { TaxonomySelectComponent } from 'app/course/competencies/taxonomy-select/taxonomy-select.component';
import { CompetencyRelationGraphComponent } from 'app/course/competencies/competency-management/competency-relation-graph.component';
import { CompetencyAccordionComponent } from 'app/course/competencies/competency-accordion/competency-accordion.component';
import { ArtemisCourseExerciseRowModule } from 'app/overview/course-exercises/course-exercise-row.module';
import { RatingModule } from 'app/exercises/shared/rating/rating.module';
import { JudgementOfLearningRatingComponent } from 'app/course/competencies/judgement-of-learning-rating/judgement-of-learning-rating.component';
import { CompetencyManagementTableComponent } from 'app/course/competencies/competency-management/competency-management-table.component';
import { CompetencySearchComponent } from 'app/course/competencies/import/competency-search.component';
import { ImportCompetenciesTableComponent } from 'app/course/competencies/import/import-competencies-table.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';

@NgModule({
    imports: [
        ArtemisSharedModule,
        FormsModule,
        ReactiveFormsModule,
        NgxGraphModule,
        NgbModule,
        ArtemisSharedComponentModule,
        RouterModule,
        FormDateTimePickerModule,
        NgbAccordionModule,
        ArtemisMarkdownModule,
        IrisModule,
        ArtemisCourseExerciseRowModule,
        RatingModule,
        JudgementOfLearningRatingComponent,
        CompetencyManagementTableComponent,
        ArtemisMarkdownEditorModule,
    ],
    declarations: [
        CompetencyRingsComponent,
        CompetencySearchComponent,
        CompetencyRecommendationDetailComponent,
        CourseDescriptionFormComponent,
        CompetencyManagementComponent,
        CompetencyCardComponent,
        CompetencyAccordionComponent,
        CompetenciesPopoverComponent,
        ImportCompetenciesTableComponent,
        TaxonomySelectComponent,
        CompetencyRelationGraphComponent,
    ],
    exports: [
        CompetencyCardComponent,
        CompetencyAccordionComponent,
        CompetenciesPopoverComponent,
        CompetencyRingsComponent,
        TaxonomySelectComponent,
        ImportCompetenciesTableComponent,
        CompetencySearchComponent,
        CompetencyRecommendationDetailComponent,
        CourseDescriptionFormComponent,
    ],
})
export class ArtemisCompetenciesModule {}
