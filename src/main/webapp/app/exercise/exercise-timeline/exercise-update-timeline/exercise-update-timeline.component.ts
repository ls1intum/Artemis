import { Component, OnInit, computed, effect, input, model, output, signal } from '@angular/core';
import { NgStyle } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Dayjs } from 'dayjs/esm';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ExerciseTimelineComponent, ExerciseTimelineStatus, TimelineItem } from 'app/exercise/exercise-timeline/exercise-timeline.component';

/**
 * Grading timeline shared by the update forms of the manually assessed exercise types
 * (modeling, text, file upload).
 *
 * It mirrors {@link ProgrammingExerciseUpdateTimelineComponent}: the example solution publication date is not a
 * standalone field next to the example solution, it is an opt-in step of the timeline, so that all dates of an
 * exercise are configured (and ordered against each other) in one place, for every exercise type.
 *
 * Publishing an example solution that does not exist is meaningless, so the opt-in is disabled - not hidden -
 * while {@link hasExampleSolution} is false, with a hint that says why. Whenever the opt-in is off the date is
 * cleared, so an exercise without an example solution can never be saved with a leftover publication date.
 */
@Component({
    selector: 'jhi-exercise-update-timeline',
    templateUrl: './exercise-update-timeline.component.html',
    styleUrl: './exercise-update-timeline.component.scss',
    imports: [FormsModule, NgStyle, TranslateDirective, ExerciseTimelineComponent],
})
export class ExerciseUpdateTimelineComponent implements OnInit {
    /** Whether the exercise currently carries an example solution that could be published. */
    readonly hasExampleSolution = input(false);
    /** The server resets the publication date on import, so the opt-in is inert there (as for programming exercises). */
    readonly isImport = input(false);

    readonly releaseDate = model<Dayjs | undefined>();
    readonly startDate = model<Dayjs | undefined>();
    readonly dueDate = model<Dayjs | undefined>();
    readonly assessmentDueDate = model<Dayjs | undefined>();
    readonly exampleSolutionPublicationDate = model<Dayjs | undefined>();

    readonly timelineStatus = output<ExerciseTimelineStatus>();

    readonly canConfigureExampleSolutionPublication = computed(() => this.hasExampleSolution() && !this.isImport());
    readonly isExampleSolutionPublicationDateVisible = signal(false);

    /** Explains a disabled opt-in; `undefined` while the opt-in is usable. */
    readonly exampleSolutionPublicationHintKey = computed<string | undefined>(() => {
        if (this.isImport()) {
            return 'artemisApp.exercise.exampleSolutionPublicationDateImportInfo';
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
        const dueDateItem: TimelineItem = {
            kind: 'optional',
            labelStringKey: 'artemisApp.exercise.dueDate',
            date: this.dueDate,
        };
        const timelineItems: TimelineItem[] = [
            {
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.releaseDate',
                date: this.releaseDate,
            },
            {
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.startDate',
                date: this.startDate,
            },
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
            });
        }
        return timelineItems;
    }
}
