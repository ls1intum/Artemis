import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { Observable, Subject, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExternalSubmissionService } from 'app/exercise/external-submission/external-submission.service';
import { ExternalSubmissionCandidate } from 'app/exercise/external-submission/external-submission-candidate.model';
import { EXTERNAL_SUBMISSION_SEARCH_MIN_LENGTH, ExternalSubmissionStudentSearchComponent } from 'app/exercise/external-submission/external-submission-student-search.component';
import { PageableResult } from 'app/foundation/pagination/pageable-table';

describe('ExternalSubmissionStudentSearchComponent', () => {
    let fixture: ComponentFixture<ExternalSubmissionStudentSearchComponent>;
    let component: ExternalSubmissionStudentSearchComponent;
    let searchCandidates: ReturnType<typeof vi.fn<(...args: unknown[]) => Observable<PageableResult<ExternalSubmissionCandidate>>>>;
    let alertService: AlertService;

    const exercise = { id: 5 } as Exercise;
    const eligible: ExternalSubmissionCandidate = { login: 'ga12abc', name: 'Jane Doe' };
    const ineligible: ExternalSubmissionCandidate = { login: 'ga34def', name: 'John Roe', ineligibility: 'RESULT_ALREADY_EXISTS' };

    const respondWith = (candidates: ExternalSubmissionCandidate[]) => searchCandidates.mockReturnValue(of({ content: candidates, totalElements: candidates.length }));
    const failWith = (error: HttpErrorResponse) => searchCandidates.mockReturnValue(throwError(() => error));

    const input = (): HTMLInputElement => fixture.nativeElement.querySelector('input[role="combobox"]');
    const options = (): HTMLElement[] => Array.from(document.querySelectorAll('[role="option"]'));
    const footer = (): HTMLElement | null => document.querySelector('.tumaet-ui-autocomplete-footer');
    const hint = (): HTMLElement | null => fixture.nativeElement.querySelector('.tumaet-ui-form-field-hint');
    const errorRegion = (): HTMLElement => fixture.nativeElement.querySelector('.tumaet-ui-form-field-error');
    const chip = (): HTMLElement | null => fixture.nativeElement.querySelector('[data-testid="external-submission-selected-student"]');

    /** Changes the text of the input without waiting for the debounce. */
    function changeTo(text: string): void {
        input().dispatchEvent(new FocusEvent('focus'));
        input().value = text;
        input().dispatchEvent(new Event('input', { bubbles: true }));
        fixture.detectChanges();
    }

    async function type(text: string): Promise<void> {
        changeTo(text);
        await vi.advanceTimersByTimeAsync(300);
        fixture.detectChanges();
    }

    beforeEach(async () => {
        vi.useFakeTimers();
        searchCandidates = vi.fn();
        respondWith([]);

        await TestBed.configureTestingModule({
            imports: [ExternalSubmissionStudentSearchComponent],
            providers: [
                { provide: ExternalSubmissionService, useValue: { searchCandidates } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        alertService = TestBed.inject(AlertService);
        vi.spyOn(alertService, 'error');
        vi.spyOn(alertService, 'addAlert');

        fixture = TestBed.createComponent(ExternalSubmissionStudentSearchComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.clearAllTimers();
        vi.useRealTimers();
        vi.restoreAllMocks();
        document.querySelectorAll('.cdk-overlay-container').forEach((container) => (container.innerHTML = ''));
    });

    // U3
    it('searches once after the debounce and renders the options as "Name (login)"', async () => {
        respondWith([eligible]);

        await type('jane');

        expect(searchCandidates).toHaveBeenCalledOnce();
        expect(searchCandidates).toHaveBeenCalledWith(exercise, 'jane', 0, 10);
        expect(options().map((option) => option.textContent?.trim())).toEqual(['Jane Doe (ga12abc)']);
    });

    // U4
    it('shows the hint with the minimum length and sends no request below it', async () => {
        const translate = vi.spyOn(TestBed.inject(TranslateService), 'instant');

        await type('ja');

        expect(searchCandidates).not.toHaveBeenCalled();
        expect(hint()?.textContent).toContain('artemisApp.externalSubmission.searchTooShort');
        expect(translate).toHaveBeenCalledWith('artemisApp.externalSubmission.searchTooShort', { min: EXTERNAL_SUBMISSION_SEARCH_MIN_LENGTH });
        expect(input().getAttribute('aria-describedby')).toBe(hint()?.id);
    });

    // U5
    it('renders an ineligible student as a disabled option with the reason and its full label as title, and does not select it', async () => {
        respondWith([ineligible]);
        const selected = vi.spyOn(component.studentSelected, 'emit');

        await type('john');

        const [option] = options();
        const expectedLabel = 'John Roe (ga34def), artemisApp.externalSubmission.ineligible.resultAlreadyExists';
        expect(option.getAttribute('aria-disabled')).toBe('true');
        expect(option.textContent?.trim()).toBe(expectedLabel);
        expect(option.querySelector('[title]')?.getAttribute('title')).toBe(expectedLabel);

        option.click();
        fixture.detectChanges();

        expect(selected).not.toHaveBeenCalled();
        expect(chip()).toBeNull();
    });

    // U6
    it('shows "No matching students" for an empty response', async () => {
        respondWith([]);

        await type('nobody');

        expect(hint()?.textContent).toContain('artemisApp.externalSubmission.noResults');
        expect(input().getAttribute('aria-describedby')).toBe(hint()?.id);
        expect(alertService.error).not.toHaveBeenCalled();
    });

    it('says that only the first matches are shown when more students match than the suggestions list', async () => {
        const translate = vi.spyOn(TestBed.inject(TranslateService), 'instant');
        const tenStudents = Array.from({ length: 10 }, (_, index) => ({ login: `ga${index}abc`, name: `Jane ${index}` }));
        searchCandidates.mockReturnValue(of({ content: tenStudents, totalElements: 25 }));

        await type('jane');

        expect(options()).toHaveLength(10);
        // the open suggestion list would cover the hint below the field, so the message ends the list instead
        expect(footer()?.textContent).toContain('artemisApp.externalSubmission.moreResults');
        expect(hint()).toBeNull();
        expect(translate).toHaveBeenCalledWith('artemisApp.externalSubmission.moreResults', { shown: 10, total: 25 });
    });

    it('shows no hint about further matches when all matches are listed', async () => {
        respondWith([eligible]);

        await type('jane');

        expect(hint()).toBeNull();
        expect(footer()).toBeNull();
    });

    it('removes the suggestions as soon as the text changes, so a student of the previous search cannot be selected', async () => {
        respondWith([eligible]);
        const selected = vi.spyOn(component.studentSelected, 'emit');
        await type('jane');
        expect(options()).toHaveLength(1);

        changeTo('john');

        expect(options().map((option) => option.textContent?.trim())).toEqual(['global.search.searching']);
        options()[0].click();
        expect(selected).not.toHaveBeenCalled();
    });

    it('ignores the answer to a search whose text has changed in the meantime', async () => {
        const pendingSearch = new Subject<PageableResult<ExternalSubmissionCandidate>>();
        searchCandidates.mockReturnValue(pendingSearch);
        await type('jane');

        changeTo('john');
        pendingSearch.next({ content: [eligible], totalElements: 1 });
        fixture.detectChanges();

        expect(options().map((option) => option.textContent?.trim())).toEqual(['global.search.searching']);
        expect(hint()).toBeNull();
    });

    // U6
    it('shows the generic error inline for a failed request, linked to the input, without a global alert', async () => {
        failWith(new HttpErrorResponse({ status: 500 }));

        await type('jane');

        expect(errorRegion().hidden).toBe(false);
        expect(errorRegion().textContent).toContain('artemisApp.externalSubmission.searchFailed');
        expect(input().getAttribute('aria-describedby')).toBe(errorRegion().id);
        expect(alertService.error).not.toHaveBeenCalled();
        expect(alertService.addAlert).not.toHaveBeenCalled();
    });

    // U11
    it.each(['error.externalSubmissionBeforeDueDate', 'error.externalSubmissionForQuizExercise'])(
        'shows the exercise-level message %s inline, offers no options and emits nothing',
        async (errorKey) => {
            failWith(new HttpErrorResponse({ status: 400, headers: new HttpHeaders({ 'X-artemisApp-error': errorKey }) }));
            const selected = vi.spyOn(component.studentSelected, 'emit');

            await type('jane');

            expect(errorRegion().hidden).toBe(false);
            expect(errorRegion().textContent).toContain(errorKey);
            // no student is offered; an open panel only repeats the error as its disabled empty entry
            expect(options().every((option) => option.getAttribute('aria-disabled') === 'true' && option.textContent?.includes(errorKey))).toBe(true);
            expect(selected).not.toHaveBeenCalled();
            expect(alertService.error).not.toHaveBeenCalled();
            expect(alertService.addAlert).not.toHaveBeenCalled();
        },
    );

    // U7
    it('emits the selected student, shows the chip, and emits undefined and shows the input again when the chip is cleared', async () => {
        respondWith([eligible]);
        const selected = vi.spyOn(component.studentSelected, 'emit');

        await type('jane');
        options()[0].click();
        fixture.detectChanges();

        expect(selected).toHaveBeenLastCalledWith(eligible);
        expect(chip()?.textContent).toContain('Jane Doe (ga12abc)');
        expect(input()).toBeNull();

        const removeButton: HTMLButtonElement = chip()!.querySelector('button')!;
        removeButton.click();
        fixture.detectChanges();

        expect(selected).toHaveBeenLastCalledWith(undefined);
        expect(chip()).toBeNull();
        expect(input()).not.toBeNull();
        expect(input().value).toBe('');
    });

    // U7
    it('says that a student is missing once the field was left without one, and not before', () => {
        expect(errorRegion().hidden).toBe(true);

        input().dispatchEvent(new FocusEvent('focusout', { bubbles: true }));
        fixture.detectChanges();

        expect(errorRegion().hidden).toBe(false);
        expect(errorRegion().textContent).toContain('artemisApp.result.studentLoginRequired');
        expect(input().getAttribute('aria-describedby')).toBe(errorRegion().id);
    });

    it('shows only the exercise-level message, without the missing student, when the field is left before the due date', async () => {
        failWith(new HttpErrorResponse({ status: 400, headers: new HttpHeaders({ 'X-artemisApp-error': 'error.externalSubmissionBeforeDueDate' }) }));

        await type('jane');
        input().dispatchEvent(new FocusEvent('focusout', { bubbles: true }));
        fixture.detectChanges();

        expect(errorRegion().textContent).toContain('error.externalSubmissionBeforeDueDate');
        expect(fixture.nativeElement.textContent).not.toContain('artemisApp.result.studentLoginRequired');
    });
});
