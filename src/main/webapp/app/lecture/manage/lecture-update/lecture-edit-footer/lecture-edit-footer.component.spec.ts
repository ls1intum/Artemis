import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockPipe } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import { TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { LectureEditFooterComponent } from 'app/lecture/manage/lecture-update/lecture-edit-footer/lecture-edit-footer.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';

describe('LectureEditFooterComponent', () => {
    let fixture: ComponentFixture<LectureEditFooterComponent>;
    let component: LectureEditFooterComponent;

    beforeEach(async () => {
        // The mock translation service returns each key as its text, so the rendered texts show which key a state uses.
        await TestBed.configureTestingModule({
            imports: [LectureEditFooterComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideComponent(LectureEditFooterComponent, {
                remove: { imports: [ArtemisDatePipe] },
                add: { imports: [MockPipe(ArtemisDatePipe, (date: dayjs.Dayjs) => date.format('HH:mm'))] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(LectureEditFooterComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function setInputs(
        inputs: Partial<Record<'hasChanges' | 'hasUnsavedContent' | 'isSaving' | 'isEditMode', boolean>> & {
            savedAt?: dayjs.Dayjs;
            changedSections?: string[];
            invalidReason?: string;
        },
    ): void {
        for (const [name, value] of Object.entries(inputs)) {
            fixture.componentRef.setInput(name, value);
        }
        fixture.detectChanges();
    }

    function element(testId: string): HTMLElement {
        return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
    }

    function text(testId: string): string {
        return element(testId).textContent?.trim() ?? '';
    }

    function saveTooltip(): string | readonly string[] {
        return fixture.debugElement.query(By.css('[data-testid="lecture-edit-save"]')).injector.get(TumAetUiTooltipDirective).content();
    }

    it('should offer Close and block saving while nothing changed', () => {
        setInputs({ hasChanges: false });
        const saveSpy = vi.fn();
        component.save.subscribe(saveSpy);

        expect(text('lecture-edit-leave')).toBe('entity.action.close');
        expect(text('lecture-edit-footer-status')).toBe('artemisApp.lecture.editFooter.noChanges');
        expect(element('lecture-edit-save').getAttribute('aria-disabled')).toBe('true');
        expect(saveTooltip()).toBe('artemisApp.lecture.editFooter.nothingToSave');

        element('lecture-edit-save').click();
        expect(saveSpy).not.toHaveBeenCalled();
    });

    it('should offer Cancel and name the content when only content could not be saved, which Save does not cover', () => {
        setInputs({ hasChanges: false, hasUnsavedContent: true, changedSections: ['artemisApp.lecture.sections.units'] });

        expect(text('lecture-edit-leave')).toBe('entity.action.cancel');
        expect(text('lecture-edit-footer-status')).toContain('artemisApp.lecture.sections.units');
        expect(element('lecture-edit-save').getAttribute('aria-disabled')).toBe('true');
    });

    it('should offer Cancel, name the changed sections and save once something changed', () => {
        setInputs({ hasChanges: true, changedSections: ['artemisApp.lecture.sections.title', 'artemisApp.lecture.sections.period'] });
        const saveSpy = vi.fn();
        component.save.subscribe(saveSpy);

        expect(text('lecture-edit-leave')).toBe('entity.action.cancel');
        const status = text('lecture-edit-footer-status');
        expect(status).toContain('artemisApp.lecture.editFooter.unsavedChanges');
        expect(status).toContain('artemisApp.lecture.sections.title,');
        expect(status).toContain('artemisApp.lecture.sections.period');
        expect(element('lecture-edit-save').getAttribute('aria-disabled')).toBe('false');
        expect(saveTooltip()).toBe('');

        element('lecture-edit-save').click();
        expect(saveSpy).toHaveBeenCalledOnce();
    });

    it('should explain why invalid changes cannot be saved', () => {
        setInputs({ hasChanges: true, invalidReason: 'artemisApp.lecture.editFooter.periodInvalid' });

        expect(element('lecture-edit-save').getAttribute('aria-disabled')).toBe('true');
        expect(saveTooltip()).toBe('artemisApp.lecture.editFooter.periodInvalid');
    });

    it('should block both buttons while saving', () => {
        setInputs({ hasChanges: true, isSaving: true });

        expect(text('lecture-edit-footer-status')).toBe('artemisApp.lecture.editFooter.saving');
        expect((element('lecture-edit-leave') as HTMLButtonElement).disabled).toBe(true);
        expect(element('lecture-edit-save').getAttribute('aria-disabled')).toBe('true');
        expect(saveTooltip()).toBe('');
    });

    it('should confirm when the details were saved until the next change', () => {
        setInputs({ hasChanges: false, savedAt: dayjs('2026-10-01T09:30') });

        expect(text('lecture-edit-footer-status')).toContain('artemisApp.lecture.editFooter.saved');

        setInputs({ hasChanges: true, changedSections: ['artemisApp.lecture.sections.title'] });

        expect(text('lecture-edit-footer-status')).not.toContain('artemisApp.lecture.editFooter.saved');
    });

    it('should say where content is saved, depending on whether the lecture exists', () => {
        setInputs({ isEditMode: false });
        expect(fixture.nativeElement.querySelector('.lecture-edit-footer-hint').textContent.trim()).toBe('artemisApp.lecture.editFooter.createHint');

        setInputs({ isEditMode: true });
        expect(fixture.nativeElement.querySelector('.lecture-edit-footer-hint').textContent.trim()).toBe('artemisApp.lecture.editFooter.contentHint');
    });

    it('should emit leave for Close and Cancel alike', () => {
        const leaveSpy = vi.fn();
        component.leave.subscribe(leaveSpy);
        setInputs({ hasChanges: false });

        element('lecture-edit-leave').click();
        setInputs({ hasChanges: true });
        element('lecture-edit-leave').click();

        expect(leaveSpy).toHaveBeenCalledTimes(2);
    });
});
