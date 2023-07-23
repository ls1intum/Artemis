import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SuspiciousSessionsOverviewComponent } from 'app/exam/manage/suspicious-behavior/suspicious-sessions-overview/suspicious-sessions-overview.component';

describe('SuspiciousSessionsComponent', () => {
    let component: SuspiciousSessionsOverviewComponent;
    let fixture: ComponentFixture<SuspiciousSessionsOverviewComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [SuspiciousSessionsOverviewComponent],
        });
        fixture = TestBed.createComponent(SuspiciousSessionsOverviewComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
