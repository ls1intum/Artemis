import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CommitsInfoComponent } from 'app/programming/shared/commits-info/commits-info.component';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import dayjs from 'dayjs/esm';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { CommitInfo } from 'app/programming/shared/entities/programming-submission.model';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

describe('CommitsInfoComponent', () => {
    setupTestBed({ zoneless: true });

    let component: CommitsInfoComponent;
    let fixture: ComponentFixture<CommitsInfoComponent>;
    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;
    let programmingExerciseParticipationServiceSpy: ReturnType<typeof vi.spyOn>;

    const commitInfo1 = {
        hash: '123',
        author: 'author',
        timestamp: dayjs('2021-01-01'),
        message: 'commit message',
    } as CommitInfo;
    const commitInfo2 = {
        hash: '456',
        author: 'author',
        timestamp: dayjs('2021-01-02'),
        message: 'other message',
        result: { successful: true },
    } as CommitInfo;
    const commitInfo3 = {
        hash: '789',
        author: 'author',
        timestamp: dayjs('2021-01-03'),
        message: 'another message',
    } as CommitInfo;
    const commitInfo4 = {
        hash: '012',
        author: 'author',
        timestamp: dayjs('2021-01-04'),
        message: 'yet another message',
        result: { successful: false },
    } as CommitInfo;
    const commitInfo5 = {
        hash: '890',
        author: 'author',
        timestamp: dayjs('2021-01-05'),
        message: 'another message',
    } as CommitInfo;
    const commitInfo6 = {
        hash: '014',
        author: 'author',
        timestamp: dayjs('2021-01-06'),
        message: 'yet another message',
    } as CommitInfo;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CommitsInfoComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(ProgrammingExerciseParticipationService),
                { provide: ProfileService, useClass: MockProfileService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
            ],
        });
        fixture = TestBed.createComponent(CommitsInfoComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        programmingExerciseParticipationService = TestBed.inject(ProgrammingExerciseParticipationService);
        programmingExerciseParticipationServiceSpy = vi
            .spyOn(programmingExerciseParticipationService, 'retrieveCommitHistoryForParticipation')
            .mockReturnValue(of([commitInfo1, commitInfo2, commitInfo3, commitInfo4, commitInfo5, commitInfo6] as CommitInfo[]));
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should call participation service to retrieve commits onInit if no commits are passed as input, group commits by push, and sort the grouped commits descending by timestamp', () => {
        fixture.componentRef.setInput('participationId', 1);
        component.ngOnInit();
        expect(programmingExerciseParticipationServiceSpy).toHaveBeenCalledExactlyOnceWith(1);
        expect((component as any).groupedCommits()).toEqual([
            {
                key: 'no-result',
                commits: [commitInfo6, commitInfo5],
                date: '2021-01-06',
            },
            {
                key: '2021-01-04-author',
                commits: [commitInfo4, commitInfo3],
                date: '2021-01-04',
            },
            {
                key: '2021-01-02-author',
                commits: [commitInfo2, commitInfo1],
                date: '2021-01-02',
            },
        ]);
    });

    it('should do nothing onInit if commits are passed as input', () => {
        fixture.componentRef.setInput('commits', [{ hash: '123', author: 'author', timestamp: dayjs('2021-01-01'), message: 'commit message' }]);
        component.ngOnInit();
        expect(programmingExerciseParticipationServiceSpy).not.toHaveBeenCalled();
    });

    it('should populate groupedCommits from the commits input via internalCommits before any service fetch', () => {
        // Exercise the full input -> internalCommits -> groupedCommits flow. This is the chain the
        // template binds against (@for over groupedCommits()), so verifying these three signals
        // together verifies that an input change reaches the rendered grouping.
        fixture.componentRef.setInput('commits', [commitInfo1, commitInfo2, commitInfo3, commitInfo4, commitInfo5, commitInfo6]);
        component.ngOnInit();

        // Service must not be called because the input already provided commits.
        expect(programmingExerciseParticipationServiceSpy).not.toHaveBeenCalled();
        // Three groups expected: 'no-result' for the latest tail (commits 5 and 6), '2021-01-04-author'
        // (commits 3 and 4, terminated by commit4.result), and '2021-01-02-author' (commits 1 and 2,
        // terminated by commit2.result). Output is reversed so the newest group comes first.
        expect((component as any).groupedCommits()).toEqual([
            { key: 'no-result', commits: [commitInfo6, commitInfo5], date: '2021-01-06' },
            { key: '2021-01-04-author', commits: [commitInfo4, commitInfo3], date: '2021-01-04' },
            { key: '2021-01-02-author', commits: [commitInfo2, commitInfo1], date: '2021-01-02' },
        ]);
    });
});
