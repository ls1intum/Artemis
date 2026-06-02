import { KeyValuePipe } from '@angular/common';
import { Component, effect, inject, input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseCreationConfig } from 'app/programming/manage/update/programming-exercise-creation-config';
import { TheiaService } from 'app/programming/shared/services/theia.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-programming-exercise-theia',
    templateUrl: './programming-exercise-theia.component.html',
    styleUrls: ['../../../../shared/programming-exercise-form.scss'],
    imports: [FormsModule, KeyValuePipe, TranslateDirective],
})
export class ProgrammingExerciseTheiaComponent {
    private theiaService = inject(TheiaService);

    readonly programmingExercise = input.required<ProgrammingExercise>();
    readonly programmingExerciseCreationConfig = input.required<ProgrammingExerciseCreationConfig>();

    programmingLanguage?: ProgrammingLanguage;
    theiaImages = {};

    constructor() {
        // Replicates the previous ngOnChanges behavior: whenever either input changes, reload the
        // Theia images if (and only if) the programming language differs from the one last loaded.
        // Reading both inputs registers the effect as a dependency on each of them.
        effect(() => {
            this.programmingExercise();
            this.programmingExerciseCreationConfig();
            if (this.shouldReloadTemplate()) {
                this.loadTheiaImages();
            }
        });
    }

    onTheiaImageChange(theiaImage: string) {
        if (this.programmingExercise().buildConfig) {
            this.programmingExercise().buildConfig!.theiaImage = theiaImage;
        }
    }

    shouldReloadTemplate(): boolean {
        return this.programmingExercise().programmingLanguage !== this.programmingLanguage;
    }

    /**
     * In case the programming language or project type changes, we need to reset the template and the build plan
     * @private
     */
    resetImageSelection() {
        if (this.programmingExercise().buildConfig) {
            this.programmingExercise().buildConfig!.theiaImage = undefined;
        }
    }

    /**
     * Loads the predefined template for the selected programming language if there is one available.
     * @private
     */
    loadTheiaImages() {
        const programmingExercise = this.programmingExercise();
        if (!programmingExercise || !programmingExercise.programmingLanguage) {
            return;
        }

        this.programmingLanguage = programmingExercise.programmingLanguage;

        this.theiaService.getTheiaImages(this.programmingLanguage).subscribe({
            next: (images) => {
                if (!images) {
                    // Remove selection if no image is available
                    this.theiaImages = {};
                    this.resetImageSelection();
                    return;
                }

                this.theiaImages = images;

                // Set the first image as default if none is selected
                if (programmingExercise.buildConfig && !programmingExercise.buildConfig.theiaImage && Object.values(images).length > 0) {
                    programmingExercise.buildConfig.theiaImage = Object.values(images).first() as string;
                }
            },
            error: () => {
                this.theiaImages = {};
                this.resetImageSelection();
            },
        });
    }
}
