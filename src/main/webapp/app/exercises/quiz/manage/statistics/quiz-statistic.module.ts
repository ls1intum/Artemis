import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { QuizStatisticComponent } from './quiz-statistic/quiz-statistic.component';
import { QuizPointStatisticComponent } from './quiz-point-statistic/quiz-point-statistic.component';
import { MultipleChoiceQuestionStatisticComponent } from './multiple-choice-question-statistic/multiple-choice-question-statistic.component';
import { DragAndDropQuestionStatisticComponent } from './drag-and-drop-question-statistic/drag-and-drop-question-statistic.component';
import { ShortAnswerQuestionStatisticComponent } from './short-answer-question-statistic/short-answer-question-statistic.component';
import { QuizStatisticsFooterComponent } from 'app/exercises/quiz/manage/statistics/quiz-statistics-footer/quiz-statistics-footer.component';
import { quizStatisticRoute } from 'app/exercises/quiz/manage/statistics/quiz-statistic.route';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisQuizQuestionTypesModule } from 'app/exercises/quiz/shared/questions/artemis-quiz-question-types.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { BarChartModule } from '@swimlane/ngx-charts';

const ENTITY_STATES = [...quizStatisticRoute];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES), ArtemisQuizQuestionTypesModule, ArtemisMarkdownModule, BarChartModule],
    declarations: [
        QuizStatisticComponent,
        QuizPointStatisticComponent,
        MultipleChoiceQuestionStatisticComponent,
        DragAndDropQuestionStatisticComponent,
        ShortAnswerQuestionStatisticComponent,
        QuizStatisticsFooterComponent,
    ],
})
export class ArtemisQuizStatisticModule {}
