import { Component, OnInit, inject, input } from '@angular/core';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseParticipationType } from 'app/programming/shared/entities/programming-exercise-participation.model';
import { createBuildPlanUrl } from 'app/programming/shared/utils/programming-exercise.utils';
import { faDownload } from '@fortawesome/free-solid-svg-icons';
import { MODULE_FEATURE_THEIA, PROFILE_LOCALCI } from 'app/app.constants';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';

@Component({
    selector: 'jhi-programming-exercise-group-cell',
    templateUrl: './programming-exercise-group-cell.component.html',
    styles: [':host{display: contents}'],
    imports: [TranslateDirective],
})
export class ProgrammingExerciseGroupCellComponent implements OnInit {
    private profileService = inject(ProfileService);

    participationType = ProgrammingExerciseParticipationType;

    protected readonly RepositoryType = RepositoryType;

    localCIEnabled = true;
    onlineIdeEnabled = false;

    displayShortName = input(false);
    displayTemplateUrls = input(false);
    displayEditorMode = input(false);
    exercise = input.required<ProgrammingExercise>();
    numberOfResultsOfTemplateParticipation = 0;
    numberOfResultsOfSolutionParticipation = 0;

    faDownload = faDownload;

    ngOnInit(): void {
        this.localCIEnabled = this.profileService.isProfileActive(PROFILE_LOCALCI);
        this.onlineIdeEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_THEIA);

        const projectKey = this.exercise()?.projectKey;
        if (projectKey && !this.localCIEnabled) {
            // buildPlanURLTemplate is only available on Artemis instances without LocalCI (e.g. using Jenkins)
            const buildPlanURLTemplate = this.profileService.getProfileInfo().buildPlanURLTemplate;
            const solutionParticipation = this.exercise()?.solutionParticipation;
            this.numberOfResultsOfSolutionParticipation = getAllResultsOfAllSubmissions(solutionParticipation?.submissions).length;
            if (solutionParticipation?.buildPlanId && buildPlanURLTemplate) {
                solutionParticipation.buildPlanUrl = createBuildPlanUrl(buildPlanURLTemplate, projectKey, solutionParticipation.buildPlanId);
            }

            const templateParticipation = this.exercise()?.templateParticipation;
            this.numberOfResultsOfTemplateParticipation = getAllResultsOfAllSubmissions(templateParticipation?.submissions).length;
            if (templateParticipation?.buildPlanId && buildPlanURLTemplate) {
                templateParticipation.buildPlanUrl = createBuildPlanUrl(buildPlanURLTemplate, projectKey, templateParticipation.buildPlanId);
            }
        }
    }
}
