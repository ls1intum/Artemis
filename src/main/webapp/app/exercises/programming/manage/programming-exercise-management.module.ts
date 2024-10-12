import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisProgrammingExerciseManagementRoutingModule } from 'app/exercises/programming/manage/programming-exercise-management-routing.module';
import { ProgrammingExerciseDetailComponent } from 'app/exercises/programming/manage/programming-exercise-detail.component';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ArtemisProgrammingAssessmentModule } from 'app/exercises/programming/assess/programming-assessment.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ArtemisProgrammingExerciseInstructionsEditorModule } from 'app/exercises/programming/manage/instructions-editor/programming-exercise-instructions-editor.module';
import { ArtemisProgrammingExerciseUpdateModule } from 'app/exercises/programming/manage/update/programming-exercise-update.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisProgrammingExerciseStatusModule } from 'app/exercises/programming/manage/status/programming-exercise-status.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { ArtemisProgrammingExerciseModule } from 'app/exercises/programming/shared/programming-exercise.module';
import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { ProgrammingExerciseEditSelectedComponent } from 'app/exercises/programming/manage/programming-exercise-edit-selected.component';
import { OrionModule } from 'app/shared/orion/orion.module';
import { ArtemisPlagiarismModule } from 'app/exercises/shared/plagiarism/plagiarism.module';
import { ArtemisProgrammingExerciseLifecycleModule } from 'app/exercises/programming/shared/lifecycle/programming-exercise-lifecycle.module';
import { ProgrammingExerciseInstructorExerciseDownloadComponent } from '../shared/actions/programming-exercise-instructor-exercise-download.component';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { ProgrammingExerciseExampleSolutionRepoDownloadComponent } from 'app/exercises/programming/shared/actions/programming-exercise-example-solution-repo-download.component';
import { TestwiseCoverageReportModule } from 'app/exercises/programming/hestia/testwise-coverage-report/testwise-coverage-report.module';
import { ArtemisCodeHintGenerationOverviewModule } from 'app/exercises/programming/hestia/generation-overview/code-hint-generation-overview/code-hint-generation-overview.module';
import { BuildPlanEditorComponent } from 'app/exercises/programming/manage/build-plan-editor.component';
import { ArtemisCodeEditorModule } from 'app/exercises/programming/shared/code-editor/code-editor.module';
import { DetailModule } from 'app/detail-overview-list/detail.module';
import { IrisModule } from 'app/iris/iris.module';
import { ArtemisExerciseModule } from 'app/exercises/shared/exercise/exercise.module';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';

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
        AssessmentInstructionsModule,
        OrionModule,
        ArtemisProgrammingExerciseLifecycleModule,
        SubmissionResultStatusModule,
        TestwiseCoverageReportModule,
        ArtemisCodeHintGenerationOverviewModule,
        ArtemisCodeEditorModule,
        ArtemisExerciseModule,
        DetailModule,
        IrisModule,
        MonacoEditorComponent,
    ],
    declarations: [
        ProgrammingExerciseDetailComponent,
        ProgrammingExerciseEditSelectedComponent,
        ProgrammingExerciseInstructorExerciseDownloadComponent,
        ProgrammingExerciseExampleSolutionRepoDownloadComponent,
        BuildPlanEditorComponent,
    ],
    exports: [ProgrammingExerciseExampleSolutionRepoDownloadComponent],
})
export class ArtemisProgrammingExerciseManagementModule {}
