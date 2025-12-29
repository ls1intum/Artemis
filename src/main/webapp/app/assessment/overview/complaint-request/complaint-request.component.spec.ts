import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComplaintRequestComponent } from 'app/assessment/overview/complaint-request/complaint-request.component';
import { Complaint, ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import dayjs from 'dayjs/esm';

describe('ComplaintRequestComponent', () => {
    setupTestBed({ zoneless: true });
    let component: ComplaintRequestComponent;
    let fixture: ComponentFixture<ComplaintRequestComponent>;

    const createComplaint = (type: ComplaintType, accepted?: boolean): Complaint => {
        const complaint = new Complaint();
        complaint.id = 1;
        complaint.complaintType = type;
        complaint.complaintText = 'Test complaint text';
        complaint.submittedTime = dayjs();
        complaint.accepted = accepted;
        return complaint;
    };

    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [MockProvider(TranslateService)],
        })
            .overrideComponent(ComplaintRequestComponent, {
                remove: { imports: [TranslateDirective, ArtemisTranslatePipe, ArtemisDatePipe, ArtemisTimeAgoPipe, NgbTooltip] },
                add: {
                    imports: [
                        MockDirective(TranslateDirective),
                        MockPipe(ArtemisTranslatePipe),
                        MockPipe(ArtemisDatePipe),
                        MockPipe(ArtemisTimeAgoPipe),
                        MockDirective(NgbTooltip),
                    ],
                },
            })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ComplaintRequestComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('component creation', () => {
        it('should create the component', () => {
            const complaint = createComplaint(ComplaintType.COMPLAINT);
            fixture.componentRef.setInput('complaint', complaint);
            fixture.componentRef.setInput('maxComplaintTextLimit', 2000);
            fixture.detectChanges();

            expect(component).toBeTruthy();
        });

        it('should expose ComplaintType enum', () => {
            expect(component.ComplaintType).toBe(ComplaintType);
        });
    });

    describe('inputs', () => {
        it('should accept complaint input', () => {
            const complaint = createComplaint(ComplaintType.COMPLAINT);
            fixture.componentRef.setInput('complaint', complaint);
            fixture.componentRef.setInput('maxComplaintTextLimit', 2000);
            fixture.detectChanges();

            expect(component.complaint()).toBe(complaint);
        });

        it('should accept maxComplaintTextLimit input', () => {
            const complaint = createComplaint(ComplaintType.COMPLAINT);
            fixture.componentRef.setInput('complaint', complaint);
            fixture.componentRef.setInput('maxComplaintTextLimit', 5000);
            fixture.detectChanges();

            expect(component.maxComplaintTextLimit()).toBe(5000);
        });
    });

    describe('template rendering', () => {
        it('should display textarea with complaint text', () => {
            const complaint = createComplaint(ComplaintType.COMPLAINT);
            fixture.componentRef.setInput('complaint', complaint);
            fixture.componentRef.setInput('maxComplaintTextLimit', 2000);
            fixture.detectChanges();

            const textarea = fixture.nativeElement.querySelector('textarea');
            expect(textarea).toBeTruthy();
        });

        it('should set textarea as readonly', () => {
            const complaint = createComplaint(ComplaintType.COMPLAINT);
            fixture.componentRef.setInput('complaint', complaint);
            fixture.componentRef.setInput('maxComplaintTextLimit', 2000);
            fixture.detectChanges();

            const textarea = fixture.nativeElement.querySelector('textarea');
            expect(textarea.readOnly).toBe(true);
        });

        it('should set maxLength on textarea', () => {
            const complaint = createComplaint(ComplaintType.COMPLAINT);
            fixture.componentRef.setInput('complaint', complaint);
            fixture.componentRef.setInput('maxComplaintTextLimit', 3000);
            fixture.detectChanges();

            const textarea = fixture.nativeElement.querySelector('textarea');
            expect(textarea.maxLength).toBe(3000);
        });

        it('should show success badge when complaint is accepted', () => {
            const complaint = createComplaint(ComplaintType.COMPLAINT, true);
            fixture.componentRef.setInput('complaint', complaint);
            fixture.componentRef.setInput('maxComplaintTextLimit', 2000);
            fixture.detectChanges();

            const successBadge = fixture.nativeElement.querySelector('.badge.bg-success');
            expect(successBadge).toBeTruthy();
        });

        it('should show danger badge when complaint is rejected', () => {
            const complaint = createComplaint(ComplaintType.COMPLAINT, false);
            fixture.componentRef.setInput('complaint', complaint);
            fixture.componentRef.setInput('maxComplaintTextLimit', 2000);
            fixture.detectChanges();

            const dangerBadge = fixture.nativeElement.querySelector('.badge.bg-danger');
            expect(dangerBadge).toBeTruthy();
        });

        it('should not show any badge when complaint is pending', () => {
            const complaint = createComplaint(ComplaintType.COMPLAINT);
            fixture.componentRef.setInput('complaint', complaint);
            fixture.componentRef.setInput('maxComplaintTextLimit', 2000);
            fixture.detectChanges();

            const badge = fixture.nativeElement.querySelector('.badge');
            expect(badge).toBeFalsy();
        });
    });

    describe('complaint types', () => {
        it('should handle COMPLAINT type', () => {
            const complaint = createComplaint(ComplaintType.COMPLAINT);
            fixture.componentRef.setInput('complaint', complaint);
            fixture.componentRef.setInput('maxComplaintTextLimit', 2000);
            fixture.detectChanges();

            expect(component.complaint().complaintType).toBe(ComplaintType.COMPLAINT);
        });

        it('should handle MORE_FEEDBACK type', () => {
            const complaint = createComplaint(ComplaintType.MORE_FEEDBACK);
            fixture.componentRef.setInput('complaint', complaint);
            fixture.componentRef.setInput('maxComplaintTextLimit', 2000);
            fixture.detectChanges();

            expect(component.complaint().complaintType).toBe(ComplaintType.MORE_FEEDBACK);
        });
    });
});
