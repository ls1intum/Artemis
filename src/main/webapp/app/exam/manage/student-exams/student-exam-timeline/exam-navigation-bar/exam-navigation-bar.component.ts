import { AfterViewInit, Component, OnInit, inject, input, output, signal } from '@angular/core';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { LayoutService } from 'app/foundation/breakpoints/layout.service';
import { CustomBreakpointNames } from 'app/foundation/breakpoints/breakpoints.service';
import { faCheck } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { FileUploadSubmission } from 'app/fileupload/shared/entities/file-upload-submission.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ExamLiveEventsButtonComponent } from 'app/exam/overview/events/button/exam-live-events-button.component';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { SubmissionVersion } from 'app/exam/shared/entities/submission-version.model';

/**
 * Exercise navigation for the instructor-facing student exam timeline, where a tutor or instructor replays a
 * student's exam submission by submission.
 *
 * This bar used to double as the student navigation during exam conduction, which is why it once also owned a save
 * button, a hand-in-early button, an exam timer and a live submission sync state. Students moved to the
 * `ExamNavigationSidebarComponent` and the timeline stayed behind as the only consumer, so all of that had become
 * unreachable and was removed. Every submission shown here is already persisted, hence a button's state only
 * expresses which exercise is currently being viewed, never whether something is saved.
 */
@Component({
    selector: 'jhi-exam-navigation-bar',
    templateUrl: './exam-navigation-bar.component.html',
    styleUrls: ['./exam-navigation-bar.component.scss'],
    imports: [TranslateDirective, ExamLiveEventsButtonComponent, NgClass, FaIconComponent],
})
export class ExamNavigationBarComponent implements OnInit, AfterViewInit {
    private layoutService = inject(LayoutService);

    readonly exercises = input<Exercise[]>([]);
    readonly exerciseIndex = input(0);
    readonly onPageChanged = output<{
        exercise: Exercise;
        submission?: ProgrammingSubmission | SubmissionVersion | FileUploadSubmission;
    }>();

    static itemsVisiblePerSideDefault = 4;
    readonly itemsVisiblePerSide = signal(ExamNavigationBarComponent.itemsVisiblePerSideDefault);

    // Icons
    readonly faCheck = faCheck;

    ngOnInit(): void {
        this.layoutService.subscribeToLayoutChanges().subscribe(() => {
            // You will have all matched breakpoints in observerResponse
            if (this.layoutService.isBreakpointActive(CustomBreakpointNames.extraLarge)) {
                this.itemsVisiblePerSide.set(ExamNavigationBarComponent.itemsVisiblePerSideDefault);
            } else if (this.layoutService.isBreakpointActive(CustomBreakpointNames.large)) {
                this.itemsVisiblePerSide.set(3);
            } else if (this.layoutService.isBreakpointActive(CustomBreakpointNames.medium)) {
                this.itemsVisiblePerSide.set(1);
            } else {
                this.itemsVisiblePerSide.set(0);
            }
        });
    }

    ngAfterViewInit() {
        // Use setTimeout to ensure the DOM is fully loaded before calculating headerHeight
        setTimeout(() => {
            const headerHeight = (document.querySelector('jhi-navbar') as HTMLElement)?.offsetHeight;
            document.documentElement.style.setProperty('--header-height', `${headerHeight}px`);
        });
    }

    /**
     * Switch the timeline to another exercise.
     *
     * @param exerciseIndex index of the exercise to switch to; an out-of-range index is ignored
     * @param submission the submission to be viewed, if a specific one should be shown
     */
    changePage(exerciseIndex: number, submission?: SubmissionVersion | ProgrammingSubmission | FileUploadSubmission): void {
        if (exerciseIndex > this.exercises().length - 1 || exerciseIndex < 0) {
            return;
        }
        this.onPageChanged.emit({ exercise: this.exercises()[exerciseIndex], submission });
    }

    /**
     * The CSS class for an exercise button. Every submission in the timeline is already saved, so the only
     * distinction left is whether the exercise is the one currently being viewed. There is deliberately no
     * "not synced" state here: an unsaved-changes indicator would be meaningless for a submission an instructor
     * is replaying after the fact.
     *
     * @param exerciseIndex index of the exercise the button belongs to
     */
    getExerciseButtonStatus(exerciseIndex: number): 'synced' | 'synced active' {
        return this.exerciseIndex() === exerciseIndex ? 'synced active' : 'synced';
    }
}
