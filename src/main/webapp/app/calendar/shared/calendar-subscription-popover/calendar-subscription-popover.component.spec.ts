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

    afterEach(() => {
        vi.restoreAllMocks();
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
        expect(component.copiedUrl()).toBe(true);
        vi.unstubAllGlobals();
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

    it('asks for a confirmation before it requests a new subscription link', () => {
        const resetSpy = vi.fn();
        component.subscriptionResetRequested.subscribe(resetSpy);

        expect(component.confirmingReset()).toBe(false);
        component.confirmingReset.set(true);
        component.confirmingReset.set(false);
        expect(resetSpy).not.toHaveBeenCalled();

        component.confirmingReset.set(true);
        component.resetSubscriptionLink();

        expect(resetSpy).toHaveBeenCalledOnce();
        expect(component.confirmingReset()).toBe(false);
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

        fixture.componentRef.setInput('resetting', true);
        component.copySubscriptionUrlToClipboard();

        expect(writeText).not.toHaveBeenCalled();
        expect(component.copiedUrl()).toBe(false);

        fixture.componentRef.setInput('resetting', false);
        component.copySubscriptionUrlToClipboard();

        expect(writeText).toHaveBeenCalledOnce();
    });
});
