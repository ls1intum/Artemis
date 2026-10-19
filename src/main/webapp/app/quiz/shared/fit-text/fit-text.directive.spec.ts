import { inputBinding } from '@angular/core';
import { DirectiveFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { FitTextDirective } from './fit-text.directive';

/** Applies the directive to a bare <div> that TestBed creates, binding each given input to a constant value. */
function createFitText(inputs: Record<string, unknown> = {}): DirectiveFixture<FitTextDirective> {
    return TestBed.createDirective(FitTextDirective, {
        tagName: 'div',
        bindings: Object.entries(inputs).map(([name, value]) => inputBinding(name, () => value)),
    });
}

describe('FitTextDirective', () => {
    let fixture: DirectiveFixture<FitTextDirective>;

    beforeEach(() => {
        fixture = createFitText();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create an instance', () => {
        expect(fixture.directiveInstance).toBeInstanceOf(FitTextDirective);
    });

    it('should have the fittext directive element', () => {
        fixture.detectChanges();
        expect(fixture.nativeElement.tagName).toBe('DIV');
        expect(fixture.debugElement.injector.get(FitTextDirective)).toBe(fixture.directiveInstance);
    });

    it('should clear timeout on destroy', () => {
        vi.useFakeTimers();
        const clearTimeoutSpy = vi.spyOn(globalThis, 'clearTimeout');

        fixture.detectChanges();
        fixture.destroy();

        expect(clearTimeoutSpy).toHaveBeenCalled();

        vi.useRealTimers();
    });

    it('should initialize with default values', () => {
        fixture.detectChanges();
        const directive = fixture.directiveInstance;
        expect(directive.fitText()).toBe(true);
        expect(directive.compression()).toBe(1);
        expect(directive.activateOnResize()).toBe(true);
        expect(directive.minFontSize()).toBe(0);
        expect(directive.maxFontSize()).toBe(Number.POSITIVE_INFINITY);
        expect(directive.delay()).toBe(100);
        expect(directive.fontUnit()).toBe('px');
    });

    it('should support activateOnResize input', () => {
        // Test that directive can be instantiated with activateOnResize
        expect(fixture.directiveInstance.activateOnResize()).toBe(true);
    });
});

describe('FitTextDirective - Input configurations', () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    it.each([
        ['minFontSize', 20],
        ['maxFontSize', 10],
        ['fontUnit', 'em'],
        ['delay', 500],
        ['compression', 0.5],
        ['minFontSize', 'inherit'],
        ['maxFontSize', 'inherit'],
        ['activateOnResize', true],
        ['activateOnResize', false],
    ] as const)('should accept %s input %p', (name, value) => {
        const fixture = createFitText({ [name]: value });
        fixture.detectChanges();
        expect(fixture.directiveInstance[name]()).toBe(value);
    });
});

describe('FitTextDirective - Resize Behavior', () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should handle window resize when activateOnResize is true', () => {
        vi.useFakeTimers();

        const fixture = createFitText({ activateOnResize: true });
        fixture.detectChanges();
        const setFontSizeSpy = vi.spyOn(fixture.directiveInstance as any, 'setFontSize');

        // Trigger resize event
        window.dispatchEvent(new Event('resize'));
        vi.advanceTimersByTime(150);

        expect(setFontSizeSpy).toHaveBeenCalledOnce();

        vi.useRealTimers();
    });

    it('should not call setFontSize on resize when activateOnResize is false', () => {
        vi.useFakeTimers();

        const fixture = createFitText({ activateOnResize: false });
        fixture.detectChanges();
        const setFontSizeSpy = vi.spyOn(fixture.directiveInstance as any, 'setFontSize');

        // Trigger resize event - should not affect font size
        window.dispatchEvent(new Event('resize'));
        vi.advanceTimersByTime(150);

        expect(setFontSizeSpy).not.toHaveBeenCalled();

        vi.useRealTimers();
    });
});

describe('FitTextDirective - Font Size Calculation', () => {
    afterEach(() => {
        document.body.style.removeProperty('padding');
        vi.restoreAllMocks();
    });

    /**
     * jsdom does no layout, so every offset size is 0 and the directive skips the calculation. Report a host that is
     * 10px wide at the directive's 10px probe font size, inside a parent of the given width (the <body> TestBed attaches
     * the host to). The uncapped font size is then the parent width minus the directive's 6px allowance: 200 for 206.
     */
    function stubLayout(host: Element, parentWidth: number): void {
        document.body.style.padding = '0px';
        vi.spyOn(HTMLElement.prototype, 'offsetWidth', 'get').mockImplementation(function (this: HTMLElement) {
            return this === host ? 10 : this === document.body ? parentWidth : 0;
        });
        vi.spyOn(HTMLElement.prototype, 'offsetHeight', 'get').mockImplementation(function (this: HTMLElement) {
            return this === host ? 10 : 0;
        });
    }

    it.each([
        ['set font size after view init with delay', { delay: 200 }, 250, 206, '200px'],
        ['respect minFontSize constraint', { minFontSize: 12 }, 150, 16, '12px'],
        ['respect maxFontSize constraint', { maxFontSize: 24 }, 150, 206, '24px'],
        ['use compression factor in calculation', { compression: 0.5 }, 150, 206, '100px'],
        ['use em font unit', { fontUnit: 'em' }, 150, 206, '200em'],
    ])('should %s', (_, inputs, elapsedMs, parentWidth, expectedFontSize) => {
        vi.useFakeTimers();

        const fixture = createFitText(inputs);
        stubLayout(fixture.nativeElement, parentWidth);
        fixture.detectChanges();

        // Allow the delayed font size calculation
        vi.advanceTimersByTime(elapsedMs);

        expect((fixture.nativeElement as HTMLElement).style.fontSize).toBe(expectedFontSize);

        vi.useRealTimers();
    });
});

describe('FitTextDirective - innerHTML Changes', () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should set initial innerHTML content', () => {
        vi.useFakeTimers();

        const fixture = createFitText({ innerHTML: 'initial content' });
        fixture.detectChanges();
        vi.advanceTimersByTime(150);

        expect(fixture.nativeElement.innerHTML).toBe('initial content');

        vi.useRealTimers();
    });

    it('should handle HTML content in innerHTML', () => {
        vi.useFakeTimers();

        const fixture = createFitText({ innerHTML: '<strong>bold text</strong>' });
        fixture.detectChanges();
        vi.advanceTimersByTime(150);

        expect(fixture.nativeElement.querySelector('strong')).toBeTruthy();

        vi.useRealTimers();
    });
});

describe('FitTextDirective - Lifecycle', () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should properly clean up on destroy', () => {
        vi.useFakeTimers();
        const clearTimeoutSpy = vi.spyOn(globalThis, 'clearTimeout');

        const fixture = createFitText();
        fixture.detectChanges();

        // Advance time to trigger timeout
        vi.advanceTimersByTime(150);

        // Destroy the directive
        fixture.destroy();

        expect(clearTimeoutSpy).toHaveBeenCalled();

        vi.useRealTimers();
    });

    it('should handle element with child elements', () => {
        vi.useFakeTimers();

        const fixture = createFitText();
        // Content added before the first change detection is in place for ngOnInit and every later hook.
        for (const line of ['Line 1', 'Line 2', 'Line 3']) {
            const paragraph = document.createElement('p');
            paragraph.textContent = line;
            fixture.nativeElement.appendChild(paragraph);
        }
        fixture.detectChanges();
        vi.advanceTimersByTime(150);

        // Without a bound innerHTML input the directive leaves the host content alone.
        expect(fixture.nativeElement.childElementCount).toBe(3);

        vi.useRealTimers();
    });

    it('should not crash when fitText is false', () => {
        const fixture = createFitText({ fitText: false });
        fixture.detectChanges();

        expect(fixture.directiveInstance.fitText()).toBe(false);
    });
});
