import { NgModule } from '@angular/core';
import { MultipleChoiceQuestionEditComponent } from './multiple-choice-question/multiple-choice-question-edit.component';
import { DragAndDropQuestionEditComponent } from './drag-and-drop-question/drag-and-drop-question-edit.component';
import { ShortAnswerQuestionEditComponent } from './short-answer-question/short-answer-question-edit.component';
import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { DragDropModule } from '@angular/cdk/drag-drop';
import { QuizScoringInfoModalComponent } from './quiz-scoring-info-modal/quiz-scoring-info-modal.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisQuizStatisticModule } from 'app/exercises/quiz/manage/statistics/quiz-statistic.module';
import { ArtemisApollonDiagramsModule } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram.module';
import { quizManagementRoute } from 'app/exercises/quiz/manage/quiz-management.route';
import { QuizExerciseComponent } from 'app/exercises/quiz/manage/quiz-exercise.component';
import { QuizExerciseDetailComponent } from 'app/exercises/quiz/manage/quiz-exercise-detail.component';
import { RouterModule } from '@angular/router';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisCategorySelectorModule } from 'app/shared/category-selector/category-selector.module';
import { ArtemisDifficultyPickerModule } from 'app/exercises/shared/difficulty-picker/difficulty-picker.module';
import { QuizReEvaluateComponent } from 'app/exercises/quiz/manage/re-evaluate/quiz-re-evaluate.component';
import { ReEvaluateMultipleChoiceQuestionComponent } from 'app/exercises/quiz/manage/re-evaluate/multiple-choice-question/re-evaluate-multiple-choice-question.component';
import { ReEvaluateDragAndDropQuestionComponent } from 'app/exercises/quiz/manage/re-evaluate/drag-and-drop-question/re-evaluate-drag-and-drop-question.component';
import { ReEvaluateShortAnswerQuestionComponent } from 'app/exercises/quiz/manage/re-evaluate/short-answer-question/re-evaluate-short-answer-question.component';
import { QuizReEvaluateWarningComponent } from 'app/exercises/quiz/manage/re-evaluate/quiz-re-evaluate-warning.component';
import { QuizExerciseExportComponent } from 'app/exercises/quiz/manage/quiz-exercise-export.component';
import { ArtemisQuizQuestionTypesModule } from 'app/exercises/quiz/shared/questions/artemis-quiz-question-types.module';
import { ArtemisQuizParticipationModule } from 'app/exercises/quiz/participate/quiz-participation.module';
import { QuizConfirmImportInvalidQuestionsModalComponent } from 'app/exercises/quiz/manage/quiz-confirm-import-invalid-questions-modal.component';
import { ArtemisIncludedInOverallScorePickerModule } from 'app/exercises/shared/included-in-overall-score-picker/included-in-overall-score-picker.module';
import { MatchPercentageInfoModalComponent } from 'app/exercises/quiz/manage/match-percentage-info-modal/match-percentage-info-modal.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { FitTextModule } from 'app/exercises/quiz/shared/fit-text/fit-text.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { QuizExerciseImportComponent } from 'app/exercises/quiz/manage/quiz-exercise-import.component';

const ENTITY_STATES = [...quizManagementRoute];

@NgModule({
    imports: [
        ArtemisSharedModule,
        RouterModule.forChild(ENTITY_STATES),
        DragDropModule,
        AceEditorModule,
        FormDateTimePickerModule,
        ArtemisQuizQuestionTypesModule,
        ArtemisMarkdownEditorModule,
        ArtemisQuizStatisticModule,
        ArtemisApollonDiagramsModule,
        ArtemisCategorySelectorModule,
        ArtemisDifficultyPickerModule,
        ArtemisIncludedInOverallScorePickerModule,
        ArtemisQuizParticipationModule,
        ArtemisMarkdownModule,
        FitTextModule,
        ArtemisSharedComponentModule,
    ],
    declarations: [
        QuizExerciseComponent,
        QuizConfirmImportInvalidQuestionsModalComponent,
        QuizExerciseDetailComponent,
        MultipleChoiceQuestionEditComponent,
        DragAndDropQuestionEditComponent,
        QuizScoringInfoModalComponent,
        ShortAnswerQuestionEditComponent,
        QuizReEvaluateComponent,
        ReEvaluateMultipleChoiceQuestionComponent,
        ReEvaluateDragAndDropQuestionComponent,
        ReEvaluateShortAnswerQuestionComponent,
        QuizReEvaluateWarningComponent,
        QuizExerciseExportComponent,
        MatchPercentageInfoModalComponent,
        QuizExerciseImportComponent,
    ],
    // only add popups
    entryComponents: [QuizReEvaluateWarningComponent],
    exports: [QuizExerciseComponent],
})
export class ArtemisQuizManagementModule {}
