import { Component, computed, input } from '@angular/core';
import type { ProgrammingRepositoryButtonsDetail } from 'app/shared-ui/detail-overview-list/detail.model';
import { NoDataComponent } from 'app/shared-ui/components/no-data/no-data-component';
import { RouterModule } from '@angular/router';
import { CodeButtonComponent, type VcsAccessTokenSourceInfo } from 'app/shared-ui/components/buttons/code-button/code-button.component';
import { ProgrammingExerciseInstructorRepoDownloadComponent } from 'app/programming/shared/actions/instructor-repo-download/programming-exercise-instructor-repo-download.component';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';

type BaseRepositoryType = RepositoryType.TEMPLATE | RepositoryType.SOLUTION | RepositoryType.TESTS;

interface ProgrammingRepositoryButtonData {
    exerciseId: number;
    repositoryUri: string;
    repositoryType: BaseRepositoryType;
    vcsAccessTokenData: VcsAccessTokenSourceInfo;
}

@Component({
    selector: 'jhi-programming-repository-buttons-detail',
    templateUrl: 'programming-repository-buttons-detail.component.html',
    imports: [NoDataComponent, RouterModule, CodeButtonComponent, ProgrammingExerciseInstructorRepoDownloadComponent],
})
export class ProgrammingRepositoryButtonsDetailComponent {
    detail = input.required<ProgrammingRepositoryButtonsDetail>();
    readonly repositoryDataForCodeButton = computed(() => this.getRepositoryDataForCodeButton());

    private getRepositoryDataForCodeButton(): ProgrammingRepositoryButtonData | undefined {
        const { exerciseId, participation, type } = this.detail().data;
        const repositoryUri = participation?.repositoryUri;
        if (!exerciseId || !repositoryUri) {
            return undefined;
        }

        return {
            exerciseId,
            repositoryUri,
            repositoryType: type,
            vcsAccessTokenData: { returnedTokenType: 'repository-token', repositoryType: type },
        };
    }
}
