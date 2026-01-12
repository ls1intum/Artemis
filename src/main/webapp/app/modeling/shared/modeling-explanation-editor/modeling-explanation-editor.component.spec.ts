import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ModelingExplanationEditorComponent } from 'app/modeling/shared/modeling-explanation-editor/modeling-explanation-editor.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('ModelingExplanationEditorComponent', () => {
    setupTestBed({ zoneless: true });

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

    it('should initialize', () => {
        fixture.detectChanges();
        expect(ModelingExplanationEditorComponent).not.toBeNull();
    });

    it('should change explanation value bidirectionally between component and template', async () => {
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

        // Test tab event
        textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab' }));
        textarea.dispatchEvent(new Event('input'));
        fixture.detectChanges();
        expect(textarea.value).toBe('Test\t');
        expect(comp.explanation()).toBe('Test\t');
    });
});
