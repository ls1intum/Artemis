import { ComponentFixture, TestBed } from '@angular/core/testing';
import dayjs from 'dayjs/esm';
import { By } from '@angular/platform-browser';
import { MockDirective, MockPipe } from 'ng-mocks';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { faChalkboardUser, faCheckDouble, faDiagramProject, faFileArrowUp, faFont, faGraduationCap, faKeyboard, faPersonChalkboard } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import * as CalendarUtils from 'app/core/calendar/shared/util/calendar-util';
import { CalendarEvent } from 'app/core/calendar/shared/entities/calendar-event.model';
import { CalendarEventDetailPopoverComponent } from './calendar-event-detail-popover.component';

describe('CalendarEventDetailPopoverComponent', () => {
    let fixture: ComponentFixture<CalendarEventDetailPopoverComponent>;
    let component: CalendarEventDetailPopoverComponent;

    const baseEvent: CalendarEvent = {
        id: 'textExercise-1-dueDate',
        title: 'Lecture 1',
        startDate: dayjs('2025-07-05T10:00:00'),
        endDate: dayjs('2025-07-05T12:00:00'),
        location: 'Room 42',
        facilitator: 'Dr. Smith',
        isLectureEvent: () => true,
        isExamEvent: () => false,
        isTutorialEvent: () => false,
        isExerciseEvent: () => false,
        isProgrammingExercise: () => false,
        isTextExerciseEvent: () => false,
        isModelingExerciseEvent: () => false,
        isQuizExerciseEvent: () => false,
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CalendarEventDetailPopoverComponent, MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarEventDetailPopoverComponent);
        component = fixture.componentInstance;
    });

    function setEventInput(event: CalendarEvent) {
        fixture.componentRef.setInput('event', event);
        fixture.detectChanges();
    }

    it('should render start-and-end-row if endDate is provided', () => {
        setEventInput(baseEvent);
        const row = fixture.debugElement.query(By.css('#start-and-end-row'));
        expect(row).toBeTruthy();
        expect(fixture.debugElement.query(By.css('#start-row'))).toBeFalsy();
    });

    it('should render only start-row if endDate is missing', () => {
        const event = { ...baseEvent, endDate: undefined };
        fixture.componentRef.setInput('event', event);
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('#start-row'))).toBeTruthy();
        expect(fixture.debugElement.query(By.css('#start-and-end-row'))).toBeFalsy();
    });

    it('should render location-row if location is present', () => {
        setEventInput(baseEvent);
        expect(fixture.debugElement.query(By.css('#location-row'))).toBeTruthy();
    });

    it('should not render location-row if location is missing', () => {
        const event = { ...baseEvent, location: undefined };
        fixture.componentRef.setInput('event', event);
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('#location-row'))).toBeFalsy();
    });

    it('should render facilitator-row if facilitator is present', () => {
        setEventInput(baseEvent);
        expect(fixture.debugElement.query(By.css('#facilitator-row'))).toBeTruthy();
    });

    it('should not render facilitator-row if facilitator is missing', () => {
        const event = { ...baseEvent, facilitator: undefined };
        fixture.componentRef.setInput('event', event);
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('#facilitator-row'))).toBeFalsy();
    });

    it('should emit onClosePopover when close button is clicked', () => {
        const emitSpy = jest.spyOn(component.onClosePopover, 'emit');
        setEventInput(baseEvent);

        const closeButton = fixture.debugElement.query(By.css('.close-button')).nativeElement;
        closeButton.click();

        expect(emitSpy).toHaveBeenCalledOnce();
    });

    describe('should render correct subtype name for event', () => {
        const testCases: { id: string; expectedEventSubtypeNameKey: string | undefined }[] = [
            { id: 'tutorial-1', expectedEventSubtypeNameKey: undefined },
            { id: 'lecture-1-startAndDueDate', expectedEventSubtypeNameKey: undefined },
            { id: 'lecture-2-startDate', expectedEventSubtypeNameKey: 'artemisApp.calendar.eventSubtypeName.lectureStart' },
            { id: 'lecture-3-endDate', expectedEventSubtypeNameKey: 'artemisApp.calendar.eventSubtypeName.lectureEnd' },
            { id: 'exam-1-startAndEndDate', expectedEventSubtypeNameKey: undefined },
            { id: 'exam-2-publishResultsDate', expectedEventSubtypeNameKey: 'artemisApp.calendar.eventSubtypeName.examPublishResults' },
            { id: 'exam-3-studentReviewStartDate', expectedEventSubtypeNameKey: 'artemisApp.calendar.eventSubtypeName.examReviewStart' },
            { id: 'exam-4-studentReviewEndDate', expectedEventSubtypeNameKey: 'artemisApp.calendar.eventSubtypeName.examReviewEnd' },
            { id: 'quizExercise-1-startAndEndDate', expectedEventSubtypeNameKey: undefined },
            { id: 'quizExercise-2-releaseDate', expectedEventSubtypeNameKey: 'artemisApp.calendar.eventSubtypeName.exerciseRelease' },
            { id: 'quizExercise-3-dueDate', expectedEventSubtypeNameKey: 'artemisApp.calendar.eventSubtypeName.exerciseDue' },
            { id: 'textExercise-4-releaseDate', expectedEventSubtypeNameKey: 'artemisApp.calendar.eventSubtypeName.exerciseRelease' },
            { id: 'textExercise-5-startDate', expectedEventSubtypeNameKey: 'artemisApp.calendar.eventSubtypeName.exerciseStart' },
            { id: 'textExercise-6-dueDate', expectedEventSubtypeNameKey: 'artemisApp.calendar.eventSubtypeName.exerciseDue' },
            { id: 'textExercise-7-assessmentDueDate', expectedEventSubtypeNameKey: 'artemisApp.calendar.eventSubtypeName.exerciseAssessmentDue' },
        ];

        for (const { id, expectedEventSubtypeNameKey } of testCases) {
            it(`should render correct event description info row and header for event with ID: ${id}`, () => {
                const event = new CalendarEvent(id, 'Sample Event', dayjs());

                const spy = jest.spyOn(CalendarUtils, 'getEventSubtypeNameKey');

                setEventInput(event);

                expect(spy).toHaveBeenCalledWith(event);
                expect(spy).toHaveReturnedWith(expectedEventSubtypeNameKey);
            });
        }
    });

    describe('should render correct type name and icon for event', () => {
        const iconTestCases: {
            id: string;
            expectedIcon: IconDefinition;
            expectedEventNameKey: string;
        }[] = [
            { id: 'lecture-1', expectedIcon: faChalkboardUser, expectedEventNameKey: 'artemisApp.calendar.eventName.lecture' },
            { id: 'tutorial-2', expectedIcon: faPersonChalkboard, expectedEventNameKey: 'artemisApp.calendar.eventName.tutorial' },
            { id: 'exam-3', expectedIcon: faGraduationCap, expectedEventNameKey: 'artemisApp.calendar.eventName.exam' },
            { id: 'textExercise-4', expectedIcon: faFont, expectedEventNameKey: 'artemisApp.calendar.eventName.text' },
            { id: 'modelingExercise-5', expectedIcon: faDiagramProject, expectedEventNameKey: 'artemisApp.calendar.eventName.modeling' },
            { id: 'quizExercise-6', expectedIcon: faCheckDouble, expectedEventNameKey: 'artemisApp.calendar.eventName.quiz' },
            { id: 'programmingExercise-7', expectedIcon: faKeyboard, expectedEventNameKey: 'artemisApp.calendar.eventName.programming' },
            { id: 'fileUploadExercise-8', expectedIcon: faFileArrowUp, expectedEventNameKey: 'artemisApp.calendar.eventName.fileUpload' },
        ];

        for (const { id, expectedIcon, expectedEventNameKey } of iconTestCases) {
            it(`should call getEventTypeNameKey with the correct event for ID: ${id}`, () => {
                const event = new CalendarEvent(id, 'Sample Event', dayjs());

                const spy = jest.spyOn(CalendarUtils, 'getEventTypeNameKey');

                setEventInput(event);

                expect(spy).toHaveBeenCalledWith(event);
                expect(spy).toHaveReturnedWith(expectedEventNameKey);

                const faIconComponent = fixture.debugElement.query(By.css('.popover-icon')).componentInstance;
                expect(faIconComponent.icon).toBe(expectedIcon);
            });
        }
    });
});
