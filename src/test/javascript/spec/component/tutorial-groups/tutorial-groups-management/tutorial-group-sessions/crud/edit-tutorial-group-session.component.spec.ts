import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { MockRouter } from '../../../../../helpers/mocks/mock-router';
import { of } from 'rxjs';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { LoadingIndicatorContainerStubComponent } from '../../../../../helpers/stubs/loading-indicator-container-stub.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { EditTutorialGroupSessionComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-sessions/crud/edit-tutorial-group-session/edit-tutorial-group-session.component';
import { TutorialGroupSessionFormStubComponent } from '../../../stubs/tutorial-group-session-form-stub.component';
import { TutorialGroupSessionService } from 'app/course/tutorial-groups/services/tutorial-group-session.service';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { Router } from '@angular/router';
import {
    formDataToTutorialGroupSessionDTO,
    generateExampleTutorialGroupSession,
    tutorialGroupSessionToTutorialGroupSessionFormData,
} from '../../../helpers/tutorialGroupSessionExampleModels';
import { mockedActivatedRoute } from '../../../../../helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { Course } from 'app/entities/course.model';

describe('EditTutorialGroupSessionComponent', () => {
    let fixture: ComponentFixture<EditTutorialGroupSessionComponent>;
    let component: EditTutorialGroupSessionComponent;
    let sessionService: TutorialGroupSessionService;
    let findSessionSpy: jest.SpyInstance;
    let exampleSession: TutorialGroupSession;

    const router = new MockRouter();

    const timeZone = 'Europe/Berlin';
    const tutorialGroupId = 2;
    const sessionId = 3;
    const courseId = 5;
    const configurationId = 7;
    const course = {
        id: courseId,
        tutorialGroupsConfiguration: {
            id: configurationId,
            timeZone,
        },
    } as Course;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [EditTutorialGroupSessionComponent, LoadingIndicatorContainerStubComponent, TutorialGroupSessionFormStubComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(TutorialGroupsService),
                MockProvider(TutorialGroupSessionService),
                MockProvider(CourseManagementService),
                MockProvider(AlertService),
                { provide: Router, useValue: router },
                mockedActivatedRoute({ tutorialGroupId, sessionId }, {}, { course }, {}),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(EditTutorialGroupSessionComponent);
                component = fixture.componentInstance;
                sessionService = TestBed.inject(TutorialGroupSessionService);
                exampleSession = generateExampleTutorialGroupSession({});

                findSessionSpy = jest.spyOn(sessionService, 'getOneOfTutorialGroup').mockReturnValue(of(new HttpResponse({ body: exampleSession })));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(findSessionSpy).toHaveBeenCalledOnce();
        expect(findSessionSpy).toHaveBeenCalledWith(courseId, tutorialGroupId, sessionId);
    });

    it('should set form data correctly', () => {
        fixture.detectChanges();

        const formStub: TutorialGroupSessionFormStubComponent = fixture.debugElement.query(By.directive(TutorialGroupSessionFormStubComponent)).componentInstance;

        expect(component.session).toEqual(exampleSession);
        expect(findSessionSpy).toHaveBeenCalledOnce();
        expect(findSessionSpy).toHaveBeenCalledWith(courseId, tutorialGroupId, sessionId);

        expect(component.formData).toEqual(tutorialGroupSessionToTutorialGroupSessionFormData(exampleSession, timeZone));
        expect(formStub.formData).toEqual(component.formData);
    });

    it('should send PUT request upon form submission and navigate', () => {
        fixture.detectChanges();

        const changedSession: TutorialGroupSession = {
            ...exampleSession,
            location: 'Changed',
        };

        const updateResponse: HttpResponse<TutorialGroupSession> = new HttpResponse({
            body: changedSession,
            status: 200,
        });

        const updatedStub = jest.spyOn(sessionService, 'update').mockReturnValue(of(updateResponse));
        const navigateSpy = jest.spyOn(router, 'navigate');

        const sessionForm: TutorialGroupSessionFormStubComponent = fixture.debugElement.query(By.directive(TutorialGroupSessionFormStubComponent)).componentInstance;

        const formData = tutorialGroupSessionToTutorialGroupSessionFormData(changedSession, timeZone);

        sessionForm.formSubmitted.emit(formData);

        expect(updatedStub).toHaveBeenCalledOnce();
        expect(updatedStub).toHaveBeenCalledWith(courseId, tutorialGroupId, sessionId, formDataToTutorialGroupSessionDTO(formData));
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['/course-management', courseId, 'tutorial-groups', tutorialGroupId, 'sessions']);
    });
});
