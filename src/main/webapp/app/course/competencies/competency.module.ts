import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { CompetencyFormComponent } from './competency-form/competency-form.component';
import { CreateCompetencyComponent } from './create-competency/create-competency.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { EditCompetencyComponent } from './edit-competency/edit-competency.component';
import { CompetencyManagementComponent } from './competency-management/competency-management.component';
import { CompetencyCardComponent } from 'app/course/competencies/competency-card/competency-card.component';
import { CompetenciesPopoverComponent } from './competencies-popover/competencies-popover.component';
import { NgxGraphModule } from '@swimlane/ngx-graph';
import { CompetencyRingsComponent } from 'app/course/competencies/competency-rings/competency-rings.component';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { NgbAccordionModule, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { GenerateCompetenciesComponent } from 'app/course/competencies/generate-competencies/generate-competencies.component';
import { CompetencyRecommendationDetailComponent } from 'app/course/competencies/generate-competencies/competency-recommendation-detail.component';
import { CourseDescriptionFormComponent } from 'app/course/competencies/generate-competencies/course-description-form.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { IrisModule } from 'app/iris/iris.module';
import { ImportAllCompetenciesComponent } from 'app/course/competencies/competency-management/import-all-competencies.component';
import { CompetencySearchComponent } from 'app/course/competencies/import-competencies/competency-search.component';
import { ImportCompetenciesTableComponent } from 'app/course/competencies/import-competencies/import-competencies-table.component';
import { TaxonomySelectComponent } from 'app/course/competencies/taxonomy-select/taxonomy-select.component';
import { CompetencyRelationGraphComponent } from 'app/course/competencies/competency-management/competency-relation-graph.component';
import { CompetencyAccordionComponent } from 'app/course/competencies/competency-accordion/competency-accordion.component';
import { CourseImportStandardizedCompetenciesComponent } from 'app/course/competencies/import-standardized-competencies/course-import-standardized-competencies.component';
import { ArtemisStandardizedCompetencyModule } from 'app/shared/standardized-competencies/standardized-competency.module';
import { ImportCompetenciesComponent } from 'app/course/competencies/import-competencies/import-competencies.component';
import { ImportPrerequisitesComponent } from 'app/course/competencies/import-competencies/import-prerequisites.component';
import { ArtemisCourseExerciseRowModule } from 'app/overview/course-exercises/course-exercise-row.module';
import { RatingModule } from 'app/exercises/shared/rating/rating.module';
import { JudgementOfLearningRatingComponent } from 'app/course/competencies/judgement-of-learning-rating/judgement-of-learning-rating.component';

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
        ArtemisStandardizedCompetencyModule,
        ArtemisCourseExerciseRowModule,
        RatingModule,
        JudgementOfLearningRatingComponent,
    ],
    declarations: [
        CompetencyFormComponent,
        CompetencyRingsComponent,
        CreateCompetencyComponent,
        EditCompetencyComponent,
        CompetencySearchComponent,
        GenerateCompetenciesComponent,
        CompetencyRecommendationDetailComponent,
        CourseDescriptionFormComponent,
        CompetencyManagementComponent,
        CompetencyCardComponent,
        CompetencyAccordionComponent,
        CompetenciesPopoverComponent,
        ImportAllCompetenciesComponent,
        ImportCompetenciesTableComponent,
        TaxonomySelectComponent,
        CompetencyRelationGraphComponent,
        CourseImportStandardizedCompetenciesComponent,
        ImportCompetenciesComponent,
        ImportPrerequisitesComponent,
    ],
    exports: [CompetencyCardComponent, CompetencyAccordionComponent, CompetenciesPopoverComponent, CompetencyFormComponent, CompetencyRingsComponent, TaxonomySelectComponent],
})
export class ArtemisCompetenciesModule {}
