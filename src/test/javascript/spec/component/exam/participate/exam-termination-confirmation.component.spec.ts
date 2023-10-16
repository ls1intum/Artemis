import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ExamTerminationConfirmationComponent } from 'app/exam/participate/exam-termination/exam-termination-confirmation/exam-termination-confirmation.component';

describe('ExamTerminationConfirmationComponent', () => {
    let component: ExamTerminationConfirmationComponent;
    let fixture: ComponentFixture<ExamTerminationConfirmationComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [ExamTerminationConfirmationComponent],
        });
        fixture = TestBed.createComponent(ExamTerminationConfirmationComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
