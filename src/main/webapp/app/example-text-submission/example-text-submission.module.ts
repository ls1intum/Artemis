import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiAlertService } from 'ng-jhipster';

import { ArtemisSharedModule } from '../shared';
import { CourseComponent, CourseService } from '../entities/course';
import { ArtemisResultModule } from '../entities/result';
import { HomeComponent } from '../home';
import { MomentModule } from 'ngx-moment';
import { JhiMainComponent } from '../layouts';
import { ClipboardModule } from 'ngx-clipboard';
import { exampleTextSubmissionRoute } from 'app/example-text-submission/example-text-submission.route';
import { ExampleTextSubmissionComponent } from 'app/example-text-submission/example-text-submission.component';
import { ArtemisTextAssessmentModule } from 'app/text-assessment';

const ENTITY_STATES = [...exampleTextSubmissionRoute];

@NgModule({
    imports: [ArtemisSharedModule, ArtemisResultModule, MomentModule, ClipboardModule, RouterModule.forChild(ENTITY_STATES), ArtemisTextAssessmentModule],
    declarations: [ExampleTextSubmissionComponent],
    exports: [],
    entryComponents: [HomeComponent, CourseComponent, JhiMainComponent],
    providers: [CourseService, JhiAlertService],
    schemas: [],
})
export class ArtemisExampleTextSubmissionModule {}
