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
import { EditTutorialGroupSessionComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-group-sessions/crud/edit-tutorial-group-session/edit-tutorial-group-session.component';
import { TutorialGroupSessionFormStubComponent } from '../../../stubs/tutorial-group-session-form-stub.component';
import { TutorialGroupSessionService } from 'app/course/tutorial-groups/services/tutorial-group-session.service';
import { simpleOneLayerActivatedRouteProvider } from '../../../../../helpers/mocks/activated-route/simple-activated-route-providers';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import dayjs from 'dayjs/esm';
import { Router } from '@angular/router';

describe('EditTutorialGroupSessionComponent', () => {
    let fixture: ComponentFixture<EditTutorialGroupSessionComponent>;
    let component: EditTutorialGroupSessionComponent;
    let sessionService: TutorialGroupSessionService;
    let findSessionSpy: jest.SpyInstance;
    let exampleSession: TutorialGroupSession;

    const router = new MockRouter();

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [EditTutorialGroupSessionComponent, LoadingIndicatorContainerStubComponent, TutorialGroupSessionFormStubComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(TutorialGroupsService),
                MockProvider(TutorialGroupSessionService),
                MockProvider(CourseManagementService),
                MockProvider(AlertService),
                { provide: Router, useValue: router },
                simpleOneLayerActivatedRouteProvider(
                    new Map([
                        ['tutorialGroupId', 2],
                        ['sessionId', 3],
                    ]),
                    {
                        course: {
                            id: 1,
                            tutorialGroupsConfiguration: {
                                id: 1,
                                timeZone: 'Europe/Berlin',
                            },
                        },
                    },
                ),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(EditTutorialGroupSessionComponent);
                component = fixture.componentInstance;
                sessionService = TestBed.inject(TutorialGroupSessionService);

                exampleSession = new TutorialGroupSession();
                exampleSession.id = 3;
                // we get utc from the server --> will be converted to time zone of configuration
                exampleSession.start = dayjs.utc('2021-01-01T10:00:00');
                exampleSession.end = dayjs.utc('2021-01-01T11:00:00');
                exampleSession.location = 'Room 1';

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
        expect(findSessionSpy).toHaveBeenCalledWith(1, 2, 3);
    });

    it('should set form data correctly', () => {
        fixture.detectChanges();

        const formStub: TutorialGroupSessionFormStubComponent = fixture.debugElement.query(By.directive(TutorialGroupSessionFormStubComponent)).componentInstance;

        expect(component.session).toEqual(exampleSession);
        expect(findSessionSpy).toHaveBeenCalledOnce();
        expect(findSessionSpy).toHaveBeenCalledWith(1, 2, 3);

        expect(component.formData.location).toEqual(exampleSession.location);
        // converted to berlin time
        expect(component.formData.startTime).toBe('11:00:00');
        expect(component.formData.endTime).toBe('12:00:00');
        expect(component.formData.date).toStrictEqual(dayjs('2021-01-01T00:00:00').tz('Europe/Berlin').toDate());
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

        const formData = {
            date: dayjs('2021-01-01T00:00:00').tz('Europe/Berlin').toDate(),
            startTime: '11:00:00',
            endTime: '12:00:00',
            location: 'Changed',
        };

        sessionForm.formSubmitted.emit(formData);

        expect(updatedStub).toHaveBeenCalledOnce();
        expect(updatedStub).toHaveBeenCalledWith(1, 2, 3, {
            date: formData.date,
            startTime: formData.startTime,
            endTime: formData.endTime,
            location: formData.location,
        });
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['course-management', 1, 'tutorial-groups-management', 2, 'sessions']);
    });
});
