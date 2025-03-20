import { Component, Input, OnChanges, inject } from '@angular/core';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { faFolderOpen } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseActionButtonComponent } from '../exercise-action-button.component';
import { FeatureToggleDirective } from '../../feature-toggle/feature-toggle.directive';
import { RouterLink } from '@angular/router';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-open-code-editor-button',
    templateUrl: './open-code-editor-button.component.html',
    imports: [ExerciseActionButtonComponent, FeatureToggleDirective, RouterLink, NgbPopover, TranslateDirective, ArtemisTranslatePipe],
})
export class OpenCodeEditorButtonComponent implements OnChanges {
    private participationService = inject(ParticipationService);

    readonly FeatureToggle = FeatureToggle;

    @Input()
    loading = false;
    @Input()
    smallButtons: boolean;
    @Input()
    participations: ProgrammingExerciseStudentParticipation[];
    @Input()
    courseAndExerciseNavigationUrlSegment: any[];
    @Input()
    exercise: Exercise;
    @Input()
    hideLabelMobile = false;

    courseAndExerciseNavigationUrl: string;
    activeParticipation: ProgrammingExerciseStudentParticipation;
    isPracticeMode = true;

    // Icons
    faFolderOpen = faFolderOpen;

    ngOnChanges() {
        this.courseAndExerciseNavigationUrl = this.courseAndExerciseNavigationUrlSegment.reduce((acc, segment) => `${acc}/${segment}`);
        const shouldPreferPractice = this.participationService.shouldPreferPractice(this.exercise);
        this.activeParticipation = this.participationService.getSpecificStudentParticipation(this.participations, shouldPreferPractice) ?? this.participations[0];
    }

    switchPracticeMode() {
        this.isPracticeMode = !this.isPracticeMode;
        this.activeParticipation = this.participationService.getSpecificStudentParticipation(this.participations!, this.isPracticeMode)!;
    }
}
