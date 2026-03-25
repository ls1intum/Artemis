import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { MockDirective } from 'ng-mocks';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { OnboardingCommunicationComponent } from './onboarding-communication.component';
import { Course, CourseInformationSharingConfiguration } from 'app/core/course/shared/entities/course.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('OnboardingCommunicationComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: OnboardingCommunicationComponent;
    let fixture: ComponentFixture<OnboardingCommunicationComponent>;
    let course: Course;

    beforeEach(async () => {
        course = new Course();
        course.id = 1;
        course.title = 'Test Course';
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;

        await TestBed.configureTestingModule({
            imports: [OnboardingCommunicationComponent, FormsModule],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideHttpClient(), provideHttpClientTesting()],
        })
            .overrideComponent(OnboardingCommunicationComponent, {
                remove: { imports: [TranslateDirective] },
                add: { imports: [MockDirective(TranslateDirective)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(OnboardingCommunicationComponent);
        fixture.componentRef.setInput('course', course);
        comp = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize with the provided course', () => {
        expect(comp).toBeTruthy();
        expect(comp.course()).toEqual(course);
    });

    describe('communicationEnabled', () => {
        it('should return true when configuration is COMMUNICATION_AND_MESSAGING', () => {
            course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
            expect(comp.communicationEnabled).toBe(true);
        });

        it('should return true when configuration is COMMUNICATION_ONLY', () => {
            course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_ONLY;
            expect(comp.communicationEnabled).toBe(true);
        });

        it('should return false when configuration is DISABLED', () => {
            course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.DISABLED;
            expect(comp.communicationEnabled).toBe(false);
        });
    });

    describe('messagingEnabled', () => {
        it('should return true when configuration is COMMUNICATION_AND_MESSAGING', () => {
            course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
            expect(comp.messagingEnabled).toBe(true);
        });

        it('should return false when configuration is COMMUNICATION_ONLY', () => {
            course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_ONLY;
            expect(comp.messagingEnabled).toBe(false);
        });

        it('should return false when configuration is DISABLED', () => {
            course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.DISABLED;
            expect(comp.messagingEnabled).toBe(false);
        });
    });

    describe('toggleCommunication', () => {
        it('should disable communication when currently enabled', () => {
            course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
            fixture.componentRef.setInput('course', course);
            const emitSpy = vi.spyOn(comp.courseUpdated, 'emit');

            comp.toggleCommunication();

            expect(emitSpy).toHaveBeenCalled();
            const emitted = emitSpy.mock.calls[0][0];
            expect(emitted.courseInformationSharingConfiguration).toBe(CourseInformationSharingConfiguration.DISABLED);
        });

        it('should enable communication only when currently disabled', () => {
            course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.DISABLED;
            fixture.componentRef.setInput('course', course);
            const emitSpy = vi.spyOn(comp.courseUpdated, 'emit');

            comp.toggleCommunication();

            expect(emitSpy).toHaveBeenCalled();
            const emitted = emitSpy.mock.calls[0][0];
            expect(emitted.courseInformationSharingConfiguration).toBe(CourseInformationSharingConfiguration.COMMUNICATION_ONLY);
        });
    });

    describe('toggleMessaging', () => {
        it('should disable messaging but keep communication when messaging is enabled', () => {
            course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
            fixture.componentRef.setInput('course', course);
            const emitSpy = vi.spyOn(comp.courseUpdated, 'emit');

            comp.toggleMessaging();

            expect(emitSpy).toHaveBeenCalled();
            const emitted = emitSpy.mock.calls[0][0];
            expect(emitted.courseInformationSharingConfiguration).toBe(CourseInformationSharingConfiguration.COMMUNICATION_ONLY);
        });

        it('should enable messaging when communication only is active', () => {
            course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_ONLY;
            fixture.componentRef.setInput('course', course);
            const emitSpy = vi.spyOn(comp.courseUpdated, 'emit');

            comp.toggleMessaging();

            expect(emitSpy).toHaveBeenCalled();
            const emitted = emitSpy.mock.calls[0][0];
            expect(emitted.courseInformationSharingConfiguration).toBe(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
        });
    });

    describe('updateCodeOfConduct', () => {
        it('should update the code of conduct and emit change', () => {
            const emitSpy = vi.spyOn(comp.courseUpdated, 'emit');

            comp.updateCodeOfConduct('Be respectful to everyone.');

            expect(emitSpy).toHaveBeenCalled();
            const emitted = emitSpy.mock.calls[0][0];
            expect(emitted.courseInformationSharingMessagingCodeOfConduct).toBe('Be respectful to everyone.');
        });
    });
});
