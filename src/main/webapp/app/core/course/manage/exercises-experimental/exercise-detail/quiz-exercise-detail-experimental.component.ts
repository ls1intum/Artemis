import { Component, signal } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faRobot } from '@fortawesome/free-solid-svg-icons';
import { ExerciseVariantAiModalDispatcherComponent } from 'app/core/course/manage/exercises-experimental/create-modal/exercise-variant-ai-modal-dispatcher.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { ExerciseDetailStatisticsComponent } from 'app/exercise/statistics/exercise-detail-statistic/exercise-detail-statistics.component';
import { DetailOverviewListComponent } from 'app/shared/detail-overview-list/detail-overview-list.component';
import { QuizExerciseManageButtonsComponent } from 'app/quiz/manage/manage-buttons/quiz-exercise-manage-buttons.component';
import { QuizExerciseLifecycleButtonsComponent } from 'app/quiz/manage/lifecyle-buttons/quiz-exercise-lifecycle-buttons.component';
import { QuizExerciseDetailComponent } from 'app/quiz/manage/detail/quiz-exercise-detail.component';

@Component({
    selector: 'jhi-quiz-exercise-detail-experimental',
    templateUrl: './quiz-exercise-detail-experimental.component.html',
    imports: [
        TranslateDirective,
        DocumentationButtonComponent,
        FaIconComponent,
        QuizExerciseManageButtonsComponent,
        QuizExerciseLifecycleButtonsComponent,
        ExerciseDetailStatisticsComponent,
        DetailOverviewListComponent,
        ExerciseVariantAiModalDispatcherComponent,
    ],
})
export class QuizExerciseDetailExperimentalComponent extends QuizExerciseDetailComponent {
    protected readonly faRobot = faRobot;
    readonly aiVariantModalVisible = signal(false);
}
