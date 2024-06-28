import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisExamTimerModule } from 'app/exam/participate/timer/exam-timer.module';
import { ArtemisExamLiveEventsModule } from 'app/exam/participate/events/exam-live-events.module';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { faDoorClosed } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-exam-bar',
    standalone: true,
    imports: [CommonModule, ArtemisSharedCommonModule, ArtemisExamTimerModule, ArtemisExamLiveEventsModule],
    templateUrl: './exam-bar.component.html',
    styleUrl: './exam-bar.component.scss',
})
export class ExamBarComponent {
    @Output() onExamHandInEarly = new EventEmitter<void>();
    @Output() examAboutToEnd = new EventEmitter<void>();

    @Input() examTitle: string;
    @Input() examTimeLineView = false;
    @Input() endDate: dayjs.Dayjs;
    @Input() exerciseIndex = 0;
    @Input() exercises: Exercise[] = [];

    readonly faDoorClosed = faDoorClosed;

    criticalTime = dayjs.duration(5, 'minutes');

    /**
     * Save the currently active exercise
     */
    saveExercise() {
        const submission = ExamParticipationService.getSubmissionForExercise(this.exercises[this.exerciseIndex]);
        // we do not submit programming exercises on a save
        if (submission && this.exercises[this.exerciseIndex].type !== ExerciseType.PROGRAMMING) {
            submission.submitted = true;
        }
    }

    triggerExamAboutToEnd() {
        this.saveExercise();
        this.examAboutToEnd.emit();
    }

    /**
     * Notify parent component when user wants to hand in early
     */
    handInEarly() {
        this.onExamHandInEarly.emit();
    }
}
