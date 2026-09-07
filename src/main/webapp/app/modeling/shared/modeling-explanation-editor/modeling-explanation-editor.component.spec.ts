import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ModelingExplanationEditorComponent } from 'app/modeling/shared/modeling-explanation-editor/modeling-explanation-editor.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { CdkTextareaAutosize } from '@angular/cdk/text-field';

describe('ModelingExplanationEditorComponent', () => {
    let fixture: ComponentFixture<ModelingExplanationEditorComponent>;
    let comp: ModelingExplanationEditorComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });

        fixture = TestBed.createComponent(ModelingExplanationEditorComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should keep a compact, labeled explanation field visible', () => {
        fixture.detectChanges();

        const surface = fixture.debugElement.query(By.css('.modeling-explanation-surface__surface'));
        const label = fixture.debugElement.query(By.css('.modeling-explanation-surface__label span'));
        const textarea = fixture.debugElement.query(By.css('textarea'));
        expect(surface).not.toBeNull();
        expect(textarea).not.toBeNull();
        expect(textarea.nativeElement.getAttribute('aria-labelledby')).toBe(label.nativeElement.id);
        const autosize = fixture.debugElement.query(By.directive(CdkTextareaAutosize)).injector.get(CdkTextareaAutosize);
        expect(autosize.minRows).toBe(1);
        expect(autosize.maxRows).toBe(3);
    });

    it('switches to manual sizing after resize movement and resets on double-click', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        const surface = fixture.debugElement.query(By.css('.modeling-explanation-surface__surface'));
        const handles = surface.queryAll(By.css('.modeling-explanation-surface__resize-handle'));
        const textarea = fixture.debugElement.query(By.css('textarea')).nativeElement as HTMLTextAreaElement;
        expect(handles).toHaveLength(1);
        expect(handles[0].nativeElement.getAttribute('role')).toBe('separator');
        expect(handles[0].nativeElement.getAttribute('aria-orientation')).toBe('horizontal');

        surface.triggerEventHandler('resizeStart', { width: 400, height: 100 });
        fixture.detectChanges();
        expect(surface.nativeElement.classList).not.toContain('modeling-explanation-surface__surface--manually-sized');

        surface.triggerEventHandler('resizeMove', { width: 400, height: 100 });
        fixture.detectChanges();

        expect(surface.nativeElement.classList).toContain('modeling-explanation-surface__surface--manually-sized');
        expect(textarea.style.getPropertyValue('height')).toBe('100%');
        expect(textarea.style.getPropertyPriority('height')).toBe('important');
        expect(textarea.style.getPropertyValue('max-height')).toBe('none');

        surface.nativeElement.style.height = '100px';
        fixture.debugElement.query(By.css('.modeling-explanation-surface__resizer')).triggerEventHandler('dblclick');
        fixture.detectChanges();

        expect(surface.nativeElement.classList).not.toContain('modeling-explanation-surface__surface--manually-sized');
        expect(surface.nativeElement.style.height).toBe('');
    });

    it('should keep read-only explanations focusable and scrollable', () => {
        fixture.componentRef.setInput('readOnly', true);
        fixture.detectChanges();

        const textarea = fixture.debugElement.query(By.css('textarea')).nativeElement as HTMLTextAreaElement;
        expect(textarea.readOnly).toBe(true);
        expect(textarea.disabled).toBe(false);
    });

    it('should adapt the shared surface for assessment explanations', () => {
        fixture.componentRef.setInput('labelKey', 'artemisApp.exampleSubmission.assessmentExplanation');
        fixture.componentRef.setInput('placeholderKey', 'artemisApp.exampleSubmission.assessmentExplanationPlaceholder');
        fixture.componentRef.setInput('maxCharacterCount', 2000);
        fixture.componentRef.setInput('notchWidth', 208);
        fixture.detectChanges();

        const surface = fixture.debugElement.query(By.css('.modeling-explanation-surface__surface'));
        const textarea = fixture.debugElement.query(By.css('textarea')).nativeElement as HTMLTextAreaElement;
        expect(surface.nativeElement.style.getPropertyValue('--modeling-explanation-surface-label-min-width')).toBe('208px');
        expect(textarea.maxLength).toBe(2000);
    });

    it('should support a bounded content-aware initial height', () => {
        fixture.componentRef.setInput('autosizeMaxRows', 6);
        fixture.detectChanges();

        const autosize = fixture.debugElement.query(By.directive(CdkTextareaAutosize)).injector.get(CdkTextareaAutosize);
        expect(autosize.minRows).toBe(1);
        expect(autosize.maxRows).toBe(6);
    });

    it('updates the explanation bidirectionally', async () => {
        fixture.componentRef.setInput('explanation', 'Initial Explanation');
        fixture.detectChanges();
        await fixture.whenStable();

        const textareaDebugElement = fixture.debugElement.query(By.css('textarea'));
        expect(textareaDebugElement).not.toBeNull();
        const textarea = textareaDebugElement.nativeElement;
        expect(textarea.value).toBe('Initial Explanation');
        textarea.value = 'Test';
        textarea.dispatchEvent(new Event('input'));
        expect(comp.explanation()).toBe('Test');
        expect(textarea.value).toBe('Test');
    });

    it('does not trap Tab navigation in the prose field', () => {
        fixture.detectChanges();

        const textarea = fixture.debugElement.query(By.css('textarea')).nativeElement as HTMLTextAreaElement;
        const tabEvent = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true });
        textarea.dispatchEvent(tabEvent);
        expect(tabEvent.defaultPrevented).toBe(false);
    });
});
