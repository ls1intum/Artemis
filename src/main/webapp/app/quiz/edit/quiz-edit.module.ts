import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArTEMiSSharedModule } from '../../shared';
import { JhiAlertService } from 'ng-jhipster';
import { RepositoryService } from '../../entities/repository/repository.service';
import { HomeComponent } from '../../home';
import { JhiMainComponent } from '../../layouts';
import { QuizComponent } from '../participate/quiz.component';
import { QuizExerciseComponent } from '../../entities/quiz-exercise';
import { EditMultipleChoiceQuestionComponent } from './multiple-choice-question/edit-multiple-choice-question.component';
import { EditDragAndDropQuestionComponent } from './drag-and-drop-question/edit-drag-and-drop-question.component';
import { EditShortAnswerQuestionComponent } from './short-answer-question/edit-short-answer-question.component';
import { AngularFittextModule } from 'angular-fittext';
import { AceEditorModule } from 'ng2-ace-editor';
import { DndModule } from 'ng2-dnd';
import { ArTEMiSQuizModule } from '../participate';
import { QuizScoringInfoModalComponent } from './quiz-scoring-info-modal/quiz-scoring-info-modal.component';

@NgModule({
    imports: [ArTEMiSSharedModule, DndModule.forRoot(), AngularFittextModule, AceEditorModule, ArTEMiSQuizModule],
    declarations: [
        EditMultipleChoiceQuestionComponent,
        EditDragAndDropQuestionComponent,
        QuizScoringInfoModalComponent,
        EditShortAnswerQuestionComponent
    ],
    entryComponents: [
        HomeComponent,
        QuizComponent,
        QuizExerciseComponent,
        JhiMainComponent,
        EditMultipleChoiceQuestionComponent,
        EditDragAndDropQuestionComponent,
        EditShortAnswerQuestionComponent
    ],
    providers: [RepositoryService, JhiAlertService, { provide: JhiLanguageService, useClass: JhiLanguageService }],
    exports: [EditMultipleChoiceQuestionComponent, EditDragAndDropQuestionComponent, EditShortAnswerQuestionComponent],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSQuizEditModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey !== undefined) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
