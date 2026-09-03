import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CdkTextareaAutosize } from '@angular/cdk/text-field';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ModelingExplanationSurfaceComponent } from './modeling-explanation-surface.component';

@Component({
    template: `
        <jhi-modeling-explanation-surface labelId="explanation-label" [notchWidth]="40">
            <textarea #resizableContent cdkTextareaAutosize aria-labelledby="explanation-label"></textarea>
        </jhi-modeling-explanation-surface>
    `,
    imports: [ModelingExplanationSurfaceComponent, CdkTextareaAutosize],
})
class TestHostComponent {}

describe('ModelingExplanationSurfaceComponent', () => {
    let fixture: ComponentFixture<TestHostComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [TestHostComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });
        fixture = TestBed.createComponent(TestHostComponent);
        fixture.detectChanges();
    });

    it('connects the projected input to a width-bounded label', () => {
        const surface = fixture.debugElement.query(By.css('.modeling-explanation-surface__surface')).nativeElement as HTMLElement;
        const label = fixture.debugElement.query(By.css('.modeling-explanation-surface__label')).nativeElement as HTMLElement;
        const textarea = fixture.debugElement.query(By.css('textarea')).nativeElement as HTMLTextAreaElement;

        expect(surface.style.getPropertyValue('--modeling-explanation-surface-label-min-width')).toBe('40px');
        expect(label.querySelector('span')?.id).toBe('explanation-label');
        expect(textarea.getAttribute('aria-labelledby')).toBe('explanation-label');
    });

    it('restores autosizing after a manual resize', () => {
        const surfaceDebug = fixture.debugElement.query(By.directive(ModelingExplanationSurfaceComponent));
        const surface = surfaceDebug.query(By.css('.modeling-explanation-surface__surface')).nativeElement as HTMLElement;
        const resizableSurface = surfaceDebug.query(By.css('.modeling-explanation-surface__surface'));
        const textareaDebug = fixture.debugElement.query(By.directive(CdkTextareaAutosize));
        const textarea = textareaDebug.nativeElement as HTMLTextAreaElement;
        const autosize = textareaDebug.injector.get(CdkTextareaAutosize);
        const autosizeSpy = vi.spyOn(autosize, 'resizeToFitContent');

        resizableSurface.triggerEventHandler('resizeMove', { width: 300, height: 100 });
        fixture.detectChanges();
        expect(surface.classList).toContain('modeling-explanation-surface__surface--manually-sized');
        expect(textarea.style.getPropertyPriority('height')).toBe('important');

        surface.style.height = '100px';
        autosizeSpy.mockClear();
        surfaceDebug.query(By.css('.modeling-explanation-surface__resizer')).triggerEventHandler('dblclick');
        fixture.detectChanges();
        expect(surface.style.height).toBe('');
        expect(textarea.style.height).toBe('');
        expect(textarea.style.maxHeight).toBe('');
        expect(autosizeSpy).toHaveBeenCalledWith(true);
    });
});
