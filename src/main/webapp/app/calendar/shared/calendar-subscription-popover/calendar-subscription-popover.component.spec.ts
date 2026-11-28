import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { CalendarSubscriptionPopoverComponent } from 'app/calendar/shared/calendar-subscription-popover/calendar-subscription-popover.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
const getParametersOf = (urlStr: string) => new URL(urlStr, 'http://dummy.invalid').searchParams;
const splitFilterParameters = (parameters: URLSearchParams) => (parameters.get('filterOptions') ?? '').split(',').filter(Boolean).sort();

describe('CalendarSubscriptionPopoverComponent (signal-based tests)', () => {
    let fixture: ComponentFixture<CalendarSubscriptionPopoverComponent>;
    let component: CalendarSubscriptionPopoverComponent;

    const clipboardBefore = Object.getOwnPropertyDescriptor(navigator, 'clipboard');

    afterEach(() => {
        vi.restoreAllMocks();
        // a test that replaced the clipboard must not leave its stub to the next one
        if (clipboardBefore) {
            Object.defineProperty(navigator, 'clipboard', clipboardBefore);
        } else {
            Reflect.deleteProperty(navigator, 'clipboard');
        }
    });

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CalendarSubscriptionPopoverComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarSubscriptionPopoverComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('courseId', 42);
        fixture.componentRef.setInput('subscriptionToken', 'testToken');
        fixture.componentRef.setInput('isMobile', false);

        fixture.detectChanges();
    });

    it('builds an initial URL with all event types and ENGLISH', () => {
        const url = component.subscriptionUrl();
        const parameters = getParametersOf(url);

        expect(parameters.get('token')).toBe('testToken');
        expect(parameters.get('language')).toBe('ENGLISH');
        expect(splitFilterParameters(parameters)).toEqual(['EXAMS', 'EXERCISES', 'LECTURES', 'TUTORIALS']);
    });

    it('updates URL when exercise events are unchecked', () => {
        component.includeExerciseEvents.set(false);
        fixture.detectChanges();

        const url = component.subscriptionUrl();
        const parameters = getParametersOf(url);
        expect(splitFilterParameters(parameters)).toEqual(['EXAMS', 'LECTURES', 'TUTORIALS']);
    });

    it('updates URL when language is switched to GERMAN', () => {
        component.selectedLanguage.set('GERMAN');
        fixture.detectChanges();

        const url = component.subscriptionUrl();
        const parameters = getParametersOf(url);
        expect(parameters.get('language')).toBe('GERMAN');
    });

    it('copies the subscription link and shows the confirmation icon', () => {
        const writeText = vi.fn().mockResolvedValue(undefined);
        vi.stubGlobal('navigator', { clipboard: { writeText } });

        component.copySubscriptionUrlToClipboard();

        expect(writeText).toHaveBeenCalledWith(component.subscriptionUrl());
        expect(component.copiedUrl()).toBe(component.subscriptionUrl());
        expect(component.copyConfirmed()).toBe(true);
        vi.unstubAllGlobals();
    });

    it('shows the check mark only for the link that was copied, not for the new one after a reset or a change of the options', () => {
        const writeText = vi.fn().mockResolvedValue(undefined);
        vi.stubGlobal('navigator', { clipboard: { writeText } });
        component.copySubscriptionUrlToClipboard();
        expect(component.copyConfirmed()).toBe(true);

        component.selectedLanguage.set('GERMAN');
        fixture.detectChanges();
        expect(component.copiedUrl()).not.toBe(component.subscriptionUrl());
        expect(component.copyConfirmed()).toBe(false);

        component.selectedLanguage.set('ENGLISH');
        fixture.detectChanges();
        expect(component.copyConfirmed()).toBe(true);
        vi.unstubAllGlobals();
    });

    it('shows the check mark for the full time after the last copy, not only for what is left of the first one', () => {
        vi.useFakeTimers();
        try {
            vi.stubGlobal('navigator', { clipboard: { writeText: vi.fn().mockResolvedValue(undefined) } });
            component.copySubscriptionUrlToClipboard();
            fixture.detectChanges();

            // another link is copied shortly before the confirmation of the first one ends
            vi.advanceTimersByTime(1400);
            component.selectedLanguage.set('GERMAN');
            fixture.detectChanges();
            component.copySubscriptionUrlToClipboard();
            fixture.detectChanges();

            vi.advanceTimersByTime(200);
            expect(component.copyConfirmed()).toBe(true);

            vi.advanceTimersByTime(1300);
            expect(component.copyConfirmed()).toBe(false);
        } finally {
            vi.useRealTimers();
            vi.unstubAllGlobals();
        }
    });

    it('does not change anything after the component was destroyed while the confirmation was shown', () => {
        vi.useFakeTimers();
        try {
            vi.stubGlobal('navigator', { clipboard: { writeText: vi.fn().mockResolvedValue(undefined) } });
            component.copySubscriptionUrlToClipboard();
            fixture.detectChanges();

            fixture.destroy();

            expect(vi.getTimerCount()).toBe(0);
        } finally {
            vi.useRealTimers();
            vi.unstubAllGlobals();
        }
    });

    it('marks the last remaining checkbox as disabled', () => {
        component.includeLectureEvents.set(false);
        component.includeTutorialEvents.set(false);
        component.includeExamEvents.set(false);
        component.includeExerciseEvents.set(true);
        fixture.detectChanges();

        expect(component.exercisesIsLastSelectedEventType()).toBe(true);
        expect(component.lecturesIsLastSelectedEventType()).toBe(false);
        expect(component.tutorialsIsLastSelectedEventType()).toBe(false);
        expect(component.examsIsLastSelectedEventType()).toBe(false);
    });

    /** The anchors of the popovers that a test opened, which are removed after the test even if it fails. */
    const anchors: HTMLElement[] = [];
    afterEach(() => anchors.splice(0).forEach((anchor) => anchor.remove()));

    /** The content of the popover is only in the document while it is open. */
    const openPopover = () => {
        const origin = document.createElement('button');
        anchors.push(origin);
        document.body.appendChild(origin);
        component.open({ currentTarget: origin } as unknown as Event);
        fixture.detectChanges();
    };
    const shown = (testId: string) => document.querySelector(`[data-testid="${testId}"]`);
    const click = (testId: string) => {
        (shown(testId) as HTMLElement).click();
        fixture.detectChanges();
    };

    it('asks for a confirmation before it requests a new subscription link', () => {
        const resetSpy = vi.fn();
        component.subscriptionResetRequested.subscribe(resetSpy);
        openPopover();

        // the first button only asks, and cancelling the question requests nothing
        click('subscription-link-reset-button');
        expect(shown('subscription-link-reset-explanation')).not.toBeNull();
        expect(resetSpy).not.toHaveBeenCalled();
        click('subscription-link-reset-cancel');
        expect(shown('subscription-link-reset-explanation')).toBeNull();
        expect(shown('subscription-link-reset-button')).not.toBeNull();
        expect(resetSpy).not.toHaveBeenCalled();

        // only the confirmation requests a new link
        click('subscription-link-reset-button');
        click('subscription-link-reset-confirm');

        expect(resetSpy).toHaveBeenCalledOnce();
        expect(component.confirmingReset()).toBe(false);
    });

    it('keeps the buttons of the confirmation in the content that scrolls', () => {
        openPopover();
        click('subscription-link-reset-button');
        const content = shown('subscription-popover-content') as HTMLElement;

        expect(content.contains(shown('subscription-link-reset-confirm'))).toBe(true);
        expect(content.contains(shown('subscription-link-reset-cancel'))).toBe(true);
    });

    it('lets the content scroll when it is taller than the screen', () => {
        openPopover();
        const content = shown('subscription-popover-content') as HTMLElement;

        expect(content.classList.contains('overflow-y-auto')).toBe(true);
        expect(content.classList.contains('max-h-[75dvh]')).toBe(true);
    });

    it('gives the content on a small screen the width that its rows need, and never more than the screen has', () => {
        fixture.componentRef.setInput('isMobile', true);
        fixture.detectChanges();
        openPopover();
        const content = shown('subscription-popover-content') as HTMLElement;

        // a narrower box clips the language selection and the two columns of checkboxes, as the content scrolls
        expect(content.classList.contains('w-80')).toBe(true);
        expect(content.classList.contains('max-w-[calc(100vw-4rem)]')).toBe(true);
        expect(content.classList.contains('w-156')).toBe(false);
    });

    it('lets the buttons of the confirmation and the checkboxes wrap on a small screen', () => {
        fixture.componentRef.setInput('isMobile', true);
        fixture.detectChanges();
        openPopover();
        click('subscription-link-reset-button');

        const buttons = shown('subscription-link-reset-confirm')!.parentElement!;
        const checkboxes = shown('lecture-events-checkbox-label')!.parentElement!.parentElement!.parentElement!;
        expect(buttons.classList.contains('flex-wrap')).toBe(true);
        expect(checkboxes.classList.contains('flex-wrap')).toBe(true);
    });

    it('starts without a pending confirmation each time it is opened', () => {
        component.confirmingReset.set(true);
        const button = document.createElement('button');

        component.open({ currentTarget: button } as unknown as Event);

        expect(component.confirmingReset()).toBe(false);
    });

    it('offers neither the link nor its copy action while the link is replaced', () => {
        const writeText = vi.fn().mockResolvedValue(undefined);
        Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
        openPopover();
        expect(shown('subscription-link-copy-button')).not.toBeNull();
        expect(shown('subscription-link-reset-pending')).toBeNull();

        fixture.componentRef.setInput('resetting', true);
        fixture.detectChanges();
        // the message replaces the link, and the buttons of the reset are gone as well
        expect(shown('subscription-link-reset-pending')).not.toBeNull();
        expect(shown('subscription-link-copy-button')).toBeNull();
        expect(document.querySelector('[data-testid="subscription-popover-content"] textarea')).toBeNull();
        expect(shown('subscription-link-reset-button')).toBeNull();
        component.copySubscriptionUrlToClipboard();

        expect(writeText).not.toHaveBeenCalled();
        expect(component.copiedUrl()).toBeUndefined();

        fixture.componentRef.setInput('resetting', false);
        component.copySubscriptionUrlToClipboard();

        expect(writeText).toHaveBeenCalledOnce();
    });
});
