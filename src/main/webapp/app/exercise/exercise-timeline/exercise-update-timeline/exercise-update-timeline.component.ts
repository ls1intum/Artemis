import { Component, computed, effect, input, linkedSignal, model, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Dayjs } from 'dayjs/esm';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TimelineComponent, TimelineItem, TimelineStatus } from 'app/shared-ui/timeline/timeline.component';
import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';

/** Grading timeline shared by manually assessed exercise update forms. */
@Component({
    selector: 'jhi-exercise-update-timeline',
    templateUrl: './exercise-update-timeline.component.html',
    styleUrl: './exercise-update-timeline.component.scss',
    imports: [FormsModule, TranslateDirective, TimelineComponent],
})
export class ExerciseUpdateTimelineComponent {
    readonly hasExampleSolution = input(false);
    /** The server resets the publication date on import, so the opt-in is inert there (as for programming exercises). */
    readonly isImport = input(false);
    /** Dates are governed by the exercise's variant group; see {@link TimelineComponent}. */
    readonly lockedToGroup = input(false);
    readonly includedInOverallScore = input<IncludedInOverallScore | undefined>(IncludedInOverallScore.INCLUDED_COMPLETELY);
    readonly lockedClick = output<void>();

    readonly releaseDate = model<Dayjs | undefined>();
    readonly startDate = model<Dayjs | undefined>();
    readonly dueDate = model<Dayjs | undefined>();
    readonly assessmentDueDate = model<Dayjs | undefined>();
    readonly exampleSolutionPublicationDate = model<Dayjs | undefined>();

    readonly timelineStatus = output<TimelineStatus>();

    readonly canConfigureExampleSolutionPublication = computed(() => this.hasExampleSolution() && !this.isImport() && !this.lockedToGroup());
    private readonly exampleSolutionPublicationState = computed(() => {
        const hasDate = this.exampleSolutionPublicationDate() !== undefined;
        return {
            canShow: !this.isImport() && !this.lockedToGroup() && (this.hasExampleSolution() || hasDate),
            hasDate,
        };
    });

    readonly isExampleSolutionPublicationDateVisible = linkedSignal<{ canShow: boolean; hasDate: boolean }, boolean>({
        source: this.exampleSolutionPublicationState,
        computation: (state, previous) => {
            if (!state.canShow) {
                return false;
            }
            return previous?.source.canShow ? previous.value : state.hasDate;
        },
    });

    /** Explains a disabled opt-in; `undefined` while the opt-in is usable. */
    readonly exampleSolutionPublicationHintKey = computed<string | undefined>(() => {
        if (this.isImport()) {
            return 'artemisApp.exercise.exampleSolutionPublicationDateImportInfo';
        }
        if (this.lockedToGroup()) {
            return 'artemisApp.exercise.exampleSolutionPublicationDateLockedToGroup';
        }
        if (!this.hasExampleSolution()) {
            return 'artemisApp.exercise.exampleSolutionPublicationDateRequiresExampleSolution';
        }
        return undefined;
    });

    readonly timelineItems = computed<TimelineItem[]>(() => this.computeTimelineItems());

    constructor() {
        let previouslyHadExampleSolution: boolean | undefined;
        effect(() => {
            const hasExampleSolution = this.hasExampleSolution();
            if (previouslyHadExampleSolution && !hasExampleSolution) {
                this.exampleSolutionPublicationDate.set(undefined);
            }
            previouslyHadExampleSolution = hasExampleSolution;
        });
    }

    protected onPublicationOptInChange(visible: boolean): void {
        this.isExampleSolutionPublicationDateVisible.set(visible);
        if (!visible) {
            this.exampleSolutionPublicationDate.set(undefined);
        }
    }

    private computeTimelineItems(): TimelineItem[] {
        const releaseDateItem: TimelineItem = {
            kind: 'optional',
            labelStringKey: 'artemisApp.exercise.releaseDate',
            date: this.releaseDate,
        };
        const startDateItem: TimelineItem = {
            kind: 'optional',
            labelStringKey: 'artemisApp.exercise.startDate',
            date: this.startDate,
        };
        const dueDateItem: TimelineItem = {
            kind: 'optional',
            labelStringKey: 'artemisApp.exercise.dueDate',
            date: this.dueDate,
        };
        const timelineItems: TimelineItem[] = [
            releaseDateItem,
            startDateItem,
            dueDateItem,
            {
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.assessmentDueDate',
                date: this.assessmentDueDate,
                otherRequiredItem: dueDateItem,
            },
        ];
        if (this.isExampleSolutionPublicationDateVisible()) {
            timelineItems.push({
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.exampleSolutionPublicationDate',
                date: this.exampleSolutionPublicationDate,
                orderCheckAgainst:
                    this.includedInOverallScore() === IncludedInOverallScore.NOT_INCLUDED ? [releaseDateItem, startDateItem] : [releaseDateItem, startDateItem, dueDateItem],
            });
        }
        return timelineItems;
    }
}
