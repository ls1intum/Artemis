import { NgModule } from '@angular/core';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArtemisSharedModule } from '../../shared';
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
import { ArtemisMarkdownEditorModule } from 'app/markdown-editor/markdown-editor.module';
import { DndModule } from 'ng2-dnd';
import { ArtemisQuizModule } from '../participate';
import { QuizScoringInfoModalComponent } from './quiz-scoring-info-modal/quiz-scoring-info-modal.component';

@NgModule({
    imports: [ArtemisSharedModule, DndModule.forRoot(), AngularFittextModule, AceEditorModule, ArtemisQuizModule, ArtemisMarkdownEditorModule],
    declarations: [EditMultipleChoiceQuestionComponent, EditDragAndDropQuestionComponent, QuizScoringInfoModalComponent, EditShortAnswerQuestionComponent],
    entryComponents: [
        HomeComponent,
        QuizComponent,
        QuizExerciseComponent,
        JhiMainComponent,
        EditMultipleChoiceQuestionComponent,
        EditDragAndDropQuestionComponent,
        EditShortAnswerQuestionComponent,
    ],
    providers: [RepositoryService, JhiAlertService, { provide: JhiLanguageService, useClass: JhiLanguageService }],
    exports: [EditMultipleChoiceQuestionComponent, EditDragAndDropQuestionComponent, EditShortAnswerQuestionComponent],
})
export class ArtemisQuizEditModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
