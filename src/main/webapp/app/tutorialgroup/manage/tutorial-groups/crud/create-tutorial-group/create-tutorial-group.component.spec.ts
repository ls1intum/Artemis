import '@angular/localize/init';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { Router } from '@angular/router';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { of } from 'rxjs';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { CreateTutorialGroupComponent } from 'app/tutorialgroup/manage/tutorial-groups/crud/create-tutorial-group/create-tutorial-group.component';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { By } from '@angular/platform-browser';
import { generateExampleTutorialGroup, tutorialGroupToTutorialGroupFormData } from 'test/helpers/sample/tutorialgroup/tutorialGroupExampleModels';
import { mockedActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { TutorialGroupFormData } from '../tutorial-group-form/tutorial-group-form.component';
import { LoadingIndicatorContainerComponent } from '../../../../../shared/loading-indicator-container/loading-indicator-container.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { Component, input, output } from '@angular/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { Course } from 'app/core/course/shared/entities/course.model';
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
describe('CreateTutorialGroupComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<CreateTutorialGroupComponent>;
    let component: CreateTutorialGroupComponent;
    const course = { id: 1, title: 'Example' };
    let tutorialGroupService: TutorialGroupsService;

    const router = new MockRouter();

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [CreateTutorialGroupComponent, OwlNativeDateTimeModule, TutorialGroupFormStubComponent],
            providers: [
                MockProvider(TutorialGroupsService),
                MockProvider(AlertService),
                { provide: Router, useValue: router },
                mockedActivatedRoute({}, {}, { course }, {}),
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
                SessionStorageService,
                LocalStorageService,
            ],
        })
            .overrideComponent(CreateTutorialGroupComponent, {
                set: {
                    imports: [LoadingIndicatorContainerComponent, TranslateDirective, TutorialGroupFormStubComponent],
                },
            })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CreateTutorialGroupComponent);
                component = fixture.componentInstance;
                tutorialGroupService = TestBed.inject(TutorialGroupsService);
                vi.spyOn(tutorialGroupService, 'getUniqueCampusValues').mockReturnValue(of(new HttpResponse<string[]>({ body: [] })));
                vi.spyOn(tutorialGroupService, 'getUniqueLanguageValues').mockReturnValue(of(new HttpResponse<string[]>({ body: [] })));
                global.ResizeObserver = vi.fn().mockImplementation((callback: ResizeObserverCallback) => {
                    return new MockResizeObserver(callback);
                });
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).not.toBeNull();
    });

    it('should send POST request upon form submission and navigate', () => {
        const exampleTutorialGroup = generateExampleTutorialGroup({});
        delete exampleTutorialGroup.id;
        delete exampleTutorialGroup.isUserRegistered;
        delete exampleTutorialGroup.isUserTutor;
        delete exampleTutorialGroup.course;
        delete exampleTutorialGroup.numberOfRegisteredUsers;
        delete exampleTutorialGroup.courseTitle;
        delete exampleTutorialGroup.teachingAssistantName;
        delete exampleTutorialGroup.teachingAssistantId;
        delete exampleTutorialGroup.teachingAssistantImageUrl;
        delete exampleTutorialGroup.tutorialGroupSchedule!.id;

        const createResponse: HttpResponse<TutorialGroup> = new HttpResponse({
            body: exampleTutorialGroup,
            status: 201,
        });

        const createStub = vi.spyOn(tutorialGroupService, 'create').mockReturnValue(of(createResponse));
        const navigateSpy = vi.spyOn(router, 'navigate');

        expect(fixture.nativeElement.innerHTML).toContain('jhi-tutorial-group-form');

        const tutorialGroupElement = fixture.debugElement.query(By.css('jhi-tutorial-group-form'));
        expect(tutorialGroupElement).not.toBeNull();

        const tutorialGroupForm = tutorialGroupElement.componentInstance;

        const formData = tutorialGroupToTutorialGroupFormData(exampleTutorialGroup);

        tutorialGroupForm.formSubmitted.emit(formData);

        expect(createStub).toHaveBeenCalledOnce();
        expect(createStub).toHaveBeenCalledWith(exampleTutorialGroup, course.id);
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['/course-management', course.id, 'tutorial-groups']);
    });
});
