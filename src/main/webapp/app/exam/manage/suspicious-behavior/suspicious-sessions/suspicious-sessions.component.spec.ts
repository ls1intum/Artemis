import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SuspiciousSessionsComponent } from 'app/exam/manage/suspicious-behavior/suspicious-sessions/suspicious-sessions.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { SuspiciousExamSessions, SuspiciousSessionReason } from 'app/exam/shared/entities/exam-session.model';

describe('SuspiciousSessionsComponent', () => {
    setupTestBed({ zoneless: true });

    let component: SuspiciousSessionsComponent;
    let fixture: ComponentFixture<SuspiciousSessionsComponent>;
    const studentExam = { id: 1, exam: { id: 1, course: { id: 1 } } } as StudentExam;
    const suspiciousSessions1 = {
        examSessions: [
            {
                suspiciousReasons: [SuspiciousSessionReason.SAME_STUDENT_EXAM_DIFFERENT_IP_ADDRESSES, SuspiciousSessionReason.SAME_STUDENT_EXAM_DIFFERENT_BROWSER_FINGERPRINTS],
            },
        ],
    } as SuspiciousExamSessions;

    const suspiciousSessions2 = {
        examSessions: [
            {
                suspiciousReasons: [],
            },
        ],
    } as SuspiciousExamSessions;

    const suspiciousSessions3 = {
        examSessions: [
            {
                suspiciousReasons: [SuspiciousSessionReason.IP_ADDRESS_OUTSIDE_OF_RANGE],
            },
        ],
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [SuspiciousSessionsComponent, MockPipe(ArtemisTranslatePipe)],
        });
        fixture = TestBed.createComponent(SuspiciousSessionsComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('suspiciousSessions', suspiciousSessions1);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should contain correct link to student exam in table cell', () => {
        expect(component.getStudentExamLink(studentExam)).toBe('/course-management/1/exams/1/student-exams/1');
    });

    it('should correctly determine suspicious reasons', () => {
        fixture.componentRef.setInput('suspiciousSessions', suspiciousSessions1);
        component.ngOnInit();
        expect(component.suspiciousFingerprint()).toBe(true);
        expect(component.suspiciousIpAddress()).toBe(true);

        fixture.componentRef.setInput('suspiciousSessions', suspiciousSessions2);
        component.ngOnInit();
        expect(component.suspiciousFingerprint()).toBe(false);
        expect(component.suspiciousIpAddress()).toBe(false);
        fixture.componentRef.setInput('suspiciousSessions', suspiciousSessions3);
        component.ngOnInit();
        expect(component.suspiciousFingerprint()).toBe(false);
        expect(component.suspiciousIpAddress()).toBe(true);
    });
});
