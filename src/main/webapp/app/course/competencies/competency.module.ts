import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { CompetencyCardComponent } from 'app/course/competencies/competency-card/competency-card.component';
import { CompetenciesPopoverComponent } from './competencies-popover/competencies-popover.component';
import { NgxGraphModule } from '@swimlane/ngx-graph';
import { CompetencyRingsComponent } from 'app/course/competencies/competency-rings/competency-rings.component';
import { NgbAccordionModule, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { CompetencyRecommendationDetailComponent } from 'app/course/competencies/generate-competencies/competency-recommendation-detail.component';
import { CourseDescriptionFormComponent } from 'app/course/competencies/generate-competencies/course-description-form.component';
import { IrisModule } from 'app/iris/iris.module';
import { TaxonomySelectComponent } from 'app/course/competencies/taxonomy-select/taxonomy-select.component';
import { CompetencyAccordionComponent } from 'app/course/competencies/competency-accordion/competency-accordion.component';
import { ArtemisCourseExerciseRowModule } from 'app/overview/course-exercises/course-exercise-row.module';
import { JudgementOfLearningRatingComponent } from 'app/course/competencies/judgement-of-learning-rating/judgement-of-learning-rating.component';
import { CompetencyManagementTableComponent } from 'app/course/competencies/competency-management/competency-management-table.component';
import { CompetencySearchComponent } from 'app/course/competencies/import/competency-search.component';
import { ImportCompetenciesTableComponent } from 'app/course/competencies/import/import-competencies-table.component';

@NgModule({
    imports: [
        FormsModule,
        ReactiveFormsModule,
        NgxGraphModule,
        NgbModule,

        RouterModule,
        NgbAccordionModule,
        IrisModule,
        ArtemisCourseExerciseRowModule,
        JudgementOfLearningRatingComponent,
        CompetencyManagementTableComponent,
        CompetencyRingsComponent,
        CompetencySearchComponent,
        CompetencyRecommendationDetailComponent,
        CourseDescriptionFormComponent,
        CompetencyCardComponent,
        CompetencyAccordionComponent,
        CompetenciesPopoverComponent,
        ImportCompetenciesTableComponent,
        TaxonomySelectComponent,
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
