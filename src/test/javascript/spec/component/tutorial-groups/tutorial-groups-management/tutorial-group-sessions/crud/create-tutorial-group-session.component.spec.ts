// tslint:disable:max-line-length
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { Router } from '@angular/router';
import { MockRouter } from '../../../../../helpers/mocks/mock-router';
import { of } from 'rxjs';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { LoadingIndicatorContainerStubComponent } from '../../../../../helpers/stubs/loading-indicator-container-stub.component';
import { CreateTutorialGroupSessionComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-sessions/crud/create-tutorial-group-session/create-tutorial-group-session.component';
import { TutorialGroupSessionService } from 'app/course/tutorial-groups/services/tutorial-group-session.service';
import {
    formDataToTutorialGroupSessionDTO,
    generateExampleTutorialGroupSession,
    tutorialGroupSessionToTutorialGroupSessionFormData,
} from '../../../helpers/tutorialGroupSessionExampleModels';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { TutorialGroupSessionFormStubComponent } from '../../../stubs/tutorial-group-session-form-stub.component';
import { generateExampleTutorialGroup } from '../../../helpers/tutorialGroupExampleModels';
import { mockedActivatedRoute } from '../../../../../helpers/mocks/activated-route/mock-activated-route-query-param-map';

describe('CreateTutorialGroupSessionComponent', () => {
    let fixture: ComponentFixture<CreateTutorialGroupSessionComponent>;
    let component: CreateTutorialGroupSessionComponent;
    let tutorialGroupService: TutorialGroupsService;
    let tutorialGroupSessionService: TutorialGroupSessionService;
    const courseId = 2;
    const tutorialGroupId = 1;

    let findTutorialGroupSpy: jest.SpyInstance;

    const router = new MockRouter();

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [CreateTutorialGroupSessionComponent, LoadingIndicatorContainerStubComponent, TutorialGroupSessionFormStubComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(TutorialGroupsService),
                MockProvider(TutorialGroupSessionService),
                MockProvider(AlertService),
                { provide: Router, useValue: router },
                mockedActivatedRoute({ tutorialGroupId }, {}, {}, { courseId }),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CreateTutorialGroupSessionComponent);
                component = fixture.componentInstance;
                tutorialGroupService = TestBed.inject(TutorialGroupsService);
                tutorialGroupSessionService = TestBed.inject(TutorialGroupSessionService);

                findTutorialGroupSpy = jest.spyOn(tutorialGroupService, 'getOneOfCourse').mockReturnValue(of(new HttpResponse({ body: generateExampleTutorialGroup({}) })));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(findTutorialGroupSpy).toHaveBeenCalledOnce();
        expect(findTutorialGroupSpy).toHaveBeenCalledWith(courseId, tutorialGroupId);
    });

    it('should send POST request upon form submission and navigate', () => {
        fixture.detectChanges();
        const exampleSession = generateExampleTutorialGroupSession({});
        delete exampleSession.id;

        const createResponse: HttpResponse<TutorialGroupSession> = new HttpResponse({
            body: exampleSession,
            status: 201,
        });

        const createStub = jest.spyOn(tutorialGroupSessionService, 'create').mockReturnValue(of(createResponse));
        const navigateSpy = jest.spyOn(router, 'navigate');

        const sessionForm: TutorialGroupSessionFormStubComponent = fixture.debugElement.query(By.directive(TutorialGroupSessionFormStubComponent)).componentInstance;

        const formData = tutorialGroupSessionToTutorialGroupSessionFormData(exampleSession, 'Europe/Berlin');

        sessionForm.formSubmitted.emit(formData);

        expect(createStub).toHaveBeenCalledOnce();
        expect(createStub).toHaveBeenCalledWith(courseId, tutorialGroupId, formDataToTutorialGroupSessionDTO(formData));
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['/course-management', courseId, 'tutorial-groups', tutorialGroupId, 'sessions']);
    });
});
