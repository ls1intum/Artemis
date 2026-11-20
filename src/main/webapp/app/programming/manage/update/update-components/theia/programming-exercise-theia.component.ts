import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiSelectComponent } from '@tumaet/ui-angular';
import { getCurrentLocaleSignal } from 'app/foundation/util/global.utils';
import { FormsModule } from '@angular/forms';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { OnlineIdeExperienceLevel } from 'app/programming/shared/entities/programming-exercise-build.config';
import { ProgrammingExerciseCreationConfig } from 'app/programming/manage/update/programming-exercise-creation-config';
import { TheiaService } from 'app/programming/shared/services/theia.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';

@Component({
    selector: 'jhi-programming-exercise-theia',
    templateUrl: './programming-exercise-theia.component.html',
    styleUrls: ['../../../../shared/programming-exercise-form.scss'],
    imports: [FormsModule, TranslateDirective, ArtemisTranslatePipe, HelpIconComponent, TumAetUiSelectComponent],
})
export class ProgrammingExerciseTheiaComponent {
    private theiaService = inject(TheiaService);
    private readonly translateService = inject(TranslateService);
    private readonly currentLocale = getCurrentLocaleSignal(this.translateService);

    readonly programmingExercise = input.required<ProgrammingExercise>();
    readonly programmingExerciseCreationConfig = input.required<ProgrammingExerciseCreationConfig>();

    programmingLanguage?: ProgrammingLanguage;
    readonly theiaImages = signal<object>({});
    readonly experienceLevels = Object.values(OnlineIdeExperienceLevel);

    /** The entries of the image select: the name of each image the server offers for the language. */
    readonly theiaImageOptions = computed(() => Object.entries(this.theiaImages()).map(([label, value]) => ({ label, value: value as string })));

    /** The entries of the experience level select: "not set" first, then every level. */
    readonly experienceLevelOptions = computed(() => {
        this.currentLocale();
        const prefix = 'artemisApp.programmingExercise.onlineIdeExperienceLevel.';
        return [
            { value: undefined as OnlineIdeExperienceLevel | undefined, label: this.translateService.instant(prefix + 'notSet') as string },
            ...this.experienceLevels.map((level): { value: OnlineIdeExperienceLevel | undefined; label: string } => ({
                value: level,
                label: this.translateService.instant(prefix + 'levels.' + level) as string,
            })),
        ];
    });

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

    onExperienceLevelChange(experienceLevel: OnlineIdeExperienceLevel | undefined) {
        if (this.programmingExercise().buildConfig) {
            this.programmingExercise().buildConfig!.onlineIdeExperienceLevel = experienceLevel;
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
                    this.theiaImages.set({});
                    this.resetImageSelection();
                    return;
                }

                this.theiaImages.set(images);

                // Set the first image as default if none is selected
                if (programmingExercise.buildConfig && !programmingExercise.buildConfig.theiaImage && Object.values(images).length > 0) {
                    programmingExercise.buildConfig.theiaImage = Object.values(images).first();
                }
            },
            error: () => {
                this.theiaImages.set({});
                this.resetImageSelection();
            },
        });
    }
}
