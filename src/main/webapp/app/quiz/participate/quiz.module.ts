import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArTEMiSSharedModule } from '../../shared';
import { JhiWebsocketService } from '../../core';
import { JhiAlertService } from 'ng-jhipster';
import { quizRoute } from './quiz.route';
import { RepositoryService } from '../../entities/repository/repository.service';
import { HomeComponent } from '../../home';
import { JhiMainComponent } from '../../layouts';
import { QuizComponent } from './quiz.component';
import { MultipleChoiceQuestionComponent } from './multiple-choice-question/multiple-choice-question.component';
import { DragAndDropQuestionComponent } from './drag-and-drop-question/drag-and-drop-question.component';
import { ShortAnswerQuestionComponent } from './short-answer-question/short-answer-question.component';
import { DragItemComponent } from './drag-and-drop-question/drag-item.component';
import { AngularFittextModule } from 'angular-fittext';
import { SecuredImageComponent } from '../../components/util/secured-image.component';
import { DndModule } from 'ng2-dnd';
import { QuizScoringInfoStudentModalComponent } from './quiz-scoring-infostudent-modal/quiz-scoring-info-student-modal.component';

const ENTITY_STATES = [...quizRoute];

@NgModule({
    imports: [ArTEMiSSharedModule, RouterModule.forChild(ENTITY_STATES), DndModule.forRoot(), AngularFittextModule],
    declarations: [
        QuizComponent,
        MultipleChoiceQuestionComponent,
        DragAndDropQuestionComponent,
        QuizScoringInfoStudentModalComponent,
        ShortAnswerQuestionComponent,
        DragItemComponent,
        SecuredImageComponent
    ],
    entryComponents: [HomeComponent, QuizComponent, JhiMainComponent],
    providers: [RepositoryService, JhiWebsocketService, JhiAlertService, { provide: JhiLanguageService, useClass: JhiLanguageService }],
    exports: [
        MultipleChoiceQuestionComponent,
        DragAndDropQuestionComponent,
        ShortAnswerQuestionComponent,
        DragItemComponent,
        SecuredImageComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSQuizModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey !== undefined) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
