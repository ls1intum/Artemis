import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { ActivatedRoute } from '@angular/router';
import { MockProgrammingExerciseParticipationService } from 'test/helpers/mocks/service/mock-programming-exercise-participation.service';
import dayjs from 'dayjs/esm';
import { of } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { MockProgrammingExerciseService } from 'test/helpers/mocks/service/mock-programming-exercise.service';
import { VcsRepositoryAccessLogViewComponent } from 'app/programming/manage/vcs-repository-access-log-view/vcs-repository-access-log-view.component';
import { VcsAccessLogDTO } from 'app/programming/shared/entities/vcs-access-log-entry.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

describe('VcsRepositoryAccessLogViewComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<VcsRepositoryAccessLogViewComponent>;
    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;
    const userId = 4;
    let participationVcsAccessLogSpy: ReturnType<typeof vi.spyOn>;
    let repositoryVcsAccessLogSpy: ReturnType<typeof vi.spyOn>;

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

    const route = { params: of({ repositoryId: '5' }) } as any as ActivatedRoute;

    function createComponent() {
        fixture = TestBed.createComponent(VcsRepositoryAccessLogViewComponent);
        programmingExerciseParticipationService = TestBed.inject(ProgrammingExerciseParticipationService);
        repositoryVcsAccessLogSpy = vi.spyOn(programmingExerciseParticipationService, 'getVcsAccessLogForRepository').mockReturnValue(of(mockVcsAccessLog));
        participationVcsAccessLogSpy = vi.spyOn(programmingExerciseParticipationService, 'getVcsAccessLogForParticipation').mockReturnValue(of(mockVcsAccessLog));
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

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should load participation vcs access log', () => {
        createComponent();
        fixture.detectChanges();

        expect(participationVcsAccessLogSpy).toHaveBeenCalledOnce();
    });

    it('should load template repository vcs access log', () => {
        route.params = of({ exerciseId: '10', repositoryType: 'TEMPLATE' });
        TestBed.overrideProvider(ActivatedRoute, { useValue: route });

        createComponent();
        fixture.detectChanges();

        expect(repositoryVcsAccessLogSpy).toHaveBeenCalledOnce();
    });
});
