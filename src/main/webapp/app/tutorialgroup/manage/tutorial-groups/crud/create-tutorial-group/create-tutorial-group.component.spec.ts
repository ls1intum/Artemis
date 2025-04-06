import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { Router } from '@angular/router';
import { MockRouter } from '../../../../../../../../test/javascript/spec/helpers/mocks/mock-router';
import { of } from 'rxjs';
import { CreateTutorialGroupComponent } from 'app/tutorialgroup/manage/tutorial-groups/crud/create-tutorial-group/create-tutorial-group.component';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HttpResponse } from '@angular/common/http';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { By } from '@angular/platform-browser';
import {
    generateExampleTutorialGroup,
    tutorialGroupToTutorialGroupFormData,
} from '../../../../../../../../test/javascript/spec/component/tutorial-groups/helpers/tutorialGroupExampleModels';
import { mockedActivatedRoute } from '../../../../../../../../test/javascript/spec/helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { TutorialGroupFormComponent } from '../tutorial-group-form/tutorial-group-form.component';
import { LoadingIndicatorContainerComponent } from '../../../../../shared/loading-indicator-container/loading-indicator-container.component';
import { MockTranslateService } from '../../../../../../../../test/javascript/spec/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('CreateTutorialGroupComponent', () => {
    let fixture: ComponentFixture<CreateTutorialGroupComponent>;
    let component: CreateTutorialGroupComponent;
    const course = { id: 1, title: 'Example' };
    let tutorialGroupService: TutorialGroupsService;

    const router = new MockRouter();

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [
                CreateTutorialGroupComponent,
                MockComponent(LoadingIndicatorContainerComponent),
                MockComponent(TutorialGroupFormComponent),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                MockProvider(TutorialGroupsService),
                MockProvider(AlertService),
                { provide: Router, useValue: router },
                mockedActivatedRoute({}, {}, { course }, {}),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CreateTutorialGroupComponent);
        component = fixture.componentInstance;
        tutorialGroupService = TestBed.inject(TutorialGroupsService);
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
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

        const createStub = jest.spyOn(tutorialGroupService, 'create').mockReturnValue(of(createResponse));
        const navigateSpy = jest.spyOn(router, 'navigate');

        const tutorialGroupForm: TutorialGroupFormComponent = fixture.debugElement.query(By.directive(TutorialGroupFormComponent)).componentInstance;

        const formData = tutorialGroupToTutorialGroupFormData(exampleTutorialGroup);

        tutorialGroupForm.formSubmitted.emit(formData);

        expect(createStub).toHaveBeenCalledOnce();
        expect(createStub).toHaveBeenCalledWith(exampleTutorialGroup, course.id);
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['/course-management', course.id, 'tutorial-groups']);
    });
});
