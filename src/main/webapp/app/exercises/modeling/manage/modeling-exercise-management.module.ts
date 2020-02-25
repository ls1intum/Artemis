import { NgModule } from '@angular/core';
import { ArtemisExampleModelingSolutionModule } from 'app/exercises/modeling/manage/example-modeling/example-modeling-solution.module';
import { ArtemisExampleModelingSubmissionModule } from 'app/exercises/modeling/manage/example-modeling/example-modeling-submission.module';
import { ArtemisModelingExerciseModule } from 'app/exercises/modeling/manage/modeling-exercise.module';
import { ArtemisModelingStatisticsModule } from 'app/exercises/modeling/manage/modeling-statistics/modeling-statistics.module';

@NgModule({
    imports: [ArtemisExampleModelingSolutionModule, ArtemisExampleModelingSubmissionModule, ArtemisModelingExerciseModule, ArtemisModelingStatisticsModule],
})
export class ArtemisModelingExerciseManagementModule {}
