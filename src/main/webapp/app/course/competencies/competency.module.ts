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
import { PrerequisiteImportComponent } from 'app/course/competencies/competency-management/prerequisite-import.component';
import { NgxGraphModule } from '@swimlane/ngx-graph';
import { CompetencyRingsComponent } from 'app/course/competencies/competency-rings/competency-rings.component';
import { CompetencyImportComponent } from 'app/course/competencies/competency-management/competency-import.component';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { NgbAccordionModule } from '@ng-bootstrap/ng-bootstrap';
import { ParseCoureDescriptionComponent } from 'app/course/competencies/parse-description/parse-course-description.component';
import { CompetencyRecommendationDetailComponent } from 'app/course/competencies/parse-description/competency-recommendation-detail.component';
import { CourseDescriptionComponent } from 'app/course/competencies/parse-description/course-description.component';

@NgModule({
    imports: [ArtemisSharedModule, FormsModule, ReactiveFormsModule, NgxGraphModule, ArtemisSharedComponentModule, RouterModule, FormDateTimePickerModule, NgbAccordionModule],
    declarations: [
        CompetencyFormComponent,
        CompetencyRingsComponent,
        CreateCompetencyComponent,
        EditCompetencyComponent,
        ParseCoureDescriptionComponent,
        CompetencyRecommendationDetailComponent,
        CourseDescriptionComponent,
        CompetencyManagementComponent,
        CompetencyCardComponent,
        CompetenciesPopoverComponent,
        PrerequisiteImportComponent,
        CompetencyImportComponent,
    ],
    exports: [CompetencyCardComponent, CompetenciesPopoverComponent, CompetencyFormComponent, CompetencyRingsComponent],
})
export class ArtemisCompetenciesModule {}
