import { Component, Input, OnChanges, SimpleChanges, inject } from '@angular/core';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/entities/programming/programming-exercise.model';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';
import { TheiaService } from 'app/exercises/programming/shared/service/theia.service';
import { ArtemisSharedLibsModule } from 'app/shared/shared-libs.module';

@Component({
    selector: 'jhi-programming-exercise-theia',
    templateUrl: './programming-exercise-theia.component.html',
    styleUrls: ['../../../programming-exercise-form.scss'],
    standalone: true,
    imports: [ArtemisSharedLibsModule],
})
export class ProgrammingExerciseTheiaComponent implements OnChanges {
    private theiaService = inject(TheiaService);

    @Input() programmingExercise: ProgrammingExercise;
    @Input() programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;

    programmingLanguage?: ProgrammingLanguage;
    theiaImages = {};

    ngOnChanges(changes: SimpleChanges) {
        if ((changes.programmingExerciseCreationConfig || changes.programmingExercise) && this.shouldReloadTemplate()) {
            this.loadTheiaImages();
        }
    }

    onTheiaImageChange(theiaImage: string) {
        if (this.programmingExercise.buildConfig) {
            this.programmingExercise.buildConfig.theiaImage = theiaImage;
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
        if (this.programmingExercise.buildConfig) {
            this.programmingExercise.buildConfig.theiaImage = undefined;
        }
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
                if (!images) {
                    // Remove selection if no image is available
                    this.theiaImages = {};
                    this.resetImageSelection();
                    return;
                }

                this.theiaImages = images;

                // Set the first image as default if none is selected
                if (this.programmingExercise && this.programmingExercise.buildConfig && !this.programmingExercise.buildConfig.theiaImage && Object.values(images).length > 0) {
                    this.programmingExercise.buildConfig.theiaImage = Object.values(images).first() as string;
                }
            },
            error: () => {
                this.theiaImages = {};
                this.resetImageSelection();
            },
        });
    }
}
