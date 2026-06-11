import { Component } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DocumentationButtonComponent } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { ExerciseDetailStatisticsComponent } from 'app/exercise/statistics/exercise-detail-statistic/exercise-detail-statistics.component';
import { DetailOverviewListComponent } from 'app/shared-ui/detail-overview-list/detail-overview-list.component';
import { QuizExerciseManageButtonsComponent } from 'app/quiz/manage/manage-buttons/quiz-exercise-manage-buttons.component';
import { QuizExerciseLifecycleButtonsComponent } from 'app/quiz/manage/lifecyle-buttons/quiz-exercise-lifecycle-buttons.component';
import { QuizExerciseDetailComponent } from 'app/quiz/manage/detail/quiz-exercise-detail.component';

@Component({
    selector: 'jhi-quiz-exercise-detail-experimental',
    templateUrl: './quiz-exercise-detail-experimental.component.html',
    imports: [
        TranslateDirective,
        DocumentationButtonComponent,
        QuizExerciseManageButtonsComponent,
        QuizExerciseLifecycleButtonsComponent,
        ExerciseDetailStatisticsComponent,
        DetailOverviewListComponent,
    ],
})
export class QuizExerciseDetailExperimentalComponent extends QuizExerciseDetailComponent {}
