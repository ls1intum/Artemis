import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { SuspiciousSessionsComponent } from 'app/exam/manage/suspicious-behavior/suspicious-sessions/suspicious-sessions.component';
import { MockRouter } from '../../../../helpers/mocks/mock-router';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { StudentExam } from 'app/entities/student-exam.model';

describe('SuspiciousSessionsComponent', () => {
    let component: SuspiciousSessionsComponent;
    let fixture: ComponentFixture<SuspiciousSessionsComponent>;
    let router: Router;
    const studentExam = { id: 1, exam: { id: 1, course: { id: 1 } } } as StudentExam;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [SuspiciousSessionsComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: Router, useClass: MockRouter }],
        });
        fixture = TestBed.createComponent(SuspiciousSessionsComponent);
        component = fixture.componentInstance;
        router = TestBed.inject(Router);
        component.suspiciousSessions = { examSessions: [] };
        fixture.detectChanges();
    });

    it('should contain correct link to student exam in table cell', () => {
        expect(component.getStudentExamLink(studentExam)).toBe('/course-management/1/exams/1/student-exams/1');
    });
});
