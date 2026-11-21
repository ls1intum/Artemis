import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

import { SecurityTestBehavior } from 'app/programming/shared/entities/security-policy.model';
import { SecurityTestBehaviourComponent } from 'app/programming/manage/update/update-components/security/policies/security-test-behaviour.component';

type Handlers = {
    onTabChange(value: string): void;
    setReplaceExceptions(enabled: boolean): void;
    setFailureMessage(message: string): void;
    setHideTests(hidden: boolean): void;
    onHiddenDateChange(field: 'hiddenFrom' | 'hiddenUntil', event: Event): void;
    setStrictTimeout(enabled: boolean): void;
    setTimeoutField(field: 'timeout' | 'terminationGrace', value: string | number | null): void;
    setTimeoutUnit(field: 'timeUnit' | 'terminationGraceUnit', unit: unknown): void;
    setMirrorOutput(mirrored: boolean): void;
    setMaximumCharacters(value: string | number | null): void;
};

/** Every label of the element has text and names a control of the element, so clicking it focuses the control and screen readers announce it. */
function expectLabelledControls(element: HTMLElement): void {
    for (const label of Array.from(element.querySelectorAll<HTMLLabelElement>('label[for]'))) {
        expect(label.textContent?.trim()).not.toBe('');
        expect(element.querySelector(`[id="${label.htmlFor}"]`)).not.toBeNull();
    }
}

describe('SecurityTestBehaviourComponent', () => {
    let fixture: ComponentFixture<SecurityTestBehaviourComponent>;
    let comp: SecurityTestBehaviourComponent;
    let handlers: Handlers;
    let emitted: (SecurityTestBehavior | undefined)[];

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] });
        fixture = TestBed.createComponent(SecurityTestBehaviourComponent);
        comp = fixture.componentInstance;
        handlers = comp as unknown as Handlers;
        emitted = [];
        comp.behaviorChange.subscribe((behavior) => {
            emitted.push(behavior);
            fixture.componentRef.setInput('behavior', behavior);
        });
        fixture.detectChanges();
    });

    it('renders every tab', () => {
        for (const tab of ['securityFeedback', 'standardFeedback', 'visibility', 'timeout', 'output']) {
            handlers.onTabChange(tab);
            fixture.detectChanges();
            expect(comp.activeTab()).toBe(tab);
            expectLabelledControls(fixture.nativeElement);
        }
        expect(fixture.nativeElement.querySelector('[data-testid="security-behaviour-mirror"]')).not.toBeNull();
    });

    it('previews the exception or the replacing message exactly, without the indentation of the template', () => {
        handlers.onTabChange('standardFeedback');
        fixture.detectChanges();
        const preview = (): string | null => fixture.nativeElement.querySelector('[data-testid="security-behaviour-standard-preview"]').textContent;

        expect(preview()).toBe('artemisApp.programmingExercise.security.advanced.behaviour.originalException');

        handlers.setReplaceExceptions(true);
        handlers.setFailureMessage('  Test failed.  ');
        fixture.detectChanges();
        expect(preview()).toBe('Test failed.');
    });

    it('replaces exceptions with a message and drops the section when switched off again', () => {
        handlers.setReplaceExceptions(true);
        handlers.setFailureMessage('Test failed.');
        expect(emitted.at(-1)).toEqual({ privilegedExceptions: { enabled: true, failureMessage: 'Test failed.' } });

        handlers.setReplaceExceptions(false);
        expect(emitted.at(-1)).toBeUndefined();
    });

    it('restores a section the committed policy switches off, so switching on and off is no change', () => {
        const base: SecurityTestBehavior = { outputMirroring: { mirrored: false } };
        fixture.componentRef.setInput('baseBehavior', base);
        fixture.componentRef.setInput('behavior', base);

        handlers.setMirrorOutput(true);
        expect(emitted.at(-1)).toEqual({ outputMirroring: { mirrored: true, maximumCharacters: 100_000_000 } });
        expect(comp.isUncommitted('output')).toBe(true);
        handlers.setMaximumCharacters('1_000');
        expect(emitted.at(-1)?.outputMirroring?.maximumCharacters).toBe(1000);
        handlers.setMaximumCharacters('-5');
        handlers.setMaximumCharacters(1.5);
        handlers.setMaximumCharacters(null);
        expect(emitted.at(-1)?.outputMirroring?.maximumCharacters).toBe(1000);
        expect(comp.uncommittedTabs().has('output')).toBe(true);

        handlers.setMirrorOutput(false);
        expect(emitted.at(-1)).toEqual(base);
        expect(comp.isUncommitted('output')).toBe(false);
    });

    it('edits a strict timeout and ignores invalid values', () => {
        handlers.setStrictTimeout(true);
        expect(emitted.at(-1)?.strictTimeout).toEqual({ timeout: 5, timeUnit: 'SECONDS', terminationGrace: 1, terminationGraceUnit: 'SECONDS' });

        handlers.setTimeoutField('timeout', '0');
        handlers.setTimeoutField('timeout', 3);
        handlers.setTimeoutField('terminationGrace', '');
        handlers.setTimeoutUnit('timeUnit', 'MINUTES');

        expect(emitted.at(-1)?.strictTimeout).toEqual({ timeout: 3, timeUnit: 'MINUTES', terminationGraceUnit: 'SECONDS' });
        handlers.setStrictTimeout(false);
        expect(emitted.at(-1)).toBeUndefined();
    });

    it('refuses numbers that are no exact whole number of the server', () => {
        handlers.setStrictTimeout(true);
        handlers.setMirrorOutput(true);
        const before = emitted.at(-1);

        // 9007199254740993 reads as 9007199254740992 and 1e20 does not fit a long, so neither changes the stored values.
        handlers.setTimeoutField('timeout', '9007199254740993');
        handlers.setTimeoutField('terminationGrace', 1e20);
        handlers.setMaximumCharacters('100000000000000000000');

        expect(emitted.at(-1)).toEqual(before);
        handlers.setTimeoutField('terminationGrace', 0);
        expect(emitted.at(-1)?.strictTimeout?.terminationGrace).toBe(0);
    });

    it('hides tests for a period and flags a period that ends before it starts', () => {
        handlers.setHideTests(true);
        expect(emitted.at(-1)?.hiddenTests?.hiddenFrom).toBeDefined();
        expect(emitted.at(-1)?.hiddenTests?.hiddenUntil).toBeDefined();
        expect(comp.hiddenPeriodInvalid()).toBe(false);

        handlers.onHiddenDateChange('hiddenFrom', { target: { value: '2026-12-24T10:00' } } as unknown as Event);
        handlers.onHiddenDateChange('hiddenUntil', { target: { value: '2026-12-24T09:00' } } as unknown as Event);

        expect(comp.hiddenFromInput()).toBe('2026-12-24T10:00');
        expect(comp.hiddenPeriodInvalid()).toBe(true);
        // Both dates are marked invalid and name the message, so a screen reader reads it when one of them gets the focus again.
        handlers.onTabChange('visibility');
        fixture.detectChanges();
        const element = fixture.nativeElement as HTMLElement;
        const problemId = element.querySelector('[data-testid="security-behaviour-hidden-period-problem"]')?.id;
        expect(problemId).toBeTruthy();
        for (const testId of ['security-behaviour-hidden-from', 'security-behaviour-hidden-until']) {
            const input = element.querySelector(`[data-testid="${testId}"]`);
            expect(input?.getAttribute('aria-invalid')).toBe('true');
            expect(input?.getAttribute('aria-describedby')).toBe(problemId);
        }
        handlers.setHideTests(false);
        expect(emitted.at(-1)).toBeUndefined();
    });

    it('shows the stored end of the hidden period again when the field is cleared', () => {
        handlers.setHideTests(true);
        handlers.onHiddenDateChange('hiddenUntil', { target: { value: '2026-12-24T09:00' } } as unknown as Event);
        const count = emitted.length;
        const input = { value: '' };

        handlers.onHiddenDateChange('hiddenUntil', { target: input } as unknown as Event);

        expect(emitted).toHaveLength(count);
        expect(input.value).toBe('2026-12-24T09:00');
        expect(comp.hiddenUntilInput()).toBe('2026-12-24T09:00');
    });

    it('removes the grace period when its field is cleared instead of storing 0', () => {
        handlers.setStrictTimeout(true);

        handlers.setTimeoutField('terminationGrace', null);

        expect(emitted.at(-1)?.strictTimeout).toEqual({ timeout: 5, timeUnit: 'SECONDS', terminationGraceUnit: 'SECONDS' });
        handlers.setTimeoutField('terminationGrace', 2);
        expect(emitted.at(-1)?.strictTimeout?.terminationGrace).toBe(2);
    });

    it('shows the stored time limit and maximum again when a refused value leaves the field', () => {
        handlers.onTabChange('timeout');
        handlers.setStrictTimeout(true);
        fixture.detectChanges();
        const timeLimit: HTMLInputElement = fixture.nativeElement.querySelector('[data-testid="security-behaviour-time-limit"]');
        timeLimit.value = '0';
        timeLimit.dispatchEvent(new Event('input'));
        timeLimit.dispatchEvent(new Event('blur'));

        expect(emitted.at(-1)?.strictTimeout?.timeout).toBe(5);
        expect(timeLimit.value).toBe('5');

        handlers.onTabChange('output');
        handlers.setMirrorOutput(true);
        fixture.detectChanges();
        const maximum: HTMLInputElement = fixture.nativeElement.querySelector('[data-testid="security-behaviour-max-chars"]');
        maximum.value = '1.5';
        maximum.dispatchEvent(new Event('input'));
        maximum.dispatchEvent(new Event('blur'));

        expect(maximum.value).toBe(String(comp.shownMaximumCharacters()));
    });
});
