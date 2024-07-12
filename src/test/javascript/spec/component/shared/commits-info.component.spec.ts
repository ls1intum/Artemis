import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CommitsInfoComponent } from 'app/exercises/programming/shared/commits-info/commits-info.component';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import dayjs from 'dayjs/esm';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { of } from 'rxjs';
import { CommitInfo } from 'app/entities/programming-submission.model';
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
        timestamp: dayjs('2021-01-02'),
        message: 'commit message',
    } as CommitInfo;
    const commitInfo2 = {
        hash: '456',
        author: 'author2',
        timestamp: dayjs('2021-01-01'),
        message: 'other message',
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
            .mockReturnValue(of([commitInfo2, commitInfo1] as CommitInfo[]));
        profileService = TestBed.inject(ProfileService);
        profileServiceSpy = jest
            .spyOn(profileService, 'getProfileInfo')
            .mockReturnValue(of({ commitHashURLTemplate: 'https://gitlab.ase.in.tum.de/projects/{projectKey}/repos/{repoSlug}/commits/{commitHash}' } as unknown as ProfileInfo));
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should call participation service to retrieve commits onInit if no commits are passed as input and sort the commits descending by timestamp', () => {
        component.participationId = 1;
        component.ngOnInit();
        expect(programmingExerciseParticipationServiceSpy).toHaveBeenCalledExactlyOnceWith(1);
        expect(component.commits).toEqual([commitInfo1, commitInfo2]);
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
