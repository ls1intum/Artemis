import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ExternalSubmissionDialogComponent } from 'app/exercise/external-submission/external-submission-dialog.component';
import { ExternalSubmissionService } from 'app/exercise/external-submission/external-submission.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { HttpErrorResponse, HttpHeaders, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Subject, throwError } from 'rxjs';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { By } from '@angular/platform-browser';
import { ExternalSubmissionStudentSearchComponent } from 'app/exercise/external-submission/external-submission-student-search.component';
import { ExternalSubmissionCandidate } from 'app/exercise/external-submission/external-submission-candidate.model';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { MockProvider } from 'ng-mocks';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiDialogRef } from '@tumaet/ui-angular/dialog';

describe('ExternalSubmissionDialogComponent', () => {
    let fixture: ComponentFixture<ExternalSubmissionDialogComponent>;
    let component: ExternalSubmissionDialogComponent;
    let externalSubmissionService: ExternalSubmissionService;
    let dialogRef: TumAetUiDialogRef;
    let dialogRefCloseSpy: ReturnType<typeof vi.fn>;
    const httpMock = () => TestBed.inject(HttpTestingController);

    beforeEach(async () => {
        dialogRefCloseSpy = vi.fn();
        dialogRef = { close: dialogRefCloseSpy, dismiss: dialogRefCloseSpy, onClose: new Subject<any>() } as unknown as TumAetUiDialogRef;

        await TestBed.configureTestingModule({
            imports: [ExternalSubmissionDialogComponent],
            providers: [
                { provide: TumAetUiDialogRef, useValue: dialogRef },
                MockProvider(EventManager),
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExternalSubmissionDialogComponent);
        component = fixture.componentInstance;
        externalSubmissionService = TestBed.inject(ExternalSubmissionService);
    });

    afterEach(() => {
        TestBed.inject(HttpTestingController).verify();
        vi.restoreAllMocks();
    });

    it('associates each dynamic feedback label with its own input after feedback is added and removed', async () => {
        fixture.componentRef.setInput('exercise', { id: 2, title: 'Sorting review' } as Exercise);
        component.pushFeedback();
        component.pushFeedback();
        fixture.detectChanges();
        await fixture.whenStable();

        const assertFeedbackLabels = (count: number) => {
            const labels = Array.from(fixture.nativeElement.querySelectorAll('label[for^="feedback"]')) as HTMLLabelElement[];
            expect(labels).toHaveLength(count * 2);
            for (const label of labels) {
                const control = label.parentElement!.querySelector('input, textarea') as HTMLInputElement | HTMLTextAreaElement;
                expect(control).not.toBeNull();
                expect(label.control).toBe(control);
                expect(control.labels).toContain(label);
            }
        };
        assertFeedbackLabels(2);
        component.popFeedback();
        fixture.detectChanges();
        await fixture.whenStable();
        assertFeedbackLabels(1);
    });

    it('should get initial result on init', () => {
        const result = new Result();
        const extServiceSpy = vi.spyOn(externalSubmissionService, 'generateInitialManualResult').mockReturnValue(result);
        component.ngOnInit();
        expect(extServiceSpy).toHaveBeenCalledOnce();
        expect(component.result).toBe(result);
    });

    it('should close the dialog on clear', () => {
        component.clear();
        expect(dialogRefCloseSpy).toHaveBeenCalledOnce();
        expect(dialogRefCloseSpy).toHaveBeenCalledWith('cancel');
    });

    it('should post the result with its manual feedback and close with the created result', () => {
        const result: Result = new Result();
        result.score = 80;
        result.rated = true;
        component.result = result;
        fixture.componentRef.setInput('exercise', { id: 2 } as Exercise);
        component.selectedStudent.set({ login: 'student3', name: 'Student Three' });
        const eventManagerSpy = vi.spyOn(TestBed.inject(EventManager), 'broadcast').mockImplementation(() => {});
        const feedbackA = new Feedback();
        feedbackA.type = FeedbackType.AUTOMATIC;
        feedbackA.detailText = 'detail';
        const feedbackB = new Feedback();
        feedbackB.type = FeedbackType.AUTOMATIC_ADAPTED;
        component.feedbacks = [feedbackA, feedbackB];

        component.save();

        expect(component.isSaving()).toBe(true);
        const request = httpMock().expectOne({ method: 'POST', url: '/api/assessment/exercises/2/external-submission-results?studentLogin=student3' });
        expect(request.request.body).toEqual({
            score: 80,
            rated: true,
            feedbacks: [expect.objectContaining({ type: 'MANUAL', detailText: 'detail' }), expect.objectContaining({ type: 'MANUAL' })],
        });
        expect(dialogRefCloseSpy).not.toHaveBeenCalled();

        request.flush({ id: 9, score: 80 });

        expect(dialogRefCloseSpy).toHaveBeenCalledExactlyOnceWith({ id: 9, score: 80 });
        expect(component.isSaving()).toBe(false);
        expect(eventManagerSpy).toHaveBeenCalledExactlyOnceWith({ name: 'resultListModification', content: 'Added a manual result' });
    });

    it('should set isSaving to false on saveError', () => {
        component.result = new Result();
        fixture.componentRef.setInput('exercise', { id: 2 } as Exercise);
        component.selectedStudent.set({ login: 'student1' });

        component.save();
        expect(component.isSaving()).toBe(true);
        httpMock()
            .expectOne({ method: 'POST', url: '/api/assessment/exercises/2/external-submission-results?studentLogin=student1' })
            .flush(null, { status: 400, statusText: 'Bad Request' });

        expect(component.isSaving()).toBe(false);
        expect(dialogRefCloseSpy).not.toHaveBeenCalled();
    });

    describe('student selection', () => {
        const student: ExternalSubmissionCandidate = { login: 'ga12abc', name: 'Jane Doe' };

        const saveButton = (): HTMLButtonElement => fixture.nativeElement.querySelector('button[type="submit"]');
        const studentSearch = (): ExternalSubmissionStudentSearchComponent => fixture.debugElement.query(By.directive(ExternalSubmissionStudentSearchComponent)).componentInstance;

        const render = async () => {
            fixture.detectChanges();
            await fixture.whenStable();
            fixture.detectChanges();
        };

        beforeEach(async () => {
            fixture.componentRef.setInput('exercise', { id: 2, title: 'Sorting review' } as Exercise);
            await render();
        });

        it('keeps Save disabled until a student is selected and disables it again when the selection is cleared', async () => {
            expect(saveButton().disabled).toBe(true);

            studentSearch().studentSelected.emit(student);
            await render();
            expect(saveButton().disabled).toBe(false);

            studentSearch().studentSelected.emit(undefined);
            await render();
            expect(saveButton().disabled).toBe(true);
        });

        it('shows the missing student only once under the field, and not when the exercise takes no external submissions yet', async () => {
            const searchField = (): HTMLInputElement => fixture.nativeElement.querySelector('input[role="combobox"]');
            const leaveField = async () => {
                searchField().dispatchEvent(new FocusEvent('focusout', { bubbles: true }));
                await render();
            };
            const occurrences = (key: string) => (fixture.nativeElement.textContent as string).split(key).length - 1;

            await leaveField();
            expect(occurrences('artemisApp.result.studentLoginRequired')).toBe(1);

            vi.spyOn(externalSubmissionService, 'searchCandidates').mockReturnValue(
                throwError(() => new HttpErrorResponse({ status: 400, headers: new HttpHeaders({ 'X-artemisApp-error': 'error.externalSubmissionBeforeDueDate' }) })),
            );
            searchField().dispatchEvent(new FocusEvent('focus'));
            searchField().value = 'jane';
            searchField().dispatchEvent(new Event('input', { bubbles: true }));
            await new Promise((resolve) => setTimeout(resolve, 350));
            await leaveField();

            expect(occurrences('error.externalSubmissionBeforeDueDate')).toBeGreaterThan(0);
            expect(occurrences('artemisApp.result.studentLoginRequired')).toBe(0);
        });

        it('posts the login of the selected student when saving', async () => {
            studentSearch().studentSelected.emit(student);
            await render();

            saveButton().click();

            httpMock().expectOne({ method: 'POST', url: '/api/assessment/exercises/2/external-submission-results?studentLogin=ga12abc' });
        });

        it('does not save without a selected student', () => {
            component.save();
            httpMock().expectNone((request) => request.url.includes('external-submission-results'));
            expect(component.isSaving()).toBe(false);
        });
    });

    it('should add a new feedback on pushFeedback and remove last on popFeedback', () => {
        expect(component.feedbacks).toHaveLength(0);
        component.pushFeedback();
        component.pushFeedback();
        expect(component.feedbacks).toHaveLength(2);
        component.popFeedback();
        expect(component.feedbacks).toHaveLength(1);
        component.popFeedback();
        expect(component.feedbacks).toHaveLength(0);
        component.popFeedback();
        expect(component.feedbacks).toHaveLength(0);
    });
});
