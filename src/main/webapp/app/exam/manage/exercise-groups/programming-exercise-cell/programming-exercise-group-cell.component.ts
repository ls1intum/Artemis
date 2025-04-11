import { Component, OnInit, inject, input } from '@angular/core';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseParticipationType } from 'app/programming/shared/entities/programming-exercise-participation.model';
import { createBuildPlanUrl } from 'app/programming/shared/utils/programming-exercise.utils';
import { faDownload } from '@fortawesome/free-solid-svg-icons';
import { PROFILE_LOCALCI, PROFILE_THEIA } from 'app/app.constants';
import { RouterLink } from '@angular/router';
import { ProgrammingExerciseInstructorStatusComponent } from 'app/programming/manage/status/programming-exercise-instructor-status.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

@Component({
    selector: 'jhi-programming-exercise-group-cell',
    templateUrl: './programming-exercise-group-cell.component.html',
    styles: [':host{display: contents}'],
    imports: [RouterLink, ProgrammingExerciseInstructorStatusComponent, TranslateDirective],
})
export class ProgrammingExerciseGroupCellComponent implements OnInit {
    private profileService = inject(ProfileService);

    participationType = ProgrammingExerciseParticipationType;

    protected readonly RepositoryType = RepositoryType;

    localCIEnabled = true;
    onlineIdeEnabled = false;

    displayShortName = input(false);
    displayRepositoryUri = input(false);
    displayTemplateUrls = input(false);
    displayEditorMode = input(false);
    exercise = input.required<ProgrammingExercise>();

    faDownload = faDownload;

    ngOnInit(): void {
        this.localCIEnabled = this.profileService.isProfileActive(PROFILE_LOCALCI);
        this.onlineIdeEnabled = this.profileService.isProfileActive(PROFILE_THEIA);

        const projectKey = this.exercise()?.projectKey;
        if (projectKey && !this.localCIEnabled) {
            // buildPlanURLTemplate is only available on Artemis instances without LocalCI (e.g. using Jenkins)
            const buildPlanURLTemplate = this.profileService.profileInfo.buildPlanURLTemplate;

            const solutionParticipation = this.exercise()?.solutionParticipation;
            if (solutionParticipation?.buildPlanId && buildPlanURLTemplate) {
                solutionParticipation.buildPlanUrl = createBuildPlanUrl(buildPlanURLTemplate, projectKey, solutionParticipation.buildPlanId);
            }

            const templateParticipation = this.exercise()?.templateParticipation;
            if (templateParticipation?.buildPlanId && buildPlanURLTemplate) {
                templateParticipation.buildPlanUrl = createBuildPlanUrl(buildPlanURLTemplate, projectKey, templateParticipation.buildPlanId);
            }
        }
    }
}
