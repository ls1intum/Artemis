import { NgModule } from '@angular/core';

import { ProgrammingExerciseInstructorExerciseDownloadComponent } from '../shared/actions/programming-exercise-instructor-exercise-download.component';
import { ProgrammingExerciseInstructorRepoDownloadComponent } from '../shared/actions/programming-exercise-instructor-repo-download.component';
import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ArtemisProgrammingAssessmentModule } from 'app/exercises/programming/assess/programming-assessment.module';
import { ArtemisCodeHintGenerationOverviewModule } from 'app/exercises/programming/hestia/generation-overview/code-hint-generation-overview/code-hint-generation-overview.module';
import { GitDiffReportModule } from 'app/exercises/programming/hestia/git-diff-report/git-diff-report.module';
import { TestwiseCoverageReportModule } from 'app/exercises/programming/hestia/testwise-coverage-report/testwise-coverage-report.module';
import { ArtemisProgrammingExerciseInstructionsEditorModule } from 'app/exercises/programming/manage/instructions-editor/programming-exercise-instructions-editor.module';
import { ProgrammingExerciseDetailComponent } from 'app/exercises/programming/manage/programming-exercise-detail.component';
import { ProgrammingExerciseEditSelectedComponent } from 'app/exercises/programming/manage/programming-exercise-edit-selected.component';
import { ArtemisProgrammingExerciseManagementRoutingModule } from 'app/exercises/programming/manage/programming-exercise-management-routing.module';
import { ArtemisProgrammingExerciseStatusModule } from 'app/exercises/programming/manage/status/programming-exercise-status.module';
import { ArtemisProgrammingExerciseUpdateModule } from 'app/exercises/programming/manage/update/programming-exercise-update.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ProgrammingExerciseExampleSolutionRepoDownloadComponent } from 'app/exercises/programming/shared/actions/programming-exercise-example-solution-repo-download.component';
import { ArtemisProgrammingExerciseLifecycleModule } from 'app/exercises/programming/shared/lifecycle/programming-exercise-lifecycle.module';
import { ArtemisProgrammingExerciseModule } from 'app/exercises/programming/shared/programming-exercise.module';
import { ExerciseDetailsModule } from 'app/exercises/shared/exercise/exercise-details/exercise-details.module';
import { ArtemisPlagiarismModule } from 'app/exercises/shared/plagiarism/plagiarism.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { OrionModule } from 'app/shared/orion/orion.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        ArtemisResultModule,
        ArtemisAssessmentSharedModule,
        ArtemisPlagiarismModule,
        ArtemisProgrammingExerciseModule,
        ArtemisProgrammingExerciseManagementRoutingModule,
        ArtemisProgrammingAssessmentModule,
        ArtemisProgrammingExerciseActionsModule,
        ArtemisProgrammingExerciseInstructionsEditorModule,
        ArtemisProgrammingExerciseUpdateModule,
        ArtemisProgrammingExerciseStatusModule,
        FeatureToggleModule,
        ExerciseDetailsModule,
        AssessmentInstructionsModule,
        OrionModule,
        ArtemisProgrammingExerciseLifecycleModule,
        SubmissionResultStatusModule,
        GitDiffReportModule,
        TestwiseCoverageReportModule,
        ArtemisCodeHintGenerationOverviewModule,
    ],
    declarations: [
        ProgrammingExerciseDetailComponent,
        ProgrammingExerciseEditSelectedComponent,
        ProgrammingExerciseInstructorRepoDownloadComponent,
        ProgrammingExerciseInstructorExerciseDownloadComponent,
        ProgrammingExerciseExampleSolutionRepoDownloadComponent,
    ],
    exports: [ProgrammingExerciseExampleSolutionRepoDownloadComponent],
})
export class ArtemisProgrammingExerciseManagementModule {}
