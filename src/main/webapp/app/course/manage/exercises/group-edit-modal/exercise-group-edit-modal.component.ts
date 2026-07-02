import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleInfo } from '@fortawesome/free-solid-svg-icons';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import dayjs from 'dayjs/esm';
import { CourseExerciseGroup } from 'app/exercise/shared/entities/exercise/course-exercise-group.model';
import { ExerciseTimelineComponent, ExerciseTimelineStatus, TimelineItem } from 'app/exercise/exercise-timeline/exercise-timeline.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

/**
 * Content of the group-edit dialog, opened via PrimeNG's {@code DialogService} (see
 * {@code ExerciseGroupTimelineLockComponent.openModal} and {@code CourseManagementExercisesComponent.openGroupEditDialog}).
 * The edited group is passed in through the dialog's {@code inputValues.group}; saving closes the dialog with the updated
 * {@link CourseExerciseGroup} as result, cancelling closes it with {@code undefined}.
 */
@Component({
    selector: 'jhi-exercise-group-edit-modal',
    templateUrl: './exercise-group-edit-modal.component.html',
    imports: [FormsModule, InputTextModule, InputNumberModule, ButtonModule, TooltipModule, FaIconComponent, ExerciseTimelineComponent, ArtemisTranslatePipe, TranslateDirective],
})
export class ExerciseGroupEditModalComponent {
    protected readonly faCircleInfo = faCircleInfo;

    /** The group being edited, supplied by the dialog opener via {@code inputValues.group}. */
    readonly group = input.required<CourseExerciseGroup>();

    private readonly dialogRef = inject(DynamicDialogRef);

    readonly draftTitle = signal('');
    readonly draftMaxPoints = signal<number | undefined>(undefined);
    readonly draftReleaseDate = signal<dayjs.Dayjs | undefined>(undefined);
    readonly draftStartDate = signal<dayjs.Dayjs | undefined>(undefined);
    readonly draftDueDate = signal<dayjs.Dayjs | undefined>(undefined);
    readonly draftAssessmentDueDate = signal<dayjs.Dayjs | undefined>(undefined);
    readonly draftExampleSolutionPublicationDate = signal<dayjs.Dayjs | undefined>(undefined);
    readonly draftBuildAndTestStudentSubmissionsAfterDueDate = signal<dayjs.Dayjs | undefined>(undefined);

    readonly timelineItems = computed<TimelineItem[]>(() => [
        { kind: 'optional', labelStringKey: 'artemisApp.exercise.releaseDate', date: this.draftReleaseDate },
        { kind: 'optional', labelStringKey: 'artemisApp.exercise.startDate', date: this.draftStartDate },
        { kind: 'optional', labelStringKey: 'artemisApp.exercise.dueDate', date: this.draftDueDate },
        { kind: 'optional', labelStringKey: 'artemisApp.exercise.dateForRunningTestsAfterDueDate', date: this.draftBuildAndTestStudentSubmissionsAfterDueDate },
        { kind: 'optional', labelStringKey: 'artemisApp.exercise.assessmentDueDate', date: this.draftAssessmentDueDate },
        { kind: 'optional', labelStringKey: 'artemisApp.exercise.exampleSolutionPublicationDate', date: this.draftExampleSolutionPublicationDate },
    ]);

    readonly isTitleValid = computed(() => this.draftTitle().trim().length > 0);
    readonly timelineStatus = signal<ExerciseTimelineStatus>({ valid: true, empty: true });
    readonly isSaveDisabled = computed(() => !this.isTitleValid() || !this.timelineStatus().valid);

    constructor() {
        effect(() => {
            const g = this.group();
            this.draftTitle.set(g.title ?? '');
            this.draftMaxPoints.set(g.maxPoints);
            // The group's dates are typed as dayjs but arrive as ISO strings at runtime: the exercise's date
            // deserialization does not reach the nested variant-group reference. Coerce so the timeline (which calls
            // dayjs.toDate()) receives real dayjs objects. See {@link ExerciseTimelineComponent}.
            this.draftReleaseDate.set(toDayjs(g.releaseDate));
            this.draftStartDate.set(toDayjs(g.startDate));
            this.draftDueDate.set(toDayjs(g.dueDate));
            this.draftAssessmentDueDate.set(toDayjs(g.assessmentDueDate));
            this.draftExampleSolutionPublicationDate.set(toDayjs(g.exampleSolutionPublicationDate));
            this.draftBuildAndTestStudentSubmissionsAfterDueDate.set(toDayjs(g.buildAndTestStudentSubmissionsAfterDueDate));
        });
    }

    onSave(): void {
        const updated: CourseExerciseGroup = {
            ...this.group(),
            title: this.draftTitle().trim(),
            maxPoints: this.draftMaxPoints(),
            releaseDate: this.draftReleaseDate(),
            startDate: this.draftStartDate(),
            dueDate: this.draftDueDate(),
            assessmentDueDate: this.draftAssessmentDueDate(),
            exampleSolutionPublicationDate: this.draftExampleSolutionPublicationDate(),
            buildAndTestStudentSubmissionsAfterDueDate: this.draftBuildAndTestStudentSubmissionsAfterDueDate(),
        };
        // Nothing edited: close with no result so the openers treat it as a cancel and skip the persistence call.
        this.dialogRef.close(this.isUnchanged(updated) ? undefined : updated);
    }

    onCancel(): void {
        this.dialogRef.close();
    }

    /** True when the drafted values match the original group (dates compared as dayjs, accounting for the string inputs). */
    private isUnchanged(updated: CourseExerciseGroup): boolean {
        const g = this.group();
        return (
            (updated.title ?? '') === (g.title ?? '') &&
            updated.maxPoints === g.maxPoints &&
            datesEqual(updated.releaseDate, toDayjs(g.releaseDate)) &&
            datesEqual(updated.startDate, toDayjs(g.startDate)) &&
            datesEqual(updated.dueDate, toDayjs(g.dueDate)) &&
            datesEqual(updated.assessmentDueDate, toDayjs(g.assessmentDueDate)) &&
            datesEqual(updated.exampleSolutionPublicationDate, toDayjs(g.exampleSolutionPublicationDate)) &&
            datesEqual(updated.buildAndTestStudentSubmissionsAfterDueDate, toDayjs(g.buildAndTestStudentSubmissionsAfterDueDate))
        );
    }
}

/** Compares two optional dayjs values by instant, treating both-undefined as equal. */
function datesEqual(a: dayjs.Dayjs | undefined, b: dayjs.Dayjs | undefined): boolean {
    if (a === undefined || b === undefined) {
        return a === b;
    }
    return a.isSame(b);
}

/** Coerces a value that is typed as dayjs but may arrive as an ISO string / Date into a valid dayjs (or undefined). */
function toDayjs(value: dayjs.Dayjs | string | Date | undefined): dayjs.Dayjs | undefined {
    if (value === undefined) {
        return undefined;
    }
    const parsed = dayjs(value);
    return parsed.isValid() ? parsed : undefined;
}
