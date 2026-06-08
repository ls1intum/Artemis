import { Component } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DocumentationButtonComponent } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { ExerciseDetailStatisticsComponent } from 'app/exercise/statistics/exercise-detail-statistic/exercise-detail-statistics.component';
import { DetailOverviewListComponent } from 'app/shared-ui/detail-overview-list/detail-overview-list.component';
import { ModelingExerciseDetailComponent } from 'app/modeling/manage/detail/modeling-exercise-detail.component';
import { ExerciseDetailExperimentalActionsComponent } from './exercise-detail-experimental-actions.component';

@Component({
    selector: 'jhi-modeling-exercise-detail-experimental',
    templateUrl: './modeling-exercise-detail-experimental.component.html',
    imports: [TranslateDirective, DocumentationButtonComponent, ExerciseDetailExperimentalActionsComponent, ExerciseDetailStatisticsComponent, DetailOverviewListComponent],
})
export class ModelingExerciseDetailExperimentalComponent extends ModelingExerciseDetailComponent {}
