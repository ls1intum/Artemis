import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CdkTextareaAutosize } from '@angular/cdk/text-field';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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
    let surfaceWidth: number;
    let resizeObservers: Array<{ callback: ResizeObserverCallback; observe: ReturnType<typeof vi.fn>; disconnect: ReturnType<typeof vi.fn> }>;
    let mutationObservers: Array<{ callback: MutationCallback; observe: ReturnType<typeof vi.fn>; disconnect: ReturnType<typeof vi.fn> }>;
    const originalResizeObserver = globalThis.ResizeObserver;
    const originalMutationObserver = globalThis.MutationObserver;

    beforeEach(() => {
        surfaceWidth = 300;
        resizeObservers = [];
        mutationObservers = [];
        globalThis.ResizeObserver = class {
            readonly observe = vi.fn();
            readonly disconnect = vi.fn();
            readonly unobserve = vi.fn();

            constructor(readonly callback: ResizeObserverCallback) {
                resizeObservers.push({ callback, observe: this.observe, disconnect: this.disconnect });
            }
        } as unknown as typeof ResizeObserver;
        globalThis.MutationObserver = class {
            readonly observe = vi.fn();
            readonly disconnect = vi.fn();
            readonly takeRecords = vi.fn().mockReturnValue([]);

            constructor(callback: MutationCallback) {
                mutationObservers.push({ callback, observe: this.observe, disconnect: this.disconnect });
            }
        } as unknown as typeof MutationObserver;
        vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function (this: HTMLElement) {
            const width = this.classList.contains('modeling-explanation-surface__surface') ? surfaceWidth : 0;
            return { x: 0, y: 0, width, height: 0, top: 0, right: width, bottom: 0, left: 0, toJSON: () => ({}) };
        });

        TestBed.configureTestingModule({
            imports: [TestHostComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });
        fixture = TestBed.createComponent(TestHostComponent);
        fixture.detectChanges();
    });

    afterEach(() => {
        fixture.destroy();
        globalThis.ResizeObserver = originalResizeObserver;
        globalThis.MutationObserver = originalMutationObserver;
        vi.restoreAllMocks();
    });

    it('remeasures translated label content and clamps the notch to the surface', () => {
        const surfaceDebug = fixture.debugElement.query(By.directive(ModelingExplanationSurfaceComponent));
        const surface = surfaceDebug.query(By.css('.modeling-explanation-surface__surface')).nativeElement as HTMLElement;
        const notch = surfaceDebug.query(By.css('.modeling-explanation-surface__notch')).nativeElement as HTMLElement;
        const label = notch.querySelector('span')!;
        const notchObserver = mutationObservers.find((observer) => observer.observe.mock.calls.some(([target]) => target === notch))!;
        // jhiResizable observes the same element. The surface component registers its observer afterwards.
        const surfaceObserver = (surfaceDebug.componentInstance as unknown as { resizeObserver: (typeof resizeObservers)[number] }).resizeObserver;
        Object.defineProperty(label, 'scrollWidth', { configurable: true, value: 180 });

        notchObserver.callback([], {} as MutationObserver);
        fixture.detectChanges();
        expect(notch.style.width).toBe('180px');

        surfaceWidth = 120;
        surfaceObserver.callback([], {} as ResizeObserver);
        fixture.detectChanges();
        expect(notch.style.width).toBe('120px');
        expect(surfaceObserver.observe).toHaveBeenCalledExactlyOnceWith(surface);
        expect(notchObserver.observe).toHaveBeenCalledExactlyOnceWith(notch, { childList: true, characterData: true, subtree: true });
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

    it('disconnects both layout observers', () => {
        const notch = fixture.debugElement.query(By.css('.modeling-explanation-surface__notch')).nativeElement as HTMLElement;
        const surfaceObserver = (
            fixture.debugElement.query(By.directive(ModelingExplanationSurfaceComponent)).componentInstance as unknown as {
                resizeObserver: (typeof resizeObservers)[number];
            }
        ).resizeObserver;
        const notchObserver = mutationObservers.find((observer) => observer.observe.mock.calls.some(([target]) => target === notch))!;
        fixture.destroy();

        expect(surfaceObserver.disconnect).toHaveBeenCalledOnce();
        expect(notchObserver.disconnect).toHaveBeenCalledOnce();
    });
});
