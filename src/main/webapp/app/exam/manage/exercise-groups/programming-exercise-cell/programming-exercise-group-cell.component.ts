import { Component, OnInit, inject, input } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { ProgrammingExerciseParticipationType } from 'app/entities/programming/programming-exercise-participation.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { createBuildPlanUrl } from 'app/exercises/programming/shared/utils/programming-exercise.utils';
import { ProgrammingExerciseInstructorRepositoryType, ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { downloadZipFileFromResponse } from 'app/shared/util/download.util';
import { AlertService } from 'app/core/util/alert.service';
import { faDownload } from '@fortawesome/free-solid-svg-icons';
import { PROFILE_LOCALVC, PROFILE_THEIA } from 'app/app.constants';

@Component({
    selector: 'jhi-programming-exercise-group-cell',
    templateUrl: './programming-exercise-group-cell.component.html',
    styles: [':host{display: contents}'],
})
export class ProgrammingExerciseGroupCellComponent implements OnInit {
    private profileService = inject(ProfileService);
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private alertService = inject(AlertService);

    participationType = ProgrammingExerciseParticipationType;

    localVCEnabled = false;
    onlineIdeEnabled = false;

    displayShortName = input(false);
    displayRepositoryUri = input(false);
    displayTemplateUrls = input(false);
    displayEditorModus = input(false);
    programmingExercise = input.required<ProgrammingExercise>();

    faDownload = faDownload;

    ngOnInit(): void {
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            this.localVCEnabled = profileInfo.activeProfiles.includes(PROFILE_LOCALVC);
            this.onlineIdeEnabled = profileInfo.activeProfiles.includes(PROFILE_THEIA);

            const projectKey = this.programmingExercise()?.projectKey;
            if (projectKey) {
                const solutionParticipation = this.programmingExercise()?.solutionParticipation;
                if (solutionParticipation?.buildPlanId) {
                    solutionParticipation.buildPlanUrl = createBuildPlanUrl(profileInfo.buildPlanURLTemplate, projectKey, solutionParticipation.buildPlanId);
                }

                const templateParticipation = this.programmingExercise()?.templateParticipation;
                if (templateParticipation?.buildPlanId) {
                    templateParticipation.buildPlanUrl = createBuildPlanUrl(profileInfo.buildPlanURLTemplate, projectKey, templateParticipation.buildPlanId);
                }
            }
        });
    }

    /**
     * Downloads the instructor repository. Used when the "localvc" profile is active.
     * For the local VCS, linking to an external site displaying the repository does not work.
     * Instead, the repository is downloaded.
     *
     * @param repositoryType
     */
    downloadRepository(repositoryType: ProgrammingExerciseInstructorRepositoryType): void {
        const programmingExerciseId = this.programmingExercise()?.id;
        if (programmingExerciseId) {
            // Repository type cannot be 'AUXILIARY' as auxiliary repositories are currently not supported for the local VCS.
            this.programmingExerciseService.exportInstructorRepository(programmingExerciseId, repositoryType, undefined).subscribe((response: HttpResponse<Blob>) => {
                downloadZipFileFromResponse(response);
                this.alertService.success('artemisApp.programmingExercise.export.successMessageRepos');
            });
        }
    }
}
