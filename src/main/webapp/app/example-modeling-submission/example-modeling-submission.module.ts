import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { AlertService } from 'app/core/alert/alert.service';
import { MomentModule } from 'ngx-moment';
import { ClipboardModule } from 'ngx-clipboard';
import { exampleModelingSubmissionRoute } from 'app/example-modeling-submission/example-modeling-submission.route';
import { ExampleModelingSubmissionComponent } from 'app/example-modeling-submission/example-modeling-submission.component';
import { ArtemisModelingEditorModule } from 'app/modeling-editor/modeling-editor.module';
import { ModelingAssessmentModule } from 'app/modeling-assessment/modeling-assessment.module';
import { AssessmentInstructionsModule } from 'app/assessment-instructions/assessment-instructions.module';
import { JhiMainComponent } from 'app/layouts/main/main.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { HomeComponent } from 'app/home/home.component';
import { ArtemisResultModule } from 'app/entities/result/result.module';
import { CourseComponent } from 'app/entities/course/course.component';

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
    providers: [],
    schemas: [],
})
export class ArtemisExampleModelingSubmissionModule {}
