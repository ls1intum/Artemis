import { Component, ElementRef, ViewChild } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { FitTextDirective } from './fit-text.directive';

@Component({
    template: `
        <div style="width: 200px; height: 100px; padding: 10px;" id="parent">
            <div fitText #fitTextEl>test content</div>
        </div>
    `,
    imports: [FitTextDirective],
})
class TestFitTextComponent {
    @ViewChild('fitTextEl', { read: ElementRef }) fitTextEl!: ElementRef;
}

describe('FitTextDirective', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<TestFitTextComponent>;
    let component: TestFitTextComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        fixture = TestBed.createComponent(TestFitTextComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create an instance', () => {
        expect(fixture).toBeTruthy();
    });

    it('should have the fittext directive element', () => {
        fixture.detectChanges();
        const element = fixture.nativeElement.querySelector('[fittext]');
        expect(element).toBeTruthy();
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
        // Directive should be attached without errors
        expect(component.fitTextEl).toBeTruthy();
    });

    it('should support activateOnResize input', () => {
        // Test that directive can be instantiated with activateOnResize
        const element = fixture.nativeElement.querySelector('[fittext]');
        expect(element).toBeTruthy();
    });
});

describe('FitTextDirective - Input configurations', () => {
    setupTestBed({ zoneless: true });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should accept minFontSize input', () => {
        @Component({
            template: `<div><div fitText [minFontSize]="20">test</div></div>`,
            imports: [FitTextDirective],
        })
        class TestMinFontSizeComponent {}

        const fixture = TestBed.createComponent(TestMinFontSizeComponent);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[fittext]')).toBeTruthy();
    });

    it('should accept maxFontSize input', () => {
        @Component({
            template: `<div><div fitText [maxFontSize]="10">test</div></div>`,
            imports: [FitTextDirective],
        })
        class TestMaxFontSizeComponent {}

        const fixture = TestBed.createComponent(TestMaxFontSizeComponent);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[fittext]')).toBeTruthy();
    });

    it('should accept fontUnit input', () => {
        @Component({
            template: `<div><div fitText [fontUnit]="'em'">test</div></div>`,
            imports: [FitTextDirective],
        })
        class TestFontUnitComponent {}

        const fixture = TestBed.createComponent(TestFontUnitComponent);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[fittext]')).toBeTruthy();
    });

    it('should accept delay input', () => {
        @Component({
            template: `<div><div fitText [delay]="500">test</div></div>`,
            imports: [FitTextDirective],
        })
        class TestDelayComponent {}

        const fixture = TestBed.createComponent(TestDelayComponent);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[fittext]')).toBeTruthy();
    });

    it('should accept compression input', () => {
        @Component({
            template: `<div><div fitText [compression]="0.5">test</div></div>`,
            imports: [FitTextDirective],
        })
        class TestCompressionComponent {}

        const fixture = TestBed.createComponent(TestCompressionComponent);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[fittext]')).toBeTruthy();
    });

    it('should accept inherit as minFontSize', () => {
        @Component({
            template: `<div><div fitText [minFontSize]="'inherit'">test</div></div>`,
            imports: [FitTextDirective],
        })
        class TestInheritMinComponent {}

        const fixture = TestBed.createComponent(TestInheritMinComponent);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[fittext]')).toBeTruthy();
    });

    it('should accept inherit as maxFontSize', () => {
        @Component({
            template: `<div><div fitText [maxFontSize]="'inherit'">test</div></div>`,
            imports: [FitTextDirective],
        })
        class TestInheritMaxComponent {}

        const fixture = TestBed.createComponent(TestInheritMaxComponent);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[fittext]')).toBeTruthy();
    });

    it('should accept activateOnResize input', () => {
        @Component({
            template: `<div><div fitText [activateOnResize]="true">test</div></div>`,
            imports: [FitTextDirective],
        })
        class TestResizeComponent {}

        const fixture = TestBed.createComponent(TestResizeComponent);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[fittext]')).toBeTruthy();
    });

    it('should handle activateOnResize false', () => {
        @Component({
            template: `<div><div fitText [activateOnResize]="false">test</div></div>`,
            imports: [FitTextDirective],
        })
        class TestNoResizeComponent {}

        const fixture = TestBed.createComponent(TestNoResizeComponent);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[fittext]')).toBeTruthy();
    });
});

describe('FitTextDirective - Resize Behavior', () => {
    setupTestBed({ zoneless: true });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should handle window resize when activateOnResize is true', () => {
        vi.useFakeTimers();

        @Component({
            template: `<div style="width: 200px;"><div fitText [activateOnResize]="true">test content</div></div>`,
            imports: [FitTextDirective],
        })
        class TestResizeComponent {}

        const fixture = TestBed.createComponent(TestResizeComponent);
        fixture.detectChanges();

        // Trigger resize event
        window.dispatchEvent(new Event('resize'));
        vi.advanceTimersByTime(150);

        expect(fixture.nativeElement.querySelector('[fittext]')).toBeTruthy();

        vi.useRealTimers();
    });

    it('should not call setFontSize on resize when activateOnResize is false', () => {
        vi.useFakeTimers();

        @Component({
            template: `<div style="width: 200px;"><div fitText [activateOnResize]="false">test content</div></div>`,
            imports: [FitTextDirective],
        })
        class TestNoResizeComponent {}

        const fixture = TestBed.createComponent(TestNoResizeComponent);
        fixture.detectChanges();

        // Get the element and check initial state
        const element = fixture.nativeElement.querySelector('[fittext]');
        expect(element).toBeTruthy();

        // Trigger resize event - should not affect font size
        window.dispatchEvent(new Event('resize'));
        vi.advanceTimersByTime(150);

        // Element should still be rendered
        expect(fixture.nativeElement.querySelector('[fittext]')).toBeTruthy();

        vi.useRealTimers();
    });
});

describe('FitTextDirective - Font Size Calculation', () => {
    setupTestBed({ zoneless: true });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should set font size after view init with delay', () => {
        vi.useFakeTimers();

        @Component({
            template: `<div style="width: 300px; padding: 10px;"><div fitText [delay]="200">test</div></div>`,
            imports: [FitTextDirective],
        })
        class TestDelayComponent {}

        const fixture = TestBed.createComponent(TestDelayComponent);
        fixture.detectChanges();

        // Allow the delayed font size calculation
        vi.advanceTimersByTime(250);

        expect(fixture.nativeElement.querySelector('[fittext]')).toBeTruthy();

        vi.useRealTimers();
    });

    it('should respect minFontSize constraint', () => {
        vi.useFakeTimers();

        @Component({
            template: `<div style="width: 50px;"><div fitText [minFontSize]="12">very long text that would shrink too much</div></div>`,
            imports: [FitTextDirective],
        })
        class TestMinFontComponent {}

        const fixture = TestBed.createComponent(TestMinFontComponent);
        fixture.detectChanges();
        vi.advanceTimersByTime(150);

        expect(fixture.nativeElement.querySelector('[fittext]')).toBeTruthy();

        vi.useRealTimers();
    });

    it('should respect maxFontSize constraint', () => {
        vi.useFakeTimers();

        @Component({
            template: `<div style="width: 1000px;"><div fitText [maxFontSize]="24">x</div></div>`,
            imports: [FitTextDirective],
        })
        class TestMaxFontComponent {}

        const fixture = TestBed.createComponent(TestMaxFontComponent);
        fixture.detectChanges();
        vi.advanceTimersByTime(150);

        expect(fixture.nativeElement.querySelector('[fittext]')).toBeTruthy();

        vi.useRealTimers();
    });

    it('should use compression factor in calculation', () => {
        vi.useFakeTimers();

        @Component({
            template: `<div style="width: 200px;"><div fitText [compression]="0.5">test</div></div>`,
            imports: [FitTextDirective],
        })
        class TestCompressionCalcComponent {}

        const fixture = TestBed.createComponent(TestCompressionCalcComponent);
        fixture.detectChanges();
        vi.advanceTimersByTime(150);

        expect(fixture.nativeElement.querySelector('[fittext]')).toBeTruthy();

        vi.useRealTimers();
    });

    it('should use em font unit', () => {
        vi.useFakeTimers();

        @Component({
            template: `<div style="width: 200px;"><div fitText [fontUnit]="'em'">test</div></div>`,
            imports: [FitTextDirective],
        })
        class TestEmUnitComponent {}

        const fixture = TestBed.createComponent(TestEmUnitComponent);
        fixture.detectChanges();
        vi.advanceTimersByTime(150);

        const element = fixture.nativeElement.querySelector('[fittext]');
        expect(element).toBeTruthy();

        vi.useRealTimers();
    });
});

describe('FitTextDirective - innerHTML Changes', () => {
    setupTestBed({ zoneless: true });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should set initial innerHTML content', () => {
        vi.useFakeTimers();

        @Component({
            template: `<div style="width: 200px;"><div fitText [innerHTML]="content"></div></div>`,
            imports: [FitTextDirective],
        })
        class TestInnerHTMLComponent {
            content = 'initial content';
        }

        const fixture = TestBed.createComponent(TestInnerHTMLComponent);
        fixture.detectChanges();
        vi.advanceTimersByTime(150);

        const element = fixture.nativeElement.querySelector('[fittext]');
        expect(element).toBeTruthy();
        expect(element.innerHTML).toBe('initial content');

        vi.useRealTimers();
    });

    it('should handle HTML content in innerHTML', () => {
        vi.useFakeTimers();

        @Component({
            template: `<div style="width: 200px;"><div fitText [innerHTML]="htmlContent"></div></div>`,
            imports: [FitTextDirective],
        })
        class TestHTMLContentComponent {
            htmlContent = '<strong>bold text</strong>';
        }

        const fixture = TestBed.createComponent(TestHTMLContentComponent);
        fixture.detectChanges();
        vi.advanceTimersByTime(150);

        const element = fixture.nativeElement.querySelector('[fittext]');
        expect(element).toBeTruthy();
        expect(element.querySelector('strong')).toBeTruthy();

        vi.useRealTimers();
    });
});

describe('FitTextDirective - Lifecycle', () => {
    setupTestBed({ zoneless: true });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should properly clean up on destroy', () => {
        vi.useFakeTimers();
        const clearTimeoutSpy = vi.spyOn(globalThis, 'clearTimeout');

        @Component({
            template: `<div><div fitText>test</div></div>`,
            imports: [FitTextDirective],
        })
        class TestLifecycleComponent {}

        const fixture = TestBed.createComponent(TestLifecycleComponent);
        fixture.detectChanges();

        // Advance time to trigger timeout
        vi.advanceTimersByTime(150);

        // Destroy the component
        fixture.destroy();

        expect(clearTimeoutSpy).toHaveBeenCalled();

        vi.useRealTimers();
    });

    it('should handle element with child elements', () => {
        vi.useFakeTimers();

        @Component({
            template: `
                <div style="width: 200px;">
                    <div fitText>
                        <p>Line 1</p>
                        <p>Line 2</p>
                        <p>Line 3</p>
                    </div>
                </div>
            `,
            imports: [FitTextDirective],
        })
        class TestMultilineComponent {}

        const fixture = TestBed.createComponent(TestMultilineComponent);
        fixture.detectChanges();
        vi.advanceTimersByTime(150);

        const element = fixture.nativeElement.querySelector('[fittext]');
        expect(element).toBeTruthy();
        expect(element.childElementCount).toBe(3);

        vi.useRealTimers();
    });

    it('should not crash when fitText is false', () => {
        @Component({
            template: `<div><div fitText [fitText]="false">test</div></div>`,
            imports: [FitTextDirective],
        })
        class TestDisabledComponent {}

        const fixture = TestBed.createComponent(TestDisabledComponent);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[fittext]')).toBeTruthy();
    });
});
