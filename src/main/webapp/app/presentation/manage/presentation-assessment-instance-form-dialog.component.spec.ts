import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TumAetUiDatePickerComponent } from '@tumaet/ui-angular';
import { MockComponent } from 'ng-mocks';
import { Mock, beforeEach, describe, expect, it, vi } from 'vitest';
import dayjs from 'dayjs/esm';

import {
    PresentationAssessmentInstanceFormDialogComponent,
    PresentationAssessmentInstanceFormResult,
} from 'app/presentation/manage/presentation-assessment-instance-form-dialog.component';
import { TranslateService } from '@ngx-translate/core';
import { User } from 'app/account/user/user.model';
import { PresentationAssessmentMode } from 'app/presentation/shared/entities/presentation-assessment.model';
import { PresentationAssessmentPresenterSelectorComponent } from 'app/presentation/manage/presentation-assessment-presenter-selector.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('PresentationAssessmentInstanceFormDialogComponent', () => {
    let fixture: ComponentFixture<PresentationAssessmentInstanceFormDialogComponent>;
    let component: PresentationAssessmentInstanceFormDialogComponent;
    let saved: Mock<(value: PresentationAssessmentInstanceFormResult) => void>;
    let translateService: MockTranslateService;

    const presentationDate = dayjs('2026-07-31T13:26:00');

    beforeEach(async () => {
        saved = vi.fn();
        translateService = new MockTranslateService();

        await TestBed.configureTestingModule({
            imports: [PresentationAssessmentInstanceFormDialogComponent],
            providers: [{ provide: TranslateService, useValue: translateService }],
        })
            // The presenter selector searches students on the server and is tested on its own.
            .overrideComponent(PresentationAssessmentInstanceFormDialogComponent, {
                remove: { imports: [PresentationAssessmentPresenterSelectorComponent] },
                add: { imports: [MockComponent(PresentationAssessmentPresenterSelectorComponent)] },
            })
            .compileComponents();

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
            }),
        });
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
