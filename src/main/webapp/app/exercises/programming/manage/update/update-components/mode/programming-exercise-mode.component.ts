import { Component, EventEmitter, Input, OnInit, Output, ViewChild, inject, input } from '@angular/core';
import { ProgrammingExercise, ProjectType } from 'app/entities/programming/programming-exercise.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';
import { TeamConfigFormGroupComponent } from 'app/exercises/shared/team-config-form-group/team-config-form-group.component';
import { PROFILE_THEIA } from 'app/app.constants';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ProgrammingExerciseInputField } from 'app/exercises/programming/manage/update/programming-exercise-update.helper';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ProgrammingExerciseDifficultyComponent } from '../difficulty/programming-exercise-difficulty.component';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-programming-exercise-mode',
    templateUrl: './programming-exercise-mode.component.html',
    styleUrls: ['../../../programming-exercise-form.scss'],
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

    @Input({ required: true }) programmingExercise: ProgrammingExercise;
    @Input({ required: true }) programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;
    isEditFieldDisplayedRecord = input.required<Record<ProgrammingExerciseInputField, boolean>>();

    @ViewChild(TeamConfigFormGroupComponent) teamConfigComponent: TeamConfigFormGroupComponent;

    @Output() triggerValidation = new EventEmitter<void>();

    theiaEnabled = false;

    ngOnInit(): void {
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            this.theiaEnabled = profileInfo.activeProfiles?.includes(PROFILE_THEIA);
        });
    }
}
