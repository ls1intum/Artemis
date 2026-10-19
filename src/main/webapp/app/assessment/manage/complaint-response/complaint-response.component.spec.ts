import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ComplaintResponseComponent } from 'app/assessment/manage/complaint-response/complaint-response.component';
import { Complaint, ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { ComplaintResponse } from 'app/assessment/shared/entities/complaint-response.model';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTimeAgoPipe } from 'app/foundation/pipes/artemis-time-ago.pipe';
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
        vi.restoreAllMocks();
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
            expect(textarea.readOnly).toBe(true);
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

    describe('card', () => {
        it('should show the response text in the text area that the end-to-end tests read', () => {
            fixture.componentRef.setInput('complaint', createComplaint(ComplaintType.COMPLAINT, true));
            fixture.componentRef.setInput('maxComplaintResponseTextLimit', 2000);
            fixture.detectChanges();

            const textarea = fixture.nativeElement.querySelector('[data-testid="complainResponseTextArea"]') as HTMLTextAreaElement;
            expect(textarea.value).toBe('Test response text');
            expect(textarea.readOnly).toBe(true);
            // A disabled text area can be neither focused nor selected.
            expect(textarea.disabled).toBe(false);
        });

        it('should present the response in a bordered, rounded and padded card with the semantic border and surface', () => {
            fixture.componentRef.setInput('complaint', createComplaint(ComplaintType.COMPLAINT, true));
            fixture.componentRef.setInput('maxComplaintResponseTextLimit', 2000);
            fixture.detectChanges();

            const card = fixture.nativeElement.querySelector('[data-testid="complaint-response-card"]') as HTMLElement;
            expect(card).toBeTruthy();
            // The same card language as the complaint next to it and the feedback cards on the page.
            for (const utility of ['rounded-lg', 'border', 'border-(--border-color)', 'bg-(--module-bg)', 'p-4!']) {
                expect(card.classList.contains(utility), utility).toBe(true);
            }
        });

        it('should be a cell of the grid of the complaint area that spans the rows of the header and of the text', () => {
            fixture.componentRef.setInput('complaint', createComplaint(ComplaintType.COMPLAINT, true));
            fixture.componentRef.setInput('maxComplaintResponseTextLimit', 2000);
            fixture.detectChanges();

            const card = fixture.nativeElement.querySelector('[data-testid="complaint-response-card"]') as HTMLElement;
            // The host has no box of its own, so the card is a direct cell of the grid, and the complaint card shares its rows (subgrid).
            expect(fixture.nativeElement.classList.contains('contents')).toBe(true);
            for (const utility of ['row-span-2', 'grid', 'grid-rows-subgrid']) {
                expect(card.classList.contains(utility), utility).toBe(true);
            }
        });

        it('should put the answer time in the header above the text', () => {
            fixture.componentRef.setInput('complaint', createComplaint(ComplaintType.COMPLAINT, true));
            fixture.componentRef.setInput('maxComplaintResponseTextLimit', 2000);
            fixture.detectChanges();

            const card = fixture.nativeElement.querySelector('[data-testid="complaint-response-card"]') as HTMLElement;
            const header = card.firstElementChild as HTMLElement;
            expect(header.querySelector('span')).toBeTruthy();
            expect(header.nextElementSibling).toBe(card.querySelector('textarea'));
        });

        it('should render no card when complaint has no response', () => {
            fixture.componentRef.setInput('complaint', createComplaint(ComplaintType.COMPLAINT, false));
            fixture.componentRef.setInput('maxComplaintResponseTextLimit', 2000);
            fixture.detectChanges();

            expect(fixture.nativeElement.querySelector('[data-testid="complaint-response-card"]')).toBeNull();
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
