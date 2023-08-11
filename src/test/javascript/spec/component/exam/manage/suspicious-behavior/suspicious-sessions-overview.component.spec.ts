import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { SuspiciousSessionsOverviewComponent } from 'app/exam/manage/suspicious-behavior/suspicious-sessions-overview/suspicious-sessions-overview.component';
import { SuspiciousExamSessions, SuspiciousSessionReason } from 'app/entities/exam-session.model';
import { ArtemisTestModule } from '../../../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { SuspiciousSessionsComponent } from 'app/exam/manage/suspicious-behavior/suspicious-sessions/suspicious-sessions.component';

describe('SuspiciousSessionsComponent', () => {
    const suspiciousSessions = {
        examSessions: [
            {
                id: 1,
                ipAddress: '192.168.0.0',
                suspiciousReasons: [SuspiciousSessionReason.SAME_IP_ADDRESS, SuspiciousSessionReason],
            },
            { id: 2, suspiciousReasons: [SuspiciousSessionReason, SuspiciousSessionReason.SAME_IP_ADDRESS], ipAddress: '192.168.0.0' },
        ],
    } as SuspiciousExamSessions;
    let component: SuspiciousSessionsOverviewComponent;
    let fixture: ComponentFixture<SuspiciousSessionsOverviewComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [SuspiciousSessionsOverviewComponent, MockPipe(ArtemisTranslatePipe), MockComponent(SuspiciousSessionsComponent)],
        });
        history.pushState({ suspiciousSessions: [suspiciousSessions] }, '');

        fixture = TestBed.createComponent(SuspiciousSessionsOverviewComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should retrieve suspicious sessions onInit', fakeAsync(() => {
        component.ngOnInit();
        expect(component.suspiciousSessions).toEqual([suspiciousSessions]);
    }));
});
