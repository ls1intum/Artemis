import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { CalendarSubscriptionPopoverComponent } from 'app/core/calendar/shared/calendar-subscription-popover/calendar-subscription-popover.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideNoopAnimations } from '@angular/platform-browser/animations';

const getParametersOf = (urlStr: string) => new URL(urlStr, 'http://dummy.invalid').searchParams;
const splitFilterParameters = (parameters: URLSearchParams) => (parameters.get('filterOptions') ?? '').split(',').filter(Boolean).sort();

describe('CalendarSubscriptionPopoverComponent (signal-based tests)', () => {
    let fixture: ComponentFixture<CalendarSubscriptionPopoverComponent>;
    let component: CalendarSubscriptionPopoverComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CalendarSubscriptionPopoverComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideNoopAnimations()],
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

    it('marks the last remaining checkbox as disabled', () => {
        component.includeLectureEvents.set(false);
        component.includeTutorialEvents.set(false);
        component.includeExamEvents.set(false);
        component.includeExerciseEvents.set(true);
        fixture.detectChanges();

        expect(component.exercisesIsLastSelectedEventType()).toBeTrue();
        expect(component.lecturesIsLastSelectedEventType()).toBeFalse();
        expect(component.tutorialsIsLastSelectedEventType()).toBeFalse();
        expect(component.examsIsLastSelectedEventType()).toBeFalse();
    });
});
