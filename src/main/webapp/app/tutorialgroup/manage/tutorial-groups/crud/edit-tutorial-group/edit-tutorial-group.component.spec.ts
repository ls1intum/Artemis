import '@angular/localize/init';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Component, input, output } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { Router } from '@angular/router';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { of } from 'rxjs';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { By } from '@angular/platform-browser';
import { EditTutorialGroupComponent } from 'app/tutorialgroup/manage/tutorial-groups/crud/edit-tutorial-group/edit-tutorial-group.component';
import { generateExampleTutorialGroup, tutorialGroupToTutorialGroupFormData } from 'test/helpers/sample/tutorialgroup/tutorialGroupExampleModels';
import { mockedActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { ArtemisDatePipe } from '../../../../../shared/pipes/artemis-date.pipe';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { TutorialGroupFormData } from '../tutorial-group-form/tutorial-group-form.component';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { LoadingIndicatorContainerComponent } from 'app/shared/loading-indicator-container/loading-indicator-container.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-tutorial-group-form',
    template: `
        <input #teachingAssistantInput="ngbTypeahead" />
        <input #campusInput="ngbTypeahead" />
        <input #languageInput="ngbTypeahead" />
    `,
})
class TutorialGroupFormStubComponent {
    formData = input<TutorialGroupFormData>();
    course = input<Course>();
    isEditMode = input<boolean>(false);

    formSubmitted = output<TutorialGroupFormData>();
}

describe('EditTutorialGroupComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<EditTutorialGroupComponent>;
    let component: EditTutorialGroupComponent;
    let findTutorialGroupSpy: ReturnType<typeof vi.spyOn>;
    let tutorialGroupService: TutorialGroupsService;
    const course = { id: 2, title: 'Example' };

    let exampleTutorialGroup: TutorialGroup;

    const router = new MockRouter();

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [EditTutorialGroupComponent, OwlNativeDateTimeModule, TutorialGroupFormStubComponent],
            providers: [
                MockProvider(ArtemisDatePipe),
                MockProvider(AlertService),
                MockProvider(CalendarService),
                { provide: Router, useValue: router },
                mockedActivatedRoute(
                    {
                        tutorialGroupId: 1,
                    },
                    {},
                    { course },
                    {},
                ),
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideComponent(EditTutorialGroupComponent, {
                set: {
                    imports: [LoadingIndicatorContainerComponent, TranslateDirective, TutorialGroupFormStubComponent],
                },
            })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(EditTutorialGroupComponent);
                component = fixture.componentInstance;
                exampleTutorialGroup = generateExampleTutorialGroup({});

                tutorialGroupService = TestBed.inject(TutorialGroupsService);

                const response: HttpResponse<TutorialGroup> = new HttpResponse({
                    body: exampleTutorialGroup,
                    status: 200,
                });

                global.ResizeObserver = vi.fn().mockImplementation((callback: ResizeObserverCallback) => {
                    return new MockResizeObserver(callback);
                });

                findTutorialGroupSpy = vi.spyOn(tutorialGroupService, 'getOneOfCourse').mockReturnValue(of(response));
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).not.toBeNull();
        expect(findTutorialGroupSpy).toHaveBeenCalledOnce();
        expect(findTutorialGroupSpy).toHaveBeenCalledWith(2, 1);
    });

    it('should set form data correctly', () => {
        const tutorialGroupFormComponent: TutorialGroupFormStubComponent = fixture.debugElement.query(By.directive(TutorialGroupFormStubComponent)).componentInstance;

        expect(component.tutorialGroup).toEqual(exampleTutorialGroup);
        expect(findTutorialGroupSpy).toHaveBeenCalledWith(2, 1);
        expect(findTutorialGroupSpy).toHaveBeenCalledOnce();

        expect(component.formData).toEqual(tutorialGroupToTutorialGroupFormData(exampleTutorialGroup));
        expect(tutorialGroupFormComponent.formData()).toEqual(component.formData);
    });

    it('should send PUT request upon form submission and navigate', () => {
        delete exampleTutorialGroup.isUserRegistered;
        delete exampleTutorialGroup.isUserTutor;
        delete exampleTutorialGroup.course;
        delete exampleTutorialGroup.numberOfRegisteredUsers;
        delete exampleTutorialGroup.courseTitle;
        delete exampleTutorialGroup.teachingAssistantName;
        delete exampleTutorialGroup.teachingAssistantId;
        delete exampleTutorialGroup.teachingAssistantImageUrl;

        const changedTutorialGroup = {
            ...exampleTutorialGroup,
            title: 'Changed',
        } as TutorialGroup;

        const updateResponse: HttpResponse<TutorialGroup> = new HttpResponse({
            body: changedTutorialGroup,
            status: 200,
        });

        const updatedStub = vi.spyOn(tutorialGroupService, 'update').mockReturnValue(of(updateResponse));
        const navigateSpy = vi.spyOn(router, 'navigate');
        const calendarService = TestBed.inject(CalendarService);
        const refreshSpy = vi.spyOn(calendarService, 'reloadEvents');

        const tutorialGroupForm: TutorialGroupFormStubComponent = fixture.debugElement.query(By.directive(TutorialGroupFormStubComponent)).componentInstance;

        const formData = tutorialGroupToTutorialGroupFormData(changedTutorialGroup);

        tutorialGroupForm.formSubmitted.emit(formData);

        expect(updatedStub).toHaveBeenCalledOnce();
        expect(updatedStub).toHaveBeenCalledWith(2, 1, changedTutorialGroup, undefined, undefined);
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['/course-management', 2, 'tutorial-groups']);
        expect(refreshSpy).toHaveBeenCalledOnce();
    });
});
