import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { TumAetUiAutoCompleteComponent, TumAetUiDatePickerComponent } from '@tumaet/ui-angular';
import { MockComponent } from 'ng-mocks';
import { firstValueFrom, of, throwError } from 'rxjs';
import { typeIntoInputNumber } from 'src/test/javascript/spec/helpers/input-number-test-helpers';
import { Mock, beforeEach, describe, expect, it, vi } from 'vitest';
import dayjs from 'dayjs/esm';

import {
    PresentationAssessmentInstanceFormDialogComponent,
    PresentationAssessmentInstanceFormResult,
} from 'app/presentation/manage/presentation-assessment-instance-form-dialog.component';
import { TranslateService } from '@ngx-translate/core';
import { User, UserNameAndLoginDTO } from 'app/account/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { PresentationAssessmentMode } from 'app/presentation/shared/entities/presentation-assessment.model';
import { PresentationAssessmentPresenterSelectorComponent } from 'app/presentation/manage/presentation-assessment-presenter-selector.component';
import { PresentationAssessmentService } from 'app/presentation/manage/presentation-assessment.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

describe('PresentationAssessmentInstanceFormDialogComponent', () => {
    let fixture: ComponentFixture<PresentationAssessmentInstanceFormDialogComponent>;
    let component: PresentationAssessmentInstanceFormDialogComponent;
    let saved: Mock<(value: PresentationAssessmentInstanceFormResult) => void>;
    let translateService: MockTranslateService;
    let searchAssessors: ReturnType<typeof vi.fn>;

    const presentationDate = dayjs('2026-07-31T13:26:00');

    beforeEach(async () => {
        saved = vi.fn();
        translateService = new MockTranslateService();
        searchAssessors = vi.fn().mockReturnValue(of(new HttpResponse<UserNameAndLoginDTO[]>({ body: [] })));

        await TestBed.configureTestingModule({
            imports: [PresentationAssessmentInstanceFormDialogComponent],
            providers: [
                { provide: TranslateService, useValue: translateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: PresentationAssessmentService, useValue: { searchAssessors } },
                { provide: AlertService, useValue: { error: vi.fn() } },
            ],
        })
            // The presenter selector searches students on the server and is tested on its own.
            .overrideComponent(PresentationAssessmentInstanceFormDialogComponent, {
                remove: { imports: [PresentationAssessmentPresenterSelectorComponent] },
                add: { imports: [MockComponent(PresentationAssessmentPresenterSelectorComponent)] },
            })
            .compileComponents();

        TestBed.inject(AccountService).userIdentity.set(Object.assign(new User(), { login: 'tutor1', name: 'Tutor One' }));
        fixture = TestBed.createComponent(PresentationAssessmentInstanceFormDialogComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', 1);
        fixture.componentRef.setInput('presentationAssessment', { id: 42, maxPoints: 20 });
        fixture.componentRef.setInput('instance', { id: 11, presentationDate, student: { login: 'student1' } });
        fixture.componentRef.setInput('initialAssignedStudents', [new User(undefined, 'student1')]);
        component.saved.subscribe(saved);
        fixture.detectChanges();
    });

    function query(selector: string): HTMLElement | null {
        return fixture.nativeElement.querySelector(selector);
    }

    it('should split an existing presentation timestamp into date and time controls', () => {
        expect(component.editForm.controls.presentationDate.value?.hour()).toBe(0);
        expect(component.editForm.controls.presentationTime.value?.hour()).toBe(13);
        expect(component.editForm.controls.presentationTime.value?.minute()).toBe(26);
    });

    it('should obtain localized language option labels from the translation service', () => {
        expect(component.languageOptions()).toEqual([
            { label: 'artemisApp.presentationAssessment.languageOptions.english', value: 'en' },
            { label: 'artemisApp.presentationAssessment.languageOptions.german', value: 'de' },
        ]);
    });

    it('should refresh both option lists when the active language changes', () => {
        component.languageOptions();
        component.modeOptions();
        vi.spyOn(translateService, 'instant').mockImplementation((key) => `de:${key}`);
        translateService.onLangChangeSubject.next({ lang: 'de', translations: {} });

        expect(component.languageOptions().map((option) => option.label)).toEqual([
            'de:artemisApp.presentationAssessment.languageOptions.english',
            'de:artemisApp.presentationAssessment.languageOptions.german',
        ]);
        expect(component.modeOptions().map((option) => option.label)).toEqual([
            'de:artemisApp.presentationAssessment.mode.online',
            'de:artemisApp.presentationAssessment.mode.inPerson',
        ]);
    });

    it('should refresh option labels when translations are loaded', () => {
        component.languageOptions();
        component.modeOptions();
        vi.spyOn(translateService, 'instant').mockImplementation((key) => `loaded:${key}`);
        translateService.onTranslationChangeSubject.next('en');

        expect(component.languageOptions()[0].label).toBe('loaded:artemisApp.presentationAssessment.languageOptions.english');
        expect(component.modeOptions()[0].label).toBe('loaded:artemisApp.presentationAssessment.mode.online');
    });

    it('should require a presentation date', () => {
        component.editForm.controls.presentationDate.setValue(undefined);

        expect(component.editForm.controls.presentationDate.touched).toBe(false);

        component.save();

        expect(saved).not.toHaveBeenCalled();
        expect(component.editForm.controls.presentationDate.invalid).toBe(true);
        expect(component.editForm.controls.presentationDate.touched).toBe(true);
    });

    it('should reject presentation dates before 1970', () => {
        component.editForm.controls.presentationDate.setValue(dayjs('1969-12-31T00:00:00'));

        expect(component.editForm.controls.presentationDate.hasError('minDate')).toBe(true);
    });

    it('should accept decimal and reject excessive result points', () => {
        component.editForm.controls.resultPoints.setValue(1.125);
        expect(component.editForm.controls.resultPoints.valid).toBe(true);

        component.editForm.controls.resultPoints.setValue(10001);
        expect(component.editForm.controls.resultPoints.hasError('max')).toBe(true);
    });

    it('should take typed result points with a comma and up to three fraction digits', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        const input = query('input#presentation-instance-result') as HTMLInputElement;

        typeIntoInputNumber(input, '8,125');
        expect(component.editForm.controls.resultPoints.value).toBe(8.125);
        expect(component.editForm.controls.resultPoints.valid).toBe(true);

        typeIntoInputNumber(input, '8.12345');
        expect(component.editForm.controls.resultPoints.value).toBe(8.123);
    });

    it('should combine the mandatory date with the optional time', () => {
        component.editForm.controls.presentationDate.setValue(dayjs('2026-08-10T00:00:00'));
        component.editForm.controls.presentationTime.setValue(dayjs('2026-08-03T14:45:00'));

        component.save();

        const result = saved.mock.calls[0][0];
        expect(result.kind).toBe('update');
        if (result.kind !== 'update') {
            throw new Error('Expected an update result');
        }
        expect(result.instance.presentationDate).toBeDefined();
        expect(result.instance.presentationDate!.format('YYYY-MM-DD HH:mm')).toBe('2026-08-10 14:45');
    });

    it('should prevent saving an excessive remark and allow saving after shortening it', () => {
        component.editForm.controls.remark.setValue('a'.repeat(1001));
        component.save();
        expect(component.editForm.controls.remark.getError('maxlength')).toEqual({ requiredLength: 1000, actualLength: 1001 });
        expect(saved).not.toHaveBeenCalled();

        component.editForm.controls.remark.setValue('a'.repeat(1000));
        component.save();
        expect(component.editForm.controls.remark.valid).toBe(true);
        expect(saved).toHaveBeenCalledOnce();
    });

    it('should include a trimmed remark when saving an instance', () => {
        component.editForm.controls.remark.setValue('  Strong presentation  ');

        component.save();

        expect(saved).toHaveBeenCalledWith({
            kind: 'update',
            instance: expect.objectContaining({ remark: 'Strong presentation' }),
        });
    });

    it('should emit a creation request for multiple selected students', () => {
        fixture.componentRef.setInput('instance', undefined);
        fixture.componentRef.setInput('initialAssignedStudents', [new User(undefined, 'student1'), new User(undefined, 'student2')]);
        fixture.detectChanges();

        component.editForm.controls.presentationDate.setValue(presentationDate);
        component.editForm.controls.resultPoints.setValue(8);

        component.save();

        expect(saved).toHaveBeenCalledExactlyOnceWith({
            kind: 'create',
            request: expect.objectContaining({
                studentLogins: ['student1', 'student2'],
                assessorLogin: 'tutor1',
                resultPoints: 8,
                language: 'en',
                mode: PresentationAssessmentMode.IN_PERSON,
            }),
        });
    });

    it('should preserve the existing student assignment when updating', () => {
        component.assignedStudents.set([new User(undefined, 'student2')]);
        component.editForm.controls.resultPoints.setValue(9);

        component.save();

        expect(saved).toHaveBeenCalledExactlyOnceWith({
            kind: 'update',
            instance: expect.objectContaining({
                id: 11,
                studentLogin: 'student1',
                resultPoints: 9,
                assessorLogin: 'tutor1',
            }),
        });
    });

    it('should prefill the current user as assessor and show it disabled for tutors', () => {
        const assessorField = query('[data-testid="presentation-instance-assessor"]') as HTMLInputElement;

        expect(assessorField.disabled).toBe(true);
        expect(assessorField.value).toBe('Tutor One (tutor1)');
        expect(query('[data-testid="presentation-instance-assessor-search"]')).toBeNull();
    });

    it('should show the initials of the assessor only while one is set', () => {
        expect(query('#presentation-instance-assessor-default-picture')?.textContent?.trim()).toBe('TO');

        fixture.componentRef.setInput('canChooseAssessor', true);
        component.editForm.controls.assessorOption.setValue(undefined);
        fixture.detectChanges();

        expect(query('#presentation-instance-assessor-default-picture')).toBeNull();
    });

    it('should keep the existing assessor of an instance', () => {
        fixture.componentRef.setInput('instance', { id: 11, presentationDate, student: { login: 'student1' }, assessor: { login: 'editor1', name: 'Editor One' } });
        fixture.detectChanges();

        expect((query('[data-testid="presentation-instance-assessor"]') as HTMLInputElement).value).toBe('Editor One (editor1)');

        component.save();

        expect(saved).toHaveBeenCalledExactlyOnceWith({ kind: 'update', instance: expect.objectContaining({ assessorLogin: 'editor1' }) });
    });

    it('should offer the assessor search to editors and instructors', () => {
        fixture.componentRef.setInput('canChooseAssessor', true);
        fixture.detectChanges();

        expect(query('[data-testid="presentation-instance-assessor-search"]')).not.toBeNull();
        expect(query('[data-testid="presentation-instance-assessor"]')).toBeNull();
    });

    it('should not search for assessors with terms shorter than three characters', async () => {
        const result = await firstValueFrom(component.searchAssessors(of('  ab  ')));

        expect(result).toEqual([]);
        expect(searchAssessors).not.toHaveBeenCalled();
    });

    it('should report a failed assessor search instead of showing no assessors', async () => {
        searchAssessors.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

        const result = await firstValueFrom(component.searchAssessors(of('grace')));

        expect(result).toEqual([]);
        expect(TestBed.inject(AlertService).error).toHaveBeenCalledExactlyOnceWith('artemisApp.presentationAssessment.assessorSearchFailed');
    });

    it('should provide assessor suggestions with name and login', async () => {
        const assessor: UserNameAndLoginDTO = { login: 'ghopper', name: 'Grace Hopper' };
        searchAssessors.mockReturnValue(of(new HttpResponse<UserNameAndLoginDTO[]>({ body: [assessor] })));
        fixture.componentRef.setInput('canChooseAssessor', true);
        fixture.detectChanges();

        const autocomplete = fixture.debugElement.query(By.directive(TumAetUiAutoCompleteComponent)).componentInstance as TumAetUiAutoCompleteComponent;

        autocomplete.searchRequested.emit({ query: 'grace' });

        await vi.waitFor(() => {
            fixture.detectChanges();
            expect(autocomplete.suggestions()).toEqual([{ assessor, label: 'Grace Hopper (ghopper)' }]);
        });

        expect(searchAssessors).toHaveBeenCalledWith(1, 'grace');
    });

    it('should send the chosen assessor', () => {
        fixture.componentRef.setInput('canChooseAssessor', true);
        fixture.detectChanges();
        component.editForm.controls.assessorOption.setValue({ assessor: { login: 'editor1', name: 'Editor One' }, label: 'Editor One (editor1)' });

        component.save();

        expect(saved).toHaveBeenCalledExactlyOnceWith({ kind: 'update', instance: expect.objectContaining({ assessorLogin: 'editor1' }) });
    });

    it('should not save while typed assessor text has not been selected from the list', () => {
        fixture.componentRef.setInput('canChooseAssessor', true);
        fixture.detectChanges();
        const input = query('#presentation-instance-assessor') as HTMLInputElement;

        input.value = 'gra';
        input.dispatchEvent(new Event('input'));
        fixture.detectChanges();

        expect(component.editForm.controls.assessorOption.hasError('assessorNotSelected')).toBe(true);
        expect(query('#presentation-instance-assessor-default-picture')).toBeNull();

        component.save();
        fixture.detectChanges();

        expect(saved).not.toHaveBeenCalled();
        expect(query('[data-testid="presentation-instance-assessor-missing"]')).not.toBeNull();
    });

    it('should save once an assessor is selected after typing', () => {
        fixture.componentRef.setInput('canChooseAssessor', true);
        fixture.detectChanges();
        const input = query('#presentation-instance-assessor') as HTMLInputElement;
        input.value = 'edi';
        input.dispatchEvent(new Event('input'));

        component.editForm.controls.assessorOption.setValue({ assessor: { login: 'editor1', name: 'Editor One' }, label: 'Editor One (editor1)' });
        component.save();

        expect(saved).toHaveBeenCalledExactlyOnceWith({ kind: 'update', instance: expect.objectContaining({ assessorLogin: 'editor1' }) });
    });

    it('should not save without an assessor and ask for one', () => {
        fixture.componentRef.setInput('canChooseAssessor', true);
        fixture.detectChanges();
        component.editForm.controls.assessorOption.setValue(undefined);

        component.save();
        fixture.detectChanges();

        expect(saved).not.toHaveBeenCalled();
        expect(query('[data-testid="presentation-instance-assessor-missing"]')).not.toBeNull();
    });

    it('should retain a valid meeting link when switching away from online mode', () => {
        const meetingLink = 'https://example.org/presentation';
        component.editForm.controls.mode.setValue(PresentationAssessmentMode.ONLINE);
        component.editForm.controls.meetingLink.setValue(meetingLink);

        component.editForm.controls.mode.setValue(PresentationAssessmentMode.IN_PERSON);

        expect(component.editForm.controls.meetingLink.value).toBe(meetingLink);
    });

    it('should clear an invalid meeting link when switching away from online mode', () => {
        component.editForm.controls.mode.setValue(PresentationAssessmentMode.ONLINE);
        component.editForm.controls.meetingLink.setValue('a'.repeat(1001));

        component.editForm.controls.mode.setValue(PresentationAssessmentMode.IN_PERSON);

        expect(component.editForm.controls.meetingLink.value).toBe('');
    });

    it.each([
        ['https://zoom.example.org/j/123', false],
        ['HTTP://example.org/meeting', false],
        ['', false],
        ['zoom.example.org/j/123', true],
        ['/j/123', true],
        ['javascript:alert(1)', true],
        ['https://', true],
    ])('should validate the meeting link "%s" (invalid: %s)', (meetingLink, invalid) => {
        component.editForm.controls.mode.setValue(PresentationAssessmentMode.ONLINE);

        component.editForm.controls.meetingLink.setValue(meetingLink);

        expect(component.editForm.controls.meetingLink.hasError('pattern')).toBe(invalid);
    });

    it('should retain a valid location when switching away from in-person mode', () => {
        const location = 'Room 101';
        component.editForm.controls.location.setValue(location);

        component.editForm.controls.mode.setValue(PresentationAssessmentMode.ONLINE);

        expect(component.editForm.controls.location.value).toBe(location);
    });

    it('should clear an invalid location when switching away from in-person mode', () => {
        component.editForm.controls.location.setValue('a'.repeat(256));

        component.editForm.controls.mode.setValue(PresentationAssessmentMode.ONLINE);

        expect(component.editForm.controls.location.value).toBe('');
    });

    it('should transfer picker values to the form controls', () => {
        const [datePicker, timePicker] = fixture.debugElement
            .queryAll(By.directive(TumAetUiDatePickerComponent))
            .map((element) => element.componentInstance as TumAetUiDatePickerComponent);
        const date = dayjs('2026-08-10');
        const time = dayjs('2026-08-10T14:45');

        datePicker.value.set(date);
        timePicker.value.set(time);

        expect(component.editForm.controls.presentationDate.value).toEqual(date);
        expect(component.editForm.controls.presentationTime.value).toEqual(time);

        datePicker.touch.emit();
        timePicker.touch.emit();

        expect(component.editForm.controls.presentationDate.touched).toBe(true);
        expect(component.editForm.controls.presentationTime.touched).toBe(true);
    });

    it.each([0, 1])('should prevent saving while picker %i contains invalid text', (pickerIndex) => {
        const picker = fixture.debugElement.queryAll(By.directive(TumAetUiDatePickerComponent))[pickerIndex].componentInstance as TumAetUiDatePickerComponent;

        picker.inputValidityChange.emit(false);
        component.save();

        expect(saved).not.toHaveBeenCalled();

        picker.inputValidityChange.emit(true);
        component.save();

        expect(saved).toHaveBeenCalledOnce();
    });

    it('should show the location field in person and the meeting link field online', () => {
        expect(query('#presentation-instance-location')).not.toBeNull();
        expect(query('#presentation-instance-link')).toBeNull();

        fixture.componentRef.setInput('instance', {
            id: 11,
            presentationDate: dayjs('2026-07-31T13:26:00'),
            student: { login: 'student1' },
            mode: PresentationAssessmentMode.ONLINE,
            meetingLink: 'https://example.org/presentation',
        });
        fixture.detectChanges();

        expect(query('#presentation-instance-location')).toBeNull();
        expect(query('#presentation-instance-link')).not.toBeNull();
    });

    it('should explain an invalid meeting link', () => {
        component.editForm.controls.mode.setValue(PresentationAssessmentMode.ONLINE);
        component.editForm.controls.meetingLink.setValue('zoom.example.org/j/123');
        fixture.detectChanges();

        expect(query('[data-testid="presentation-instance-link-invalid"]')).not.toBeNull();
    });

    it('should explain result points above the maximum of the presentation', () => {
        expect(query('[data-testid="presentation-instance-points-exceed-max"]')).toBeNull();

        component.editForm.controls.resultPoints.setValue(21);
        fixture.detectChanges();

        expect(query('[data-testid="presentation-instance-points-exceed-max"]')).not.toBeNull();
    });

    it('should explain a remark that is too long', () => {
        component.editForm.controls.remark.setValue('a'.repeat(1001));
        fixture.detectChanges();

        expect(query('#presentation-instance-remark-error')).not.toBeNull();
    });

    it('should ask for a presenter once the form was touched without one', () => {
        component.assignedStudents.set([]);
        fixture.detectChanges();
        expect(query('[data-testid="presentation-instance-presenter-missing"]')).toBeNull();

        component.editForm.markAllAsTouched();
        fixture.detectChanges();

        expect(query('[data-testid="presentation-instance-presenter-missing"]')).not.toBeNull();
    });
});
