import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ExternalSubmissionDialogComponent } from 'app/exercise/external-submission/external-submission-dialog.component';
import { ExternalSubmissionService } from 'app/exercise/external-submission/external-submission.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
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
import { TumAetUiDialogRef } from '@tumaet/ui-angular';

describe('ExternalSubmissionDialogComponent', () => {
    let fixture: ComponentFixture<ExternalSubmissionDialogComponent>;
    let component: ExternalSubmissionDialogComponent;
    let externalSubmissionService: ExternalSubmissionService;
    let dialogRef: TumAetUiDialogRef;
    let dialogRefCloseSpy: ReturnType<typeof vi.fn>;

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
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExternalSubmissionDialogComponent);
        component = fixture.componentInstance;
        externalSubmissionService = TestBed.inject(ExternalSubmissionService);
    });

    afterEach(() => {
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

    it('should save feedback correctly', () => {
        const result: Result = new Result();
        component.result = result;
        const exercise = { id: 2 } as Exercise;
        fixture.componentRef.setInput('exercise', exercise);
        const student: ExternalSubmissionCandidate = { login: 'student3', name: 'Student Three' };
        component.selectedStudent.set(student);

        const subject: Subject<HttpResponse<Result>> = new Subject<HttpResponse<Result>>();
        const createMock = vi.spyOn(externalSubmissionService, 'create').mockReturnValue(subject.asObservable());

        const eventManager = TestBed.inject(EventManager);
        const eventManagerSpy = vi.spyOn(eventManager, 'broadcast').mockImplementation(() => {});

        component.feedbacks = [new Feedback(), new Feedback()];

        component.feedbacks[0].type = FeedbackType.AUTOMATIC;
        component.feedbacks[1].type = FeedbackType.AUTOMATIC_ADAPTED;

        component.save();

        expect(component.isSaving()).toBe(true);
        expect(result.feedbacks).toBe(component.feedbacks);
        expect(result.feedbacks?.every((feedback) => feedback.type === FeedbackType.MANUAL)).toBe(true);
        expect(createMock).toHaveBeenCalledOnce();
        expect(createMock).toHaveBeenCalledWith(exercise, student, result);
        expect(dialogRefCloseSpy).not.toHaveBeenCalled();

        subject.next(new HttpResponse<Result>({ body: result }));

        expect(dialogRefCloseSpy).toHaveBeenCalledOnce();
        expect(dialogRefCloseSpy).toHaveBeenCalledWith(result);
        expect(component.isSaving()).toBe(false);
        expect(eventManagerSpy).toHaveBeenCalledOnce();
        expect(eventManagerSpy).toHaveBeenCalledWith({ name: 'resultListModification', content: 'Added a manual result' });
    });

    it('should set isSaving to false on saveError', () => {
        component.result = new Result();
        component.isSaving.set(true);
        fixture.componentRef.setInput('exercise', { id: 2 } as Exercise);
        component.selectedStudent.set({ login: 'student1' });

        const createMock = vi.spyOn(externalSubmissionService, 'create').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
        const onSaveErrorSpy = vi.spyOn(component, 'onSaveError');

        component.save();
        expect(createMock).toHaveBeenCalledOnce();
        expect(onSaveErrorSpy).toHaveBeenCalledOnce();
        expect(component.isSaving()).toBe(false);
    });

    describe('student selection', () => {
        const student: ExternalSubmissionCandidate = { login: 'ga12abc', name: 'Jane Doe' };

        const saveButton = (): HTMLButtonElement => fixture.nativeElement.querySelector('button[type="submit"]');
        const requiredMessage = (): HTMLElement | null => fixture.nativeElement.querySelector('[data-testid="external-submission-student-required"]');
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

        it('shows the required message once the search was left without a selection, and hides it when a student is selected', async () => {
            expect(requiredMessage()).toBeNull();

            studentSearch().touched.emit();
            await render();
            expect(requiredMessage()).not.toBeNull();

            studentSearch().studentSelected.emit(student);
            await render();
            expect(requiredMessage()).toBeNull();
        });

        it('posts the login of the selected student when saving', async () => {
            const createMock = vi.spyOn(externalSubmissionService, 'create').mockReturnValue(new Subject<HttpResponse<Result>>().asObservable());
            studentSearch().studentSelected.emit(student);
            await render();

            saveButton().click();

            expect(createMock).toHaveBeenCalledOnce();
            expect(createMock.mock.calls[0][1].login).toBe('ga12abc');
        });

        it('does not save without a selected student', () => {
            const createMock = vi.spyOn(externalSubmissionService, 'create');
            component.save();
            expect(createMock).not.toHaveBeenCalled();
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
