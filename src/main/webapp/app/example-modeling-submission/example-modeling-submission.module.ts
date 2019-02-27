import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';

import { RouterModule } from '@angular/router';

import { ArTEMiSSharedModule } from '../shared';
import { CourseComponent, CourseService } from '../entities/course';
import { JhiAlertService } from 'ng-jhipster';
import { ArTEMiSResultModule } from '../entities/result';
import { HomeComponent } from '../home';
import { MomentModule } from 'angular2-moment';
import { JhiMainComponent } from '../layouts';
import { ClipboardModule } from 'ngx-clipboard';
import { exampleModelingSubmissionRoute} from 'app/example-modeling-submission/example-modeling-submission.route';
import { ExampleModelingSubmissionComponent } from 'app/example-modeling-submission/example-modeling-submission.component';
import { ArTEMiSModelingAssessmentModule} from 'app/entities/modeling-assessment/modeling-assessment.module';
import { ArTEMiSModelingEditorModule } from 'app/modeling-editor/modeling-editor.module';

const ENTITY_STATES = [...exampleModelingSubmissionRoute];

@NgModule({
    imports: [ArTEMiSSharedModule, ArTEMiSResultModule, MomentModule, ClipboardModule, RouterModule.forChild(ENTITY_STATES), ArTEMiSModelingAssessmentModule, ArTEMiSModelingEditorModule],
    declarations: [ExampleModelingSubmissionComponent],
    exports: [],
    entryComponents: [HomeComponent, CourseComponent, JhiMainComponent],
    providers: [
        CourseService,
        JhiAlertService,
    ],
    schemas: []
})
export class ArTEMiSExampleModelingSubmissionModule {}
