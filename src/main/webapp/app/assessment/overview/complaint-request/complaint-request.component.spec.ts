import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ComplaintRequestComponent } from 'app/assessment/overview/complaint-request/complaint-request.component';
import { Complaint, ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTimeAgoPipe } from 'app/foundation/pipes/artemis-time-ago.pipe';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import dayjs from 'dayjs/esm';

describe('ComplaintRequestComponent', () => {
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

        it('should show the success tag when complaint is accepted', () => {
            const complaint = createComplaint(ComplaintType.COMPLAINT, true);
            fixture.componentRef.setInput('complaint', complaint);
            fixture.componentRef.setInput('maxComplaintTextLimit', 2000);
            fixture.detectChanges();

            const tag = fixture.nativeElement.querySelector('[data-testid="complaint-status-badge"]');
            expect(tag).toBeTruthy();
            expect(tag.tagName.toLowerCase()).toBe('tumaet-ui-tag');
            expect(tag.querySelector('[data-severity="success"]')).toBeTruthy();
        });

        it('should show the danger tag when complaint is rejected', () => {
            const complaint = createComplaint(ComplaintType.COMPLAINT, false);
            fixture.componentRef.setInput('complaint', complaint);
            fixture.componentRef.setInput('maxComplaintTextLimit', 2000);
            fixture.detectChanges();

            const tag = fixture.nativeElement.querySelector('[data-testid="complaint-status-badge"]');
            expect(tag).toBeTruthy();
            expect(tag.querySelector('[data-severity="danger"]')).toBeTruthy();
        });

        it('should not show any tag when complaint is pending', () => {
            const complaint = createComplaint(ComplaintType.COMPLAINT);
            fixture.componentRef.setInput('complaint', complaint);
            fixture.componentRef.setInput('maxComplaintTextLimit', 2000);
            fixture.detectChanges();

            expect(fixture.nativeElement.querySelector('[data-testid="complaint-status-badge"]')).toBeFalsy();
            expect(fixture.nativeElement.querySelector('tumaet-ui-tag')).toBeFalsy();
        });

        it('should show the complaint text in the text area', () => {
            const complaint = createComplaint(ComplaintType.COMPLAINT);
            fixture.componentRef.setInput('complaint', complaint);
            fixture.componentRef.setInput('maxComplaintTextLimit', 2000);
            fixture.detectChanges();

            const textarea = fixture.nativeElement.querySelector('#complainTextArea') as HTMLTextAreaElement;
            expect(textarea.value).toBe('Test complaint text');
        });

        it('should keep the text focusable and selectable, which a disabled text area is not', () => {
            const complaint = createComplaint(ComplaintType.COMPLAINT);
            fixture.componentRef.setInput('complaint', complaint);
            fixture.componentRef.setInput('maxComplaintTextLimit', 2000);
            fixture.detectChanges();

            const textarea = fixture.nativeElement.querySelector('#complainTextArea') as HTMLTextAreaElement;
            expect(textarea.readOnly).toBe(true);
            expect(textarea.disabled).toBe(false);
        });
    });

    describe('card', () => {
        beforeEach(() => {
            fixture.componentRef.setInput('complaint', createComplaint(ComplaintType.COMPLAINT, false));
            fixture.componentRef.setInput('maxComplaintTextLimit', 2000);
            fixture.detectChanges();
        });

        it('should present the complaint in a bordered, rounded and padded card with the semantic border and surface', () => {
            const card = fixture.nativeElement.querySelector('[data-testid="complaint-request-card"]') as HTMLElement;

            expect(card).toBeTruthy();
            // The same card language as the feedback cards on the page. The important padding is needed because Bootstrap pads and sizes by its own scale.
            for (const utility of ['rounded-lg', 'border', 'border-(--border-color)', 'bg-(--module-bg)', 'p-4!']) {
                expect(card.classList.contains(utility), utility).toBe(true);
            }
        });

        it('should put the submission time and the status tag together in the header above the text', () => {
            const card = fixture.nativeElement.querySelector('[data-testid="complaint-request-card"]') as HTMLElement;
            const header = card.firstElementChild as HTMLElement;
            const textarea = card.querySelector('#complainTextArea') as HTMLElement;

            expect(header.querySelector('span')).toBeTruthy();
            expect(header.querySelector('[data-testid="complaint-status-badge"]')).toBeTruthy();
            expect(header.nextElementSibling).toBe(textarea);
        });

        it('should be a cell of the grid of the complaint area that spans the rows of the header and of the text', () => {
            const card = fixture.nativeElement.querySelector('[data-testid="complaint-request-card"]') as HTMLElement;

            // The host has no box of its own, so the card is a direct cell of the grid, and the response card shares its rows (subgrid).
            expect(fixture.nativeElement.classList.contains('contents')).toBe(true);
            for (const utility of ['row-span-2', 'grid', 'grid-rows-subgrid']) {
                expect(card.classList.contains(utility), utility).toBe(true);
            }
        });

        it('should not use any Bootstrap class', () => {
            const classes = Array.from(fixture.nativeElement.querySelectorAll('[class]')).flatMap((element) => Array.from((element as HTMLElement).classList));

            expect(classes.filter((name) => ['badge', 'bg-success', 'bg-danger', 'row'].includes(name) || /^col-/.test(name))).toEqual([]);
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
