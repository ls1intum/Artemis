import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SuspiciousSessionsOverviewComponent } from 'app/exam/manage/suspicious-behavior/suspicious-sessions-overview/suspicious-sessions-overview.component';
import { SuspiciousExamSessions, SuspiciousSessionReason } from 'app/entities/exam-session.model';
import { SuspiciousSessionsService } from 'app/exam/manage/suspicious-behavior/suspicious-sessions.service';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { ActivatedRoute, convertToParamMap } from '@angular/router';

describe('SuspiciousSessionsComponent', () => {
    const route = { snapshot: { paramMap: convertToParamMap({ courseId: 1, examId: 2 }) } } as unknown as ActivatedRoute;
    const suspiciousSessions = {
        examSessions: [
            {
                id: 1,
                userAgent: 'user-agent',
                ipAddress: '192.168.0.0',
                suspiciousReasons: [SuspiciousSessionReason.SAME_IP_ADDRESS, SuspiciousSessionReason.SAME_USER_AGENT],
            },
            { id: 2, suspiciousReasons: [SuspiciousSessionReason.SAME_USER_AGENT, SuspiciousSessionReason.SAME_IP_ADDRESS], userAgent: 'user-agent', ipAddress: '192.168.0.0' },
        ],
    } as SuspiciousExamSessions;
    let component: SuspiciousSessionsOverviewComponent;
    let fixture: ComponentFixture<SuspiciousSessionsOverviewComponent>;
    let suspiciousSessionService: SuspiciousSessionsService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [SuspiciousSessionsOverviewComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: ActivatedRoute, useValue: route }],
        });
        fixture = TestBed.createComponent(SuspiciousSessionsOverviewComponent);
        component = fixture.componentInstance;
        suspiciousSessionService = TestBed.inject(SuspiciousSessionsService);
        fixture.detectChanges();
    });

    it('should retrieve suspicious sessions onInit', () => {
        const suspiciousSessionsServiceSpy = jest.spyOn(suspiciousSessionService, 'getSuspiciousSessions').mockReturnValue(of([suspiciousSessions]));
        component.ngOnInit();
        expect(suspiciousSessionsServiceSpy).toHaveBeenCalledOnce();
        expect(suspiciousSessionsServiceSpy).toHaveBeenCalledWith(1, 2);
        expect(component.suspiciousSessions).toEqual([suspiciousSessions]);
    });
});
