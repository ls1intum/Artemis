import { ChangeDetectionStrategy, Component, computed, effect, input, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleInfo, faCircleXmark, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { TumUiButtonComponent, TumUiDialogComponent, TumUiInputDirective, TumUiInputNumberComponent, TumUiMessageComponent, TumUiTooltipDirective } from '@tumaet/ui-angular';
import dayjs from 'dayjs/esm';
import { CourseExerciseGroup } from 'app/exercise/shared/entities/exercise/course-exercise-group.model';
import { TimelineComponent, TimelineItem, TimelineStatus, TimelineValidationMode } from 'app/shared-ui/timeline/timeline.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

/**
 * Declarative group-edit dialog. The edited group comes in via {@link group} and is shown while {@link visible} is
 * true; saving emits the updated {@link CourseExerciseGroup} on {@link saved} and closes, cancelling just closes.
 */
@Component({
    selector: 'jhi-exercise-group-edit-modal',
    templateUrl: './exercise-group-edit-modal.component.html',
    imports: [
        FormsModule,
        TumUiDialogComponent,
        TumUiInputDirective,
        TumUiInputNumberComponent,
        TumUiButtonComponent,
        TumUiMessageComponent,
        TumUiTooltipDirective,
        FaIconComponent,
        TimelineComponent,
        ArtemisTranslatePipe,
        TranslateDirective,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExerciseGroupEditModalComponent {
    protected readonly faCircleInfo = faCircleInfo;
    protected readonly faTriangleExclamation = faTriangleExclamation;
    protected readonly faCircleXmark = faCircleXmark;
    protected readonly MAX_TITLE_LENGTH = MAX_TITLE_LENGTH;
    protected readonly TimelineValidationMode = TimelineValidationMode;

    /** Two-way visibility, driven by the parent. */
    readonly visible = model<boolean>(false);
    /** The group being edited, supplied by the parent. */
    readonly group = input.required<CourseExerciseGroup>();
    /** True when the group does not exist yet, so the dialog is titled "Create Group" instead of "Edit Group". */
    readonly isNew = input(false);
    /** Emits the edited group on save (only when something actually changed); cancel/close emit nothing. */
    readonly saved = output<CourseExerciseGroup>();

    readonly draftTitle = signal('');
    readonly draftMaxPoints = signal<number | undefined>(undefined);
    readonly draftReleaseDate = signal<dayjs.Dayjs | undefined>(undefined);
    readonly draftStartDate = signal<dayjs.Dayjs | undefined>(undefined);
    readonly draftDueDate = signal<dayjs.Dayjs | undefined>(undefined);
    readonly draftAssessmentDueDate = signal<dayjs.Dayjs | undefined>(undefined);
    readonly draftExampleSolutionPublicationDate = signal<dayjs.Dayjs | undefined>(undefined);

    readonly headerStringKey = computed(() => (this.isNew() ? 'artemisApp.exerciseManagement.groupEdit.createHeader' : 'artemisApp.exerciseManagement.groupEdit.header'));
    private readonly assessmentDueDateErrorStringKey = computed(() =>
        this.draftAssessmentDueDate() !== undefined && this.draftDueDate() === undefined ? 'artemisApp.exercise.assessmentDueDateRequiresDueDate' : undefined,
    );

    readonly timelineItems = computed<TimelineItem[]>(() => this.computeTimelineItems());

    /** Mirrors the server-side constraints: non-blank and at most 255 characters (the title column is varchar(255)). */
    readonly isTitleValid = computed(() => {
        const title = this.draftTitle().trim();
        return title.length > 0 && title.length <= MAX_TITLE_LENGTH;
    });
    readonly timelineStatus = signal<TimelineStatus>({ valid: true, empty: true });
    readonly isSaveDisabled = computed(() => !this.isTitleValid() || !this.timelineStatus().valid);

    constructor() {
        effect(() => {
            const g = this.group();
            this.draftTitle.set(g.title ?? '');
            this.draftMaxPoints.set(g.maxPoints);
            // The group's dates are typed as dayjs but arrive as ISO strings: exercise date deserialization does
            // not reach the nested variant-group reference, so coerce them for the timeline.
            this.draftReleaseDate.set(toDayjs(g.releaseDate));
            this.draftStartDate.set(toDayjs(g.startDate));
            this.draftDueDate.set(toDayjs(g.dueDate));
            this.draftAssessmentDueDate.set(toDayjs(g.assessmentDueDate));
            this.draftExampleSolutionPublicationDate.set(toDayjs(g.exampleSolutionPublicationDate));
        });
    }

    onSave(): void {
        const updated: CourseExerciseGroup = cloneWith(this.group(), {
            title: this.draftTitle().trim(),
            maxPoints: this.draftMaxPoints(),
            releaseDate: this.draftReleaseDate(),
            startDate: this.draftStartDate(),
            dueDate: this.draftDueDate(),
            assessmentDueDate: this.draftAssessmentDueDate(),
            exampleSolutionPublicationDate: this.draftExampleSolutionPublicationDate(),
        });
        // Nothing edited: close without a `saved` event, so the openers skip the persistence call.
        if (!this.isUnchanged(updated)) {
            this.saved.emit(updated);
        }
        this.visible.set(false);
    }

    onCancel(): void {
        this.visible.set(false);
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
            datesEqual(updated.exampleSolutionPublicationDate, toDayjs(g.exampleSolutionPublicationDate))
        );
    }

    private computeTimelineItems(): TimelineItem[] {
        return [
            { kind: 'optional', labelStringKey: 'artemisApp.exercise.releaseDate', date: this.draftReleaseDate },
            { kind: 'optional', labelStringKey: 'artemisApp.exercise.startDate', date: this.draftStartDate },
            { kind: 'optional', labelStringKey: 'artemisApp.exercise.dueDate', date: this.draftDueDate },
            {
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.assessmentDueDate',
                date: this.draftAssessmentDueDate,
                errorStringKey: this.assessmentDueDateErrorStringKey,
            },
            {
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.exampleSolutionPublicationDate',
                date: this.draftExampleSolutionPublicationDate,
            },
        ];
    }
}

/** Maximum group title length, matching the server's @Size(max = 255) constraint and the varchar(255) column. */
const MAX_TITLE_LENGTH = 255;

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
