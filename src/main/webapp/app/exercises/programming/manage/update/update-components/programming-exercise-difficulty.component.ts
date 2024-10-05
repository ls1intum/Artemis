import { Component, EventEmitter, Input, OnInit, Output, ViewChild, inject } from '@angular/core';
import { ProgrammingExercise, ProjectType } from 'app/entities/programming/programming-exercise.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';
import { TeamConfigFormGroupComponent } from 'app/exercises/shared/team-config-form-group/team-config-form-group.component';
import { PROFILE_THEIA } from 'app/app.constants';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';

@Component({
    selector: 'jhi-programming-exercise-difficulty',
    templateUrl: './programming-exercise-difficulty.component.html',
    styleUrls: ['../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseDifficultyComponent implements OnInit {
    private profileService = inject(ProfileService);

    @Input() programmingExercise: ProgrammingExercise;
    @Input() programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;
    @ViewChild(TeamConfigFormGroupComponent) teamConfigComponent: TeamConfigFormGroupComponent;

    @Output() triggerValidation = new EventEmitter<void>();

    protected readonly ProjectType = ProjectType;

    theiaEnabled: boolean = false;

    ngOnInit(): void {
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            this.theiaEnabled = profileInfo.activeProfiles?.includes(PROFILE_THEIA);
        });
    }

    faQuestionCircle = faQuestionCircle;
}
