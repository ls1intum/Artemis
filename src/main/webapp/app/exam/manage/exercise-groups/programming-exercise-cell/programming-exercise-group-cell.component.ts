import { Component, OnInit, inject, input } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseParticipationType } from 'app/programming/shared/entities/programming-exercise-participation.model';
import { createBuildPlanUrl } from 'app/programming/shared/utils/programming-exercise.utils';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { downloadZipFileFromResponse } from 'app/shared/util/download.util';
import { AlertService } from 'app/shared/service/alert.service';
import { faDownload } from '@fortawesome/free-solid-svg-icons';
import { PROFILE_THEIA } from 'app/app.constants';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ProgrammingExerciseInstructorStatusComponent } from 'app/programming/manage/status/programming-exercise-instructor-status.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

@Component({
    selector: 'jhi-programming-exercise-group-cell',
    templateUrl: './programming-exercise-group-cell.component.html',
    styles: [':host{display: contents}'],
    imports: [FaIconComponent, ProgrammingExerciseInstructorStatusComponent, TranslateDirective],
})
export class ProgrammingExerciseGroupCellComponent implements OnInit {
    private profileService = inject(ProfileService);
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private alertService = inject(AlertService);

    participationType = ProgrammingExerciseParticipationType;

    protected readonly RepositoryType = RepositoryType;

    onlineIdeEnabled = false;

    displayShortName = input(false);
    displayRepositoryUri = input(false);
    displayTemplateUrls = input(false);
    displayEditorModus = input(false);
    exercise = input.required<ProgrammingExercise>();

    faDownload = faDownload;

    ngOnInit(): void {
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            this.onlineIdeEnabled = profileInfo.activeProfiles.includes(PROFILE_THEIA);

            const projectKey = this.exercise()?.projectKey;
            if (projectKey) {
                const solutionParticipation = this.exercise()?.solutionParticipation;
                if (solutionParticipation?.buildPlanId) {
                    solutionParticipation.buildPlanUrl = createBuildPlanUrl(profileInfo.buildPlanURLTemplate, projectKey, solutionParticipation.buildPlanId);
                }

                const templateParticipation = this.exercise()?.templateParticipation;
                if (templateParticipation?.buildPlanId) {
                    templateParticipation.buildPlanUrl = createBuildPlanUrl(profileInfo.buildPlanURLTemplate, projectKey, templateParticipation.buildPlanId);
                }
            }
        });
    }

    /**
     * Downloads the instructor repository.
     *
     * @param repositoryType
     */
    downloadRepository(repositoryType: RepositoryType): void {
        const programmingExerciseId = this.exercise()?.id;
        if (programmingExerciseId) {
            this.programmingExerciseService.exportInstructorRepository(programmingExerciseId, repositoryType, undefined).subscribe((response: HttpResponse<Blob>) => {
                downloadZipFileFromResponse(response);
                this.alertService.success('artemisApp.programmingExercise.export.successMessageRepos');
            });
        }
    }
}
