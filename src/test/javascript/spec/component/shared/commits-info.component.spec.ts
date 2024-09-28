import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CommitsInfoComponent } from 'app/exercises/programming/shared/commits-info/commits-info.component';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import dayjs from 'dayjs/esm';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { of } from 'rxjs';
import { CommitInfo } from 'app/entities/programming/programming-submission.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';

describe('CommitsInfoComponent', () => {
    let component: CommitsInfoComponent;
    let fixture: ComponentFixture<CommitsInfoComponent>;
    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;
    let programmingExerciseParticipationServiceSpy: jest.SpyInstance;
    let profileService: ProfileService;
    let profileServiceSpy: jest.SpyInstance;

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
            imports: [ArtemisTestModule],
            declarations: [CommitsInfoComponent, MockPipe(ArtemisTranslatePipe)],
        });
        fixture = TestBed.createComponent(CommitsInfoComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        programmingExerciseParticipationService = TestBed.inject(ProgrammingExerciseParticipationService);
        programmingExerciseParticipationServiceSpy = jest
            .spyOn(programmingExerciseParticipationService, 'retrieveCommitsInfoForParticipation')
            .mockReturnValue(of([commitInfo1, commitInfo2, commitInfo3, commitInfo4, commitInfo5, commitInfo6] as CommitInfo[]));
        profileService = TestBed.inject(ProfileService);
        profileServiceSpy = jest
            .spyOn(profileService, 'getProfileInfo')
            .mockReturnValue(of({ commitHashURLTemplate: 'https://gitlab.ase.in.tum.de/projects/{projectKey}/repos/{repoSlug}/commits/{commitHash}' } as unknown as ProfileInfo));
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should call participation service to retrieve commits onInit if no commits are passed as input, group commits by push, and sort the grouped commits descending by timestamp', () => {
        component.participationId = 1;
        component.ngOnInit();
        expect(programmingExerciseParticipationServiceSpy).toHaveBeenCalledExactlyOnceWith(1);
        expect((component as any).groupedCommits).toEqual([
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
        component.commits = [{ hash: '123', author: 'author', timestamp: dayjs('2021-01-01'), message: 'commit message' }];
        component.ngOnInit();
        expect(programmingExerciseParticipationServiceSpy).not.toHaveBeenCalled();
    });

    it('should correctly return commit url', () => {
        component.participationId = 1;
        component.commits = [commitInfo1];
        component.submissions = [
            {
                commitHash: '123',
                participation: {
                    id: 1,
                    type: ParticipationType.PROGRAMMING,
                    participantIdentifier: '1',
                    repositoryUri: 'repo.abc',
                } as unknown as ProgrammingExerciseStudentParticipation,
            },
        ];
        component.exerciseProjectKey = 'key';

        component.ngOnInit();

        expect(component.commits[0].commitUrl).toBe('https://gitlab.ase.in.tum.de/projects/key/repos/key-1/commits/123');
    });

    it('should set localVC to true if active profiles contains localVc', () => {
        profileServiceSpy.mockReturnValue(of({ activeProfiles: ['localvc'] } as unknown as ProfileInfo));
        component.ngOnInit();
        expect(component.localVC).toBeTrue();
    });

    it('should set localVC to false if active profiles does not contain localVc', () => {
        profileServiceSpy.mockReturnValue(of({ activeProfiles: ['gitlab'] } as unknown as ProfileInfo));
        component.ngOnInit();
        expect(component.localVC).toBeFalse();
    });
});
