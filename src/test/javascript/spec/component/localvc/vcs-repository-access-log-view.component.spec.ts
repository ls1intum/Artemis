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

describe('VcsRepositoryAccessLogViewComponent', () => {
    let component: VcsRepositoryAccessLogViewComponent;
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

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [VcsRepositoryAccessLogViewComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: ProgrammingExerciseParticipationService, useClass: MockProgrammingExerciseParticipationService },
                { provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useClass: MockAlertService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(VcsRepositoryAccessLogViewComponent);
                component = fixture.componentInstance;
                programmingExerciseParticipationService = fixture.debugElement.injector.get(ProgrammingExerciseParticipationService);

                repositoryVcsAccessLogSpy = jest.spyOn(programmingExerciseParticipationService, 'getVcsAccessLogForRepository').mockReturnValue(of(mockVcsAccessLog));
                participationVcsAccessLogSpy = jest.spyOn(programmingExerciseParticipationService, 'getVcsAccessLogForParticipation').mockReturnValue(of(mockVcsAccessLog));
            });
    });

    it('should load participation vcs access log', () => {
        // Trigger ngOnInit
        component.ngOnInit();
        expect(component).toBeTruthy();
        expect(participationVcsAccessLogSpy).toHaveBeenCalled();
        expect(component.vcsAccessLogEntries).toHaveLength(2);
        expect(component.vcsAccessLogEntries[0].userId).toBe(userId);
    });

    it('should load template repository vcs access log', () => {
        // Trigger ngOnInit
        route.params = of({ exerciseId: '10', repositoryType: 'TEMPLATE' });
        component.ngOnInit();
        expect(repositoryVcsAccessLogSpy).toHaveBeenCalled();
        expect(component.vcsAccessLogEntries).toHaveLength(2);
        expect(component.vcsAccessLogEntries[0].userId).toBe(userId);
    });
});
