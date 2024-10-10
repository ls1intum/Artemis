import { NgModule } from '@angular/core';
import { ArtemisCodeEditorModule } from 'app/exercises/programming/shared/code-editor/code-editor.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instructions-render.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { RepositoryViewComponent } from 'app/localvc/repository-view/repository-view.component';
import { ArtemisProgrammingRepositoryRoutingModule } from 'app/exercises/programming/participate/programming-repository-routing.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { ArtemisComplaintsForTutorModule } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ArtemisProgrammingManualAssessmentModule } from 'app/exercises/programming/assess/programming-manual-assessment.module';
import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { FormsModule } from '@angular/forms';
import { ArtemisProgrammingExerciseModule } from 'app/exercises/programming/shared/programming-exercise.module';
import { CommitHistoryComponent } from 'app/localvc/commit-history/commit-history.component';
import { CommitDetailsViewComponent } from 'app/localvc/commit-details-view/commit-details-view.component';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { GitDiffReportComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-report.component';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        FormDateTimePickerModule,
        FormsModule,
        FeatureToggleModule,
        ArtemisComplaintsForTutorModule,
        ArtemisProgrammingRepositoryRoutingModule,
        ArtemisAssessmentSharedModule,
        ArtemisCodeEditorModule,
        ArtemisResultModule,
        ArtemisProgrammingExerciseInstructionsRenderModule,
        ArtemisProgrammingManualAssessmentModule,
        AssessmentInstructionsModule,
        ArtemisHeaderExercisePageWithDetailsModule,
        SubmissionResultStatusModule,
        ArtemisProgrammingExerciseModule,
        ArtemisProgrammingExerciseActionsModule,
        GitDiffReportComponent,
    ],
    declarations: [RepositoryViewComponent, CommitHistoryComponent, CommitDetailsViewComponent],
    exports: [RepositoryViewComponent],
})
export class ArtemisProgrammingRepositoryModule {}
