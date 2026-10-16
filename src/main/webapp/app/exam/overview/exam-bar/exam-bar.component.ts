import { Component, computed, input, output } from '@angular/core';

import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faDoorClosed } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { ExamTimerComponent } from 'app/exam/overview/timer/exam-timer.component';
import { ExamLiveEventsButtonComponent } from 'app/exam/overview/events/button/exam-live-events-button.component';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TumAetUiButtonDirective, TumAetUiTagComponent } from '@tumaet/ui-angular';

@Component({
    selector: 'jhi-exam-bar',
    imports: [ExamTimerComponent, ExamLiveEventsButtonComponent, FontAwesomeModule, TranslateDirective, TumAetUiButtonDirective, TumAetUiTagComponent, ArtemisTranslatePipe],
    templateUrl: './exam-bar.component.html',
})
export class ExamBarComponent {
    protected readonly faDoorClosed = faDoorClosed;

    readonly onExamHandInEarly = output<void>();
    readonly examAboutToEnd = output<void>();

    readonly examTimeLineView = input(false);
    readonly endDate = input<dayjs.Dayjs>(undefined!);
    readonly exerciseIndex = input(0);
    readonly isEndView = input<boolean>(undefined!);
    readonly testRunStartTime = input<dayjs.Dayjs>();
    readonly exam = input<Exam>(undefined!);
    readonly studentExam = input<StudentExam>(undefined!);
    readonly examStartDate = input<dayjs.Dayjs>(undefined!);

    criticalTime = dayjs.duration(5, 'minutes');
    criticalTimeEndView = dayjs.duration(30, 'seconds');
    readonly testExam = computed(() => this.exam()?.testExam ?? false);
    readonly isTestRun = computed(() => this.studentExam()?.testRun ?? false);
    readonly examTitle = computed(() => this.exam()?.title ?? '');
    readonly exercises = computed<Exercise[]>(() => this.studentExam()?.exercises ?? []);

    /**
     * Save the currently active exercise
     */
    saveExercise() {
        const exercises = this.exercises();
        const submission = ExamParticipationService.getSubmissionForExercise(exercises[this.exerciseIndex()]);
        // we do not submit programming exercises on a save
        if (submission && exercises[this.exerciseIndex()].type !== ExerciseType.PROGRAMMING) {
            submission.submitted = true;
        }
    }

    triggerExamAboutToEnd() {
        this.saveExercise();
        this.examAboutToEnd.emit();
    }

    handInEarly() {
        this.onExamHandInEarly.emit();
    }
}
