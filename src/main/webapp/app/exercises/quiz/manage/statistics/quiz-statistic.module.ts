import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { QuizStatisticComponent } from './quiz-statistic/quiz-statistic.component';
import { ChartsModule } from 'ng2-charts';
import { QuizPointStatisticComponent } from './quiz-point-statistic/quiz-point-statistic.component';
import { QuizStatisticUtil } from 'app/exercises/quiz/shared/quiz-statistic-util.service';
import { MultipleChoiceQuestionStatisticComponent } from './multiple-choice-question-statistic/multiple-choice-question-statistic.component';
import { DragAndDropQuestionStatisticComponent } from './drag-and-drop-question-statistic/drag-and-drop-question-statistic.component';
import { ShortAnswerQuestionStatisticComponent } from './short-answer-question-statistic/short-answer-question-statistic.component';
import { ArtemisQuizModule } from '../../participate/quiz.module';
import { QuizStatisticsFooterComponent } from 'app/exercises/quiz/manage/statistics/quiz-statistics-footer/quiz-statistics-footer.component';
import { quizStatisticRoute } from 'app/exercises/quiz/manage/statistics/quiz-statistic.route';
import { JhiMainComponent } from 'app/shared/layouts/main/main.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { HomeComponent } from 'app/home/home.component';

const ENTITY_STATES = [...quizStatisticRoute];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES), ChartsModule, ArtemisQuizModule],
    declarations: [
        QuizStatisticComponent,
        QuizPointStatisticComponent,
        MultipleChoiceQuestionStatisticComponent,
        DragAndDropQuestionStatisticComponent,
        ShortAnswerQuestionStatisticComponent,
        QuizStatisticsFooterComponent,
    ],
    entryComponents: [
        HomeComponent,
        JhiMainComponent,
        QuizStatisticComponent,
        QuizPointStatisticComponent,
        MultipleChoiceQuestionStatisticComponent,
        DragAndDropQuestionStatisticComponent,
        ShortAnswerQuestionStatisticComponent,
    ],
    providers: [QuizStatisticUtil],
})
export class ArtemisStatisticModule {}
