import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { CommitDetailsViewComponent } from 'app/programming/manage/commit-details-view/commit-details-view.component';
import { ProgrammingExerciseExamDiffComponent } from 'app/exam/manage/student-exams/student-exam-timeline/programming-exam-diff/programming-exercise-exam-diff.component';
import { ProgrammingExerciseExampleSolutionRepoDownloadComponent } from 'app/programming/shared/actions/example-solution-repo-download/programming-exercise-example-solution-repo-download.component';
import { ProgrammingExerciseStudentRepoDownloadComponent } from 'app/programming/shared/actions/student-repo-download/programming-exercise-student-repo-download.component';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { CommitInfo } from 'app/programming/shared/entities/programming-submission.model';

vi.mock('app/programming/shared/utils/diff.utils', async () => ({
    ...(await vi.importActual<typeof import('app/programming/shared/utils/diff.utils')>('app/programming/shared/utils/diff.utils')),
    processRepositoryDiff: vi.fn().mockResolvedValue({ diffInformations: [], totalLineChange: { addedLineCount: 0, removedLineCount: 0 } }),
}));

describe('Programming repository callers select their known audience before requesting content', () => {
    let http: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                SessionStorageService,
                LocalStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: ActivatedRoute, useValue: { params: of({}) } },
            ],
        });
        TestBed.overrideComponent(CommitDetailsViewComponent, { set: { template: '', imports: [] } });
        TestBed.overrideComponent(ProgrammingExerciseExamDiffComponent, { set: { template: '', imports: [] } });
        TestBed.overrideComponent(ProgrammingExerciseExampleSolutionRepoDownloadComponent, { set: { template: '', imports: [] } });
        TestBed.overrideComponent(ProgrammingExerciseStudentRepoDownloadComponent, { set: { template: '', imports: [] } });
        http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => http.verify());

    it.each([RepositoryType.USER, RepositoryType.SOLUTION, RepositoryType.AUXILIARY])('commit details dispatches both sides using known %s context', (type) => {
        const fixture = TestBed.createComponent(CommitDetailsViewComponent);
        const component = fixture.componentInstance;
        component.exerciseId = 1;
        component.repositoryId.set(2);
        component.repositoryType = type;
        component.previousCommit.set({ hash: 'left' } as CommitInfo);
        component.currentCommit.set({ hash: 'right' } as CommitInfo);
        component['fetchParticipationRepoFiles']();
        const suffix = type === RepositoryType.USER ? '' : '/for-editor';
        const selector =
            type === RepositoryType.USER ? 'participationId=2' : type === RepositoryType.AUXILIARY ? 'repositoryType=AUXILIARY&repositoryId=2' : 'repositoryType=SOLUTION';
        for (const commit of ['left', 'right']) {
            const req = http.expectOne({ method: 'GET', url: `api/programming/programming-exercises/1/files-content-commit-details${suffix}?commitId=${commit}&${selector}` });
            req.flush({ 'src/Main.java': commit });
        }
    });

    it('exam diff uses the actual participation of each submitted commit', () => {
        const fixture = TestBed.createComponent(ProgrammingExerciseExamDiffComponent);
        fixture.componentRef.setInput('exercise', { id: 1 });
        fixture.componentRef.setInput('previousSubmission', { participation: { id: 2 }, commitHash: 'left' });
        fixture.componentRef.setInput('currentSubmission', { participation: { id: 3 }, commitHash: 'right' });
        fixture.componentInstance.fetchRepositoryFilesAtLeftCommit().subscribe((files) => expect(files?.get('src/Main.java')).toBe('left'));
        fixture.componentInstance.fetchRepositoryFilesAtRightCommit().subscribe((files) => expect(files?.get('src/Main.java')).toBe('right'));
        http.expectOne({ method: 'GET', url: 'api/programming/programming-exercises/1/files-content-commit-details?commitId=left&participationId=2' }).flush({
            'src/Main.java': 'left',
        });
        http.expectOne({ method: 'GET', url: 'api/programming/programming-exercises/1/files-content-commit-details?commitId=right&participationId=3' }).flush({
            'src/Main.java': 'right',
        });
    });

    it.each([false, true])('the inherited example-solution caller selects staff=%s and keeps includeTests', (forStaff) => {
        const fixture = TestBed.createComponent(ProgrammingExerciseExampleSolutionRepoDownloadComponent);
        fixture.componentRef.setInput('exerciseId', 1);
        fixture.componentRef.setInput('includeTests', true);
        fixture.componentRef.setInput('forStaff', forStaff);
        fixture.componentInstance.exportRepository();
        const suffix = forStaff ? '/for-staff' : '';
        http.expectOne({ method: 'GET', url: `api/programming/programming-exercises/1/export-student-requested-repository${suffix}?includeTests=true` }).flush(null);
    });

    it.each(['owner', 'tutor', 'instructor'])('repository download selects the known %s purpose', (audience) => {
        const fixture = TestBed.createComponent(ProgrammingExerciseStudentRepoDownloadComponent);
        fixture.componentRef.setInput('exerciseId', 1);
        fixture.componentRef.setInput('participationId', 2);
        fixture.componentRef.setInput('audience', audience);
        fixture.componentInstance.exportRepository();
        const suffix = audience === 'owner' ? '' : audience === 'tutor' ? '/for-staff' : '/for-instructor';
        http.expectOne({ method: 'GET', url: `api/programming/programming-exercises/1/export-student-repository${suffix}?participationId=2` }).flush(null);
    });
});
