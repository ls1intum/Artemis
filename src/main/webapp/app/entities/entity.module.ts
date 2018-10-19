import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';

import { ArTeMiSStatisticModule } from './statistic/statistic.module';
import { ArTeMiSQuizPointStatisticModule } from './quiz-point-statistic/quiz-point-statistic.module';
import { ArTeMiSQuestionStatisticModule } from './question-statistic/question-statistic.module';
import { ArTeMiSMultipleChoiceQuestionStatisticModule } from './multiple-choice-question-statistic/multiple-choice-question-statistic.module';
import { ArTeMiSDragAndDropQuestionStatisticModule } from './drag-and-drop-question-statistic/drag-and-drop-question-statistic.module';
import { ArTeMiSStatisticCounterModule } from './statistic-counter/statistic-counter.module';
import { ArTeMiSPointCounterModule } from './point-counter/point-counter.module';
import { ArTeMiSAnswerCounterModule } from './answer-counter/answer-counter.module';
import { ArTeMiSDropLocationCounterModule } from './drop-location-counter/drop-location-counter.module';
import { ArTeMiSCourseModule } from './course/course.module';
import { ArTeMiSExerciseModule } from './exercise/exercise.module';
import { ArTeMiSTextExerciseModule } from './text-exercise/text-exercise.module';
import { ArTeMiSFileUploadExerciseModule } from './file-upload-exercise/file-upload-exercise.module';
import { ArTeMiSProgrammingExerciseModule } from './programming-exercise/programming-exercise.module';
import { ArTeMiSModelingExerciseModule } from './modeling-exercise/modeling-exercise.module';
import { ArTeMiSQuizExerciseModule } from './quiz-exercise/quiz-exercise.module';
import { ArTeMiSLtiOutcomeUrlModule } from './lti-outcome-url/lti-outcome-url.module';
import { ArTeMiSSubmittedAnswerModule } from './submitted-answer/submitted-answer.module';
import { ArTeMiSQuestionModule } from './question/question.module';
import { ArTeMiSMultipleChoiceQuestionModule } from './multiple-choice-question/multiple-choice-question.module';
import { ArTeMiSAnswerOptionModule } from './answer-option/answer-option.module';
import { ArTeMiSMultipleChoiceSubmittedAnswerModule } from './multiple-choice-submitted-answer/multiple-choice-submitted-answer.module';
import { ArTeMiSDragAndDropQuestionModule } from './drag-and-drop-question/drag-and-drop-question.module';
import { ArTeMiSDropLocationModule } from './drop-location/drop-location.module';
import { ArTeMiSDragItemModule } from './drag-item/drag-item.module';
import { ArTeMiSParticipationModule } from './participation/participation.module';
import { ArTeMiSLtiUserIdModule } from './lti-user-id/lti-user-id.module';
import { ArTeMiSExerciseResultModule } from './exercise-result/exercise-result.module';
import { ArTeMiSFeedbackModule } from './feedback/feedback.module';
import { ArTeMiSSubmissionModule } from './submission/submission.module';
import { ArTeMiSModelingSubmissionModule } from './modeling-submission/modeling-submission.module';
import { ArTeMiSQuizSubmissionModule } from './quiz-submission/quiz-submission.module';
import { ArTeMiSProgrammingSubmissionModule } from './programming-submission/programming-submission.module';
import { ArTeMiSTextSubmissionModule } from './text-submission/text-submission.module';
import { ArTeMiSFileUploadSubmissionModule } from './file-upload-submission/file-upload-submission.module';
import { ArTeMiSDragAndDropSubmittedAnswerModule } from './drag-and-drop-submitted-answer/drag-and-drop-submitted-answer.module';
import { ArTeMiSDragAndDropMappingModule } from './drag-and-drop-mapping/drag-and-drop-mapping.module';
import { ArTeMiSApollonDiagramModule } from './apollon-diagram/apollon-diagram.module';
/* jhipster-needle-add-entity-module-import - JHipster will add entity modules imports here */

@NgModule({
    // prettier-ignore
    imports: [
        ArTeMiSStatisticModule,
        ArTeMiSQuizPointStatisticModule,
        ArTeMiSQuestionStatisticModule,
        ArTeMiSMultipleChoiceQuestionStatisticModule,
        ArTeMiSDragAndDropQuestionStatisticModule,
        ArTeMiSStatisticCounterModule,
        ArTeMiSPointCounterModule,
        ArTeMiSAnswerCounterModule,
        ArTeMiSDropLocationCounterModule,
        ArTeMiSCourseModule,
        ArTeMiSExerciseModule,
        ArTeMiSTextExerciseModule,
        ArTeMiSFileUploadExerciseModule,
        ArTeMiSProgrammingExerciseModule,
        ArTeMiSModelingExerciseModule,
        ArTeMiSQuizExerciseModule,
        ArTeMiSLtiOutcomeUrlModule,
        ArTeMiSSubmittedAnswerModule,
        ArTeMiSQuestionModule,
        ArTeMiSMultipleChoiceQuestionModule,
        ArTeMiSAnswerOptionModule,
        ArTeMiSMultipleChoiceSubmittedAnswerModule,
        ArTeMiSDragAndDropQuestionModule,
        ArTeMiSDropLocationModule,
        ArTeMiSDragItemModule,
        ArTeMiSParticipationModule,
        ArTeMiSLtiUserIdModule,
        ArTeMiSExerciseResultModule,
        ArTeMiSFeedbackModule,
        ArTeMiSSubmissionModule,
        ArTeMiSModelingSubmissionModule,
        ArTeMiSQuizSubmissionModule,
        ArTeMiSProgrammingSubmissionModule,
        ArTeMiSTextSubmissionModule,
        ArTeMiSFileUploadSubmissionModule,
        ArTeMiSDragAndDropSubmittedAnswerModule,
        ArTeMiSDragAndDropMappingModule,
        ArTeMiSApollonDiagramModule,
        /* jhipster-needle-add-entity-module - JHipster will add entity modules here */
    ],
    declarations: [],
    entryComponents: [],
    providers: [],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSEntityModule {}
