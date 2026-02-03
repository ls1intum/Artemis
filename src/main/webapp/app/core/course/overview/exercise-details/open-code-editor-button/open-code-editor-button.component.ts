import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { faFolderOpen } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { RouterLink } from '@angular/router';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ExerciseActionButtonComponent } from 'app/shared/components/buttons/exercise-action-button/exercise-action-button.component';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';

@Component({
    selector: 'jhi-open-code-editor-button',
    templateUrl: './open-code-editor-button.component.html',
    imports: [ExerciseActionButtonComponent, FeatureToggleDirective, RouterLink, NgbPopover, TranslateDirective, ArtemisTranslatePipe],
})
export class OpenCodeEditorButtonComponent {
    private participationService = inject(ParticipationService);

    readonly FeatureToggle = FeatureToggle;

    readonly loading = input(false);
    readonly smallButtons = input<boolean>(undefined!);
    readonly participations = input<ProgrammingExerciseStudentParticipation[]>(undefined!);
    readonly courseAndExerciseNavigationUrlSegment = input<any[]>(undefined!);
    readonly exercise = input<Exercise>(undefined!);
    readonly hideLabelMobile = input(false);

    private readonly _courseAndExerciseNavigationUrl = signal<string>('');
    private readonly _activeParticipation = signal<ProgrammingExerciseStudentParticipation | undefined>(undefined);
    private readonly _isPracticeMode = signal(true);

    readonly courseAndExerciseNavigationUrl = computed(() => this._courseAndExerciseNavigationUrl());
    readonly activeParticipation = computed(() => this._activeParticipation());
    readonly isPracticeMode = computed(() => this._isPracticeMode());

    // Icons
    faFolderOpen = faFolderOpen;

    constructor() {
        effect(() => {
            const courseAndExerciseNavigationUrlSegment = this.courseAndExerciseNavigationUrlSegment();
            const exercise = this.exercise();
            const participations = this.participations();
            untracked(() => {
                if (!courseAndExerciseNavigationUrlSegment || !exercise || !participations) {
                    return;
                }
                this._courseAndExerciseNavigationUrl.set(courseAndExerciseNavigationUrlSegment.reduce((acc, segment) => `${acc}/${segment}`));
                const shouldPreferPractice = this.participationService.shouldPreferPractice(exercise);
                this._activeParticipation.set(this.participationService.getSpecificStudentParticipation(participations, shouldPreferPractice) ?? participations[0]);
            });
        });
    }

    switchPracticeMode() {
        const newPracticeMode = !this._isPracticeMode();
        this._isPracticeMode.set(newPracticeMode);
        this._activeParticipation.set(this.participationService.getSpecificStudentParticipation(this.participations()!, newPracticeMode)!);
    }
}
