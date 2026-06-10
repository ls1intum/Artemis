import { Component, OnInit, inject, input, output, viewChild } from '@angular/core';
import { ProgrammingExercise, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseCreationConfig } from 'app/programming/manage/update/programming-exercise-creation-config';
import { TeamConfigFormGroupComponent } from 'app/exercise/team-config-form-group/team-config-form-group.component';
import { MODULE_FEATURE_THEIA } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ProgrammingExerciseInputField } from 'app/programming/manage/update/programming-exercise-update.helper';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ProgrammingExerciseDifficultyComponent } from '../difficulty/programming-exercise-difficulty.component';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-programming-exercise-mode',
    templateUrl: './programming-exercise-mode.component.html',
    styleUrls: ['../../../../shared/programming-exercise-form.scss'],
    imports: [
        TranslateDirective,
        ProgrammingExerciseDifficultyComponent,
        TeamConfigFormGroupComponent,
        FormsModule,
        FaIconComponent,
        NgbTooltip,
        HelpIconComponent,
        ArtemisTranslatePipe,
    ],
})
export class ProgrammingExerciseModeComponent implements OnInit {
    private profileService = inject(ProfileService);

    protected readonly ProjectType = ProjectType;
    protected readonly faQuestionCircle = faQuestionCircle;

    readonly programmingExercise = input.required<ProgrammingExercise>();
    readonly programmingExerciseCreationConfig = input.required<ProgrammingExerciseCreationConfig>();
    readonly isEditFieldDisplayedRecord = input.required<Record<ProgrammingExerciseInputField, boolean>>();

    // Migrated from @ViewChild. The parent (ProgrammingExerciseUpdateComponent) reads this as a plain
    // property (`...?.teamConfigComponent?.formValidChanges`), so the public contract must stay a
    // property that returns the queried component instance. A private signal backs a public getter.
    private readonly teamConfigComponentSignal = viewChild(TeamConfigFormGroupComponent);

    get teamConfigComponent(): TeamConfigFormGroupComponent | undefined {
        return this.teamConfigComponentSignal();
    }

    readonly triggerValidation = output<void>();

    theiaEnabled = false;

    ngOnInit(): void {
        this.theiaEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_THEIA);
    }
}
