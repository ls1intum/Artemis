import { AfterViewInit, Component, ElementRef, OnDestroy, computed, inject, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';

import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faDoorClosed } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { Exam, isTestExam } from 'app/exam/shared/entities/exam.model';
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
export class ExamBarComponent implements AfterViewInit, OnDestroy {
    private readonly elementRef = inject(ElementRef);

    protected readonly faDoorClosed = faDoorClosed;

    readonly onExamHandInEarly = output<void>();
    readonly examAboutToEnd = output<void>();
    readonly heightChange = output<number>();

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
    readonly testExam = computed(() => isTestExam(this.exam()));
    readonly isTestRun = computed(() => this.studentExam()?.testRun ?? false);
    readonly examTitle = computed(() => this.exam()?.title ?? '');
    readonly exercises = computed<Exercise[]>(() => this.studentExam()?.exercises ?? []);

    private previousHeight: number;
    private resizeObserver: ResizeObserver | undefined;

    /**
     * It sets up a ResizeObserver to monitor changes in the height of the exam bar element.
     * When a change in height is detected, it triggers the onHeightChange method,
     * passing the new height as an argument.
     */
    ngAfterViewInit(): void {
        const barElement = this.elementRef.nativeElement.querySelector('.exam-bar');
        this.previousHeight = barElement.offsetHeight;

        this.resizeObserver = new ResizeObserver((entries) => {
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
        this.resizeObserver.observe(barElement);
    }

    ngOnDestroy(): void {
        this.resizeObserver?.disconnect();
    }

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

    /**
     * Notify parent component when the height of the bar changes
     */
    onHeightChange(newHeight: number) {
        this.heightChange.emit(newHeight);
    }
}
