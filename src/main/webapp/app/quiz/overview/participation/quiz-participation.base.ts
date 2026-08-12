import type { OutputRef, Signal } from '@angular/core';
import type { QuizLiveHeaderInfo } from 'app/exercise/exercise-headers/exercise-headers-information/exercise-headers-information.component';
import type { LiveQuizParticipationStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import type { QuizSubmission } from 'app/quiz/shared/entities/quiz-submission.model';
import type { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';

/**
 * Abstract base class for quiz participation components rendered inside the exercise split panel's
 * router outlet. Kept in a separate file so that {@link ExerciseSplitPanelComponent} can use
 * {@code instanceof QuizParticipationBase} without statically importing
 * {@link QuizParticipationComponent} (which would defeat lazy chunk splitting).
 *
 * All imports are {@code import type} — abstract members have no JavaScript output, so this class
 * compiles to a near-empty constructor and adds zero meaningful bytes to the eager bundle.
 */
export abstract class QuizParticipationBase {
    abstract readonly isSubmitDisabled: Signal<boolean>;
    abstract readonly submitTitleKey: Signal<string>;
    abstract readonly liveHeaderInfo: Signal<QuizLiveHeaderInfo | undefined>;
    abstract readonly mode: Signal<string>;
    abstract restartPractice(): void;
    abstract readonly quizStartedEvent: OutputRef<void>;
    abstract readonly quizSubmittedEvent: OutputRef<QuizSubmission>;
    abstract readonly liveQuizStatusChange: OutputRef<LiveQuizParticipationStatus | undefined>;
    abstract readonly practiceParticipationChanged: OutputRef<StudentParticipation>;
    abstract readonly liveQuizResultParticipation: OutputRef<StudentParticipation>;
}
