import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';
import { TheiaService } from 'app/exercises/programming/shared/service/theia.service';

@Component({
    selector: 'jhi-programming-exercise-theia',
    templateUrl: './programming-exercise-theia.component.html',
    styleUrls: ['../../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseTheiaComponent implements OnChanges {
    @Input() programmingExercise: ProgrammingExercise;
    @Input() programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;

    programmingLanguage?: ProgrammingLanguage;
    theiaImages = {};

    constructor(private theiaService: TheiaService) {
        this.loadTheiaImages();
    }

    ngOnChanges(changes: SimpleChanges) {
        if (changes.programmingExerciseCreationConfig || changes.programmingExercise) {
            if (this.shouldReloadTemplate()) {
                this.loadTheiaImages();
                this.resetImageSelection();
            }
        }
    }

    shouldReloadTemplate(): boolean {
        return this.programmingExercise.programmingLanguage !== this.programmingLanguage;
    }

    /**
     * In case the programming language or project type changes, we need to reset the template and the build plan
     * @private
     */
    resetImageSelection() {
        console.log('Resetting image selection');
        this.programmingExercise.theiaImage = undefined;
    }

    /**
     * Loads the predefined template for the selected programming language if there is one available.
     * @private
     */
    loadTheiaImages() {
        if (!this.programmingExercise || !this.programmingExercise.programmingLanguage) {
            return;
        }

        this.programmingLanguage = this.programmingExercise.programmingLanguage;

        this.theiaService.getTheiaImages(this.programmingLanguage).subscribe({
            next: (images) => {
                this.theiaImages = images;
                if (!this.programmingExercise.theiaImage && Object.values(images).length > 0) {
                    this.programmingExercise.theiaImage = Object.values(images)[0] as string;
                }
            },
            error: () => {
                this.theiaImages = {};
                this.programmingExercise.theiaImage = undefined;
            },
        });
    }
}
