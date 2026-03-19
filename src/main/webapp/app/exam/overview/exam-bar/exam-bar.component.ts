import { AfterViewInit, Component, ElementRef, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';

import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faDoorClosed } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { ExamTimerComponent } from 'app/exam/overview/timer/exam-timer.component';
import { ExamLiveEventsButtonComponent } from 'app/exam/overview/events/button/exam-live-events-button.component';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-exam-bar',
    imports: [CommonModule, ExamTimerComponent, ExamLiveEventsButtonComponent, FontAwesomeModule, TranslateDirective],
    templateUrl: './exam-bar.component.html',
    styleUrl: './exam-bar.component.scss',
})
export class ExamBarComponent implements AfterViewInit, OnInit {
    private readonly elementRef = inject(ElementRef);

    protected readonly faDoorClosed = faDoorClosed;

    @Output() onExamHandInEarly = new EventEmitter<void>();
    @Output() examAboutToEnd = new EventEmitter<void>();
    @Output() heightChange = new EventEmitter<number>();

    @Input() examTimeLineView = false;
    @Input() endDate: dayjs.Dayjs;
    @Input() exerciseIndex = 0;
    @Input() isEndView: boolean;
    @Input() testRunStartTime: dayjs.Dayjs | undefined;
    @Input() exam: Exam;
    @Input() studentExam: StudentExam;
    @Input() examStartDate: dayjs.Dayjs;

    criticalTime = dayjs.duration(5, 'minutes');
    criticalTimeEndView = dayjs.duration(30, 'seconds');
    testExam: boolean;
    isTestRun: boolean;

    private previousHeight: number;
    examTitle: string;
    exercises: Exercise[] = [];

    ngOnInit(): void {
        this.examTitle = this.exam.title ?? '';
        this.exercises = this.studentExam.exercises ?? [];
        this.testExam = this.exam.testExam ?? false;
        this.isTestRun = this.studentExam.testRun ?? false;
    }

    /**
     * It sets up a ResizeObserver to monitor changes in the height of the exam bar element.
     * When a change in height is detected, it triggers the onHeightChange method,
     * passing the new height as an argument.
     */
    ngAfterViewInit(): void {
        const barElement = this.elementRef.nativeElement.querySelector('.exam-bar');
        this.previousHeight = barElement.offsetHeight;

        const resizeObserver = new ResizeObserver((entries) => {
            for (const entry of entries) {
                if (entry.target === barElement) {
                    const newHeight = entry.contentRect.height;
                    if (newHeight !== this.previousHeight) {
                        this.previousHeight = newHeight;
                        this.onHeightChange(newHeight);
                    }
                }
            }
        });
        resizeObserver.observe(barElement);
    }
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
        // TODO: The 'emit' function requires a mandatory void argument
        this.examAboutToEnd.emit();
    }

    /**
     * Notify parent component when user wants to hand in early
     */
    handInEarly() {
        // TODO: The 'emit' function requires a mandatory void argument
        this.onExamHandInEarly.emit();
    }

    /**
     * Notify parent component when the height of the bar changes
     */
    onHeightChange(newHeight: number) {
        this.heightChange.emit(newHeight);
    }
}
