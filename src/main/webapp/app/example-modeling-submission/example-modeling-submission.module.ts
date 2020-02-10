import { NgModule } from '@angular/core';

import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from '../shared';
import { CourseComponent } from '../entities/course';
import { JhiAlertService } from 'ng-jhipster';
import { ArtemisResultModule } from '../entities/result';
import { HomeComponent } from '../home';
import { MomentModule } from 'ngx-moment';
import { JhiMainComponent } from '../layouts';
import { ClipboardModule } from 'ngx-clipboard';
import { exampleModelingSubmissionRoute } from 'app/example-modeling-submission/example-modeling-submission.route';
import { ExampleModelingSubmissionComponent } from 'app/example-modeling-submission/example-modeling-submission.component';
import { ArtemisModelingEditorModule } from 'app/modeling-editor/modeling-editor.module';
import { ModelingAssessmentModule } from 'app/modeling-assessment/modeling-assessment.module';
import { AssessmentInstructionsModule } from 'app/assessment-instructions/assessment-instructions.module';

const ENTITY_STATES = [...exampleModelingSubmissionRoute];

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisResultModule,
        MomentModule,
        ClipboardModule,
        RouterModule.forChild(ENTITY_STATES),
        ModelingAssessmentModule,
        AssessmentInstructionsModule,
        ArtemisModelingEditorModule,
    ],
    declarations: [ExampleModelingSubmissionComponent],
    exports: [],
    entryComponents: [HomeComponent, CourseComponent, JhiMainComponent],
    providers: [JhiAlertService],
    schemas: [],
})
export class ArtemisExampleModelingSubmissionModule {}
