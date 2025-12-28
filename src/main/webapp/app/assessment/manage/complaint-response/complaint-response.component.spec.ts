import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ComplaintResponseComponent } from 'app/assessment/manage/complaint-response/complaint-response.component';
import { Complaint, ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { ComplaintResponse } from 'app/assessment/shared/entities/complaint-response.model';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import dayjs from 'dayjs/esm';

describe('ComplaintResponseComponent', () => {
    let component: ComplaintResponseComponent;
    let fixture: ComponentFixture<ComplaintResponseComponent>;

    const createComplaint = (type: ComplaintType, withResponse = false): Complaint => {
        const complaint = new Complaint();
        complaint.id = 1;
        complaint.complaintType = type;
        complaint.complaintText = 'Test complaint text';
        complaint.submittedTime = dayjs();

        if (withResponse) {
            const response = new ComplaintResponse();
            response.id = 1;
            response.responseText = 'Test response text';
            response.submittedTime = dayjs();
            complaint.complaintResponse = response;
        }

        return complaint;
    };

    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [MockProvider(TranslateService)],
        })
            .overrideComponent(ComplaintResponseComponent, {
                remove: { imports: [ArtemisTranslatePipe, ArtemisDatePipe, ArtemisTimeAgoPipe, NgbTooltip] },
                add: { imports: [MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe), MockPipe(ArtemisTimeAgoPipe), MockDirective(NgbTooltip)] },
            })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ComplaintResponseComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('component creation', () => {
        it('should create the component', () => {
            const complaint = createComplaint(ComplaintType.COMPLAINT, true);
            fixture.componentRef.setInput('complaint', complaint);
            fixture.componentRef.setInput('maxComplaintResponseTextLimit', 2000);
            fixture.detectChanges();

            expect(component).toBeTruthy();
        });

        it('should expose ComplaintType enum', () => {
            expect(component.ComplaintType).toBe(ComplaintType);
        });
    });

    describe('inputs', () => {
        it('should accept complaint input', () => {
            const complaint = createComplaint(ComplaintType.COMPLAINT, true);
            fixture.componentRef.setInput('complaint', complaint);
            fixture.componentRef.setInput('maxComplaintResponseTextLimit', 2000);
            fixture.detectChanges();

            expect(component.complaint()).toBe(complaint);
        });

        it('should accept maxComplaintResponseTextLimit input', () => {
            const complaint = createComplaint(ComplaintType.COMPLAINT, true);
            fixture.componentRef.setInput('complaint', complaint);
            fixture.componentRef.setInput('maxComplaintResponseTextLimit', 5000);
            fixture.detectChanges();

            expect(component.maxComplaintResponseTextLimit()).toBe(5000);
        });
    });

    describe('template rendering', () => {
        it('should display response when complaint has a response', () => {
            const complaint = createComplaint(ComplaintType.COMPLAINT, true);
            fixture.componentRef.setInput('complaint', complaint);
            fixture.componentRef.setInput('maxComplaintResponseTextLimit', 2000);
            fixture.detectChanges();

            const textarea = fixture.nativeElement.querySelector('textarea');
            expect(textarea).toBeTruthy();
        });

        it('should not display content when complaint has no response', () => {
            const complaint = createComplaint(ComplaintType.COMPLAINT, false);
            fixture.componentRef.setInput('complaint', complaint);
            fixture.componentRef.setInput('maxComplaintResponseTextLimit', 2000);
            fixture.detectChanges();

            const textarea = fixture.nativeElement.querySelector('textarea');
            expect(textarea).toBeFalsy();
        });

        it('should set textarea as readonly', () => {
            const complaint = createComplaint(ComplaintType.COMPLAINT, true);
            fixture.componentRef.setInput('complaint', complaint);
            fixture.componentRef.setInput('maxComplaintResponseTextLimit', 2000);
            fixture.detectChanges();

            const textarea = fixture.nativeElement.querySelector('textarea');
            expect(textarea.readOnly).toBeTrue();
        });

        it('should set maxLength on textarea', () => {
            const complaint = createComplaint(ComplaintType.COMPLAINT, true);
            fixture.componentRef.setInput('complaint', complaint);
            fixture.componentRef.setInput('maxComplaintResponseTextLimit', 3000);
            fixture.detectChanges();

            const textarea = fixture.nativeElement.querySelector('textarea');
            expect(textarea.maxLength).toBe(3000);
        });
    });

    describe('complaint types', () => {
        it('should handle COMPLAINT type', () => {
            const complaint = createComplaint(ComplaintType.COMPLAINT, true);
            fixture.componentRef.setInput('complaint', complaint);
            fixture.componentRef.setInput('maxComplaintResponseTextLimit', 2000);
            fixture.detectChanges();

            expect(component.complaint().complaintType).toBe(ComplaintType.COMPLAINT);
        });

        it('should handle MORE_FEEDBACK type', () => {
            const complaint = createComplaint(ComplaintType.MORE_FEEDBACK, true);
            fixture.componentRef.setInput('complaint', complaint);
            fixture.componentRef.setInput('maxComplaintResponseTextLimit', 2000);
            fixture.detectChanges();

            expect(component.complaint().complaintType).toBe(ComplaintType.MORE_FEEDBACK);
        });
    });
});
