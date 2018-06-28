import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';

import { ArTeMiSSubmissionModule } from './submission/submission.module';
import { ArTeMiSCourseModule } from './course/course.module';
import { ArTeMiSExerciseModule } from './exercise/exercise.module';
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
import { ArTeMiSResultModule } from './result/result.module';
import { ArTeMiSFeedbackModule } from './feedback/feedback.module';
import { ArTeMiSModelingSubmissionModule } from './modeling-submission/modeling-submission.module';
import { ArTeMiSQuizSubmissionModule } from './quiz-submission/quiz-submission.module';
import { ArTeMiSDragAndDropSubmittedAnswerModule } from './drag-and-drop-submitted-answer/drag-and-drop-submitted-answer.module';
import { ArTeMiSDragAndDropAssignmentModule } from './drag-and-drop-assignment/drag-and-drop-assignment.module';
/* jhipster-needle-add-entity-module-import - JHipster will add entity modules imports here */

@NgModule({
    imports: [
        ArTeMiSSubmissionModule,
        ArTeMiSCourseModule,
        ArTeMiSExerciseModule,
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
        ArTeMiSResultModule,
        ArTeMiSFeedbackModule,
        ArTeMiSModelingSubmissionModule,
        ArTeMiSQuizSubmissionModule,
        ArTeMiSDragAndDropSubmittedAnswerModule,
        ArTeMiSDragAndDropAssignmentModule,
        /* jhipster-needle-add-entity-module - JHipster will add entity modules here */
    ],
    declarations: [],
    entryComponents: [],
    providers: [],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSEntityModule {}
