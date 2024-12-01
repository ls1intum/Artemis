import { Component, Input, OnInit } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { ProgrammingExerciseParticipationType } from 'app/entities/programming/programming-exercise-participation.model';
import { Exercise } from 'app/entities/exercise.model';
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
    participationType = ProgrammingExerciseParticipationType;

    programmingExercise: ProgrammingExercise;

    localVCEnabled = false;
    onlineIdeEnabled = false;

    @Input()
    displayShortName = false;
    @Input()
    displayRepositoryUri = false;
    @Input()
    displayTemplateUrls = false;
    @Input()
    displayEditorModus = false;

    @Input()
    set exercise(exercise: Exercise) {
        this.programmingExercise = exercise as ProgrammingExercise;
    }

    faDownload = faDownload;

    constructor(
        private profileService: ProfileService,
        private programmingExerciseService: ProgrammingExerciseService,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            this.localVCEnabled = profileInfo.activeProfiles.includes(PROFILE_LOCALVC);
            this.onlineIdeEnabled = profileInfo.activeProfiles.includes(PROFILE_THEIA);
            if (this.programmingExercise.projectKey) {
                if (this.programmingExercise.solutionParticipation?.buildPlanId) {
                    this.programmingExercise.solutionParticipation!.buildPlanUrl = createBuildPlanUrl(
                        profileInfo.buildPlanURLTemplate,
                        this.programmingExercise.projectKey,
                        this.programmingExercise.solutionParticipation.buildPlanId,
                    );
                }
                if (this.programmingExercise.templateParticipation?.buildPlanId) {
                    this.programmingExercise.templateParticipation!.buildPlanUrl = createBuildPlanUrl(
                        profileInfo.buildPlanURLTemplate,
                        this.programmingExercise.projectKey,
                        this.programmingExercise.templateParticipation.buildPlanId,
                    );
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
    downloadRepository(repositoryType: ProgrammingExerciseInstructorRepositoryType) {
        if (this.programmingExercise.id) {
            // Repository type cannot be 'AUXILIARY' as auxiliary repositories are currently not supported for the local VCS.
            this.programmingExerciseService.exportInstructorRepository(this.programmingExercise.id, repositoryType, undefined).subscribe((response: HttpResponse<Blob>) => {
                downloadZipFileFromResponse(response);
                this.alertService.success('artemisApp.programmingExercise.export.successMessageRepos');
            });
        }
    }
}
