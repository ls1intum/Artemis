import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArtemisSharedModule } from '../../shared';
import { quizStatisticRoute } from './quiz-statistic.route';
import { HomeComponent } from '../../home';
import { JhiMainComponent } from '../../layouts';
import { QuizStatisticComponent } from './quiz-statistic/quiz-statistic.component';
import { ChartsModule } from 'ng2-charts';
import { QuizPointStatisticComponent } from './quiz-point-statistic/quiz-point-statistic.component';
import { QuizStatisticUtil } from '../../components/util/quiz-statistic-util.service';
import { MultipleChoiceQuestionStatisticComponent } from './multiple-choice-question-statistic/multiple-choice-question-statistic.component';
import { DragAndDropQuestionStatisticComponent } from './drag-and-drop-question-statistic/drag-and-drop-question-statistic.component';
import { ShortAnswerQuestionStatisticComponent } from './short-answer-question-statistic/short-answer-question-statistic.component';
import { ArtemisQuizModule } from '../participate/quiz.module';
import { QuizStatisticsFooterComponent } from 'app/quiz/statistics/quiz-statistics-footer/quiz-statistics-footer.component';

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
    providers: [QuizStatisticUtil, { provide: JhiLanguageService, useClass: JhiLanguageService }],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class ArtemisStatisticModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
