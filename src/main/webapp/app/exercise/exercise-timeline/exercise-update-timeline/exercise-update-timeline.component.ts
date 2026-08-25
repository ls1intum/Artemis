import { Component, OnInit, computed, effect, input, model, output, signal } from '@angular/core';
import { NgStyle } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Dayjs } from 'dayjs/esm';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TimelineComponent, TimelineItem, TimelineStatus } from 'app/shared-ui/timeline/timeline.component';

/**
 * Grading timeline shared by the update forms of the manually assessed exercise types (modeling, text, file upload).
 *
 * Mirrors {@link ProgrammingExerciseUpdateTimelineComponent}: the example solution publication date is an opt-in step
 * of the timeline rather than a standalone field, so every exercise type configures and orders all of its dates in one
 * place. The opt-in is disabled rather than hidden while {@link hasExampleSolution} is false, with a hint that says
 * why, and turning it off clears the date so no exercise can be saved with a leftover publication date.
 */
@Component({
    selector: 'jhi-exercise-update-timeline',
    templateUrl: './exercise-update-timeline.component.html',
    styleUrl: './exercise-update-timeline.component.scss',
    imports: [FormsModule, NgStyle, TranslateDirective, TimelineComponent],
})
export class ExerciseUpdateTimelineComponent implements OnInit {
    readonly hasExampleSolution = input(false);
    /** The server resets the publication date on import, so the opt-in is inert there (as for programming exercises). */
    readonly isImport = input(false);
    /** Dates are governed by the exercise's variant group; see {@link TimelineComponent}. */
    readonly lockedToGroup = input(false);
    readonly lockedClick = output<void>();

    readonly releaseDate = model<Dayjs | undefined>();
    readonly startDate = model<Dayjs | undefined>();
    readonly dueDate = model<Dayjs | undefined>();
    readonly assessmentDueDate = model<Dayjs | undefined>();
    readonly exampleSolutionPublicationDate = model<Dayjs | undefined>();

    readonly timelineStatus = output<TimelineStatus>();

    readonly canConfigureExampleSolutionPublication = computed(() => this.hasExampleSolution() && !this.isImport() && !this.lockedToGroup());
    readonly isExampleSolutionPublicationDateVisible = signal(false);

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
        effect(() => {
            if (!this.canConfigureExampleSolutionPublication()) {
                this.isExampleSolutionPublicationDateVisible.set(false);
            }
            if (!this.isExampleSolutionPublicationDateVisible()) {
                this.exampleSolutionPublicationDate.set(undefined);
            }
        });
    }

    ngOnInit(): void {
        // Show the picker for an already configured date, so editing an exercise starts from its persisted state.
        this.isExampleSolutionPublicationDateVisible.set(this.exampleSolutionPublicationDate() !== undefined);
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
                // Only the release and start dates are hard lower bounds server side
                // (`BaseExercise.isValidExampleSolutionPublicationDate`). The due-date bound is conditional — an
                // exercise not included in the score may publish its solution earlier — and the assessment due date is
                // never a bound at all. Without this restriction the default "after every preceding item" check
                // rejected schedules the server accepts.
                orderCheckAgainst: [releaseDateItem, startDateItem],
            });
        }
        return timelineItems;
    }
}
