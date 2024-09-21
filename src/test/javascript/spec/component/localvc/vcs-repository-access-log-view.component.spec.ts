import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { ActivatedRoute } from '@angular/router';
import { MockProgrammingExerciseParticipationService } from '../../helpers/mocks/service/mock-programming-exercise-participation.service';
import dayjs from 'dayjs/esm';
import { of } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { MockProgrammingExerciseService } from '../../helpers/mocks/service/mock-programming-exercise.service';
import { VcsRepositoryAccessLogViewComponent } from 'app/localvc/vcs-repository-access-log-view/vcs-repository-access-log-view.component';
import { VcsAccessLogDTO } from 'app/entities/vcs-access-log-entry.model';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { AlertService } from 'app/core/util/alert.service';
import { MockAlertService } from '../../helpers/mocks/service/mock-alert.service';
import { MockProfileService } from '../../helpers/mocks/service/mock-profile.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';

describe('VcsRepositoryAccessLogViewComponent', () => {
    let fixture: ComponentFixture<VcsRepositoryAccessLogViewComponent>;
    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;
    const userId = 4;
    let participationVcsAccessLogSpy: jest.SpyInstance;
    let repositoryVcsAccessLogSpy: jest.SpyInstance;

    const mockVcsAccessLog: VcsAccessLogDTO[] = [
        {
            id: 1,
            userId: userId,
            name: 'authorName',
            email: 'authorEmail',
            commitHash: 'abcde',
            authenticationMechanism: 'SSH',
            repositoryActionType: 'WRITE',
            timestamp: dayjs('2021-01-02'),
        },
        {
            id: 2,
            userId: userId,
            name: 'authorName',
            email: 'authorEmail',
            commitHash: 'fffee',
            authenticationMechanism: 'SSH',
            repositoryActionType: 'READ',
            timestamp: dayjs('2021-01-03'),
        },
    ];

    const route = { params: of({ participationId: '5' }) } as any as ActivatedRoute;

    function setupTestBed() {
        fixture = TestBed.createComponent(VcsRepositoryAccessLogViewComponent);
        programmingExerciseParticipationService = fixture.debugElement.injector.get(ProgrammingExerciseParticipationService);
        repositoryVcsAccessLogSpy = jest.spyOn(programmingExerciseParticipationService, 'getVcsAccessLogForRepository').mockReturnValue(of(mockVcsAccessLog));
        participationVcsAccessLogSpy = jest.spyOn(programmingExerciseParticipationService, 'getVcsAccessLogForParticipation').mockReturnValue(of(mockVcsAccessLog));
    }

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [VcsRepositoryAccessLogViewComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: ProgrammingExerciseParticipationService, useClass: MockProgrammingExerciseParticipationService },
                { provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useClass: MockAlertService },
                { provide: ProfileService, useClass: MockProfileService },
            ],
        }).compileComponents();
    });

    it('should load participation vcs access log', () => {
        setupTestBed();
        fixture.detectChanges();

        expect(participationVcsAccessLogSpy).toHaveBeenCalledOnce();
    });

    it('should load template repository vcs access log', () => {
        route.params = of({ exerciseId: '10', repositoryType: 'TEMPLATE' });
        TestBed.overrideProvider(ActivatedRoute, { useValue: route });

        setupTestBed();
        fixture.detectChanges();

        expect(repositoryVcsAccessLogSpy).toHaveBeenCalledOnce();
    });
});
