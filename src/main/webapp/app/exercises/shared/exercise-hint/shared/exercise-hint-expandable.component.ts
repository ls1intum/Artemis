import { Component, Input, OnInit, ViewEncapsulation } from '@angular/core';
import { faCheckCircle, faInfoCircle } from '@fortawesome/free-solid-svg-icons';

import { ExerciseHint } from 'app/entities/hestia/exercise-hint.model';
import { ExerciseHintResponse, ExerciseHintService } from 'app/exercises/shared/exercise-hint/shared/exercise-hint.service';

/**
 * This component is a modal that shows the exercise's hints.
 */
@Component({
    selector: 'jhi-exercise-hint-expandable',
    templateUrl: './exercise-hint-expandable.component.html',
    styleUrls: ['./exercise-hint-expandable.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class ExerciseHintExpandableComponent implements OnInit {
    @Input() exerciseHint: ExerciseHint;

    @Input() alreadyActivated: boolean;

    expanded = false;
    isLoading = false;

    faCheckCircle = faCheckCircle;
    faInfoCircle = faInfoCircle;

    constructor(private exerciseHintService: ExerciseHintService) {}

    ngOnInit(): void {}

    displayHintContent() {
        this.expanded = true;

        if (this.alreadyActivated) {
            // the hint already contains the content
            return;
        }

        this.isLoading = true;
        this.exerciseHintService.find(this.exerciseHint!.exercise!.id!, this.exerciseHint!.id!).subscribe((res: ExerciseHintResponse) => {
            this.exerciseHint = res.body!;
            this.alreadyActivated = true;
            this.isLoading = false;
        });
    }

    collapseContent() {
        this.expanded = false;
    }
}
