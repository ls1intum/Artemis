import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { SortService } from 'app/shared/service/sort.service';
import { LoadingIndicatorContainerStubComponent } from 'test/helpers/stubs/shared/loading-indicator-container-stub.component';
import dayjs from 'dayjs/esm';
import { TutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { TutorialGroupSessionsManagementComponent } from 'app/tutorialgroup/manage/tutorial-group-sessions/tutorial-group-sessions-management/tutorial-group-sessions-management.component';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { generateExampleTutorialGroupSession } from 'test/helpers/sample/tutorialgroup/tutorialGroupSessionExampleModels';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { generateExampleTutorialGroup } from 'test/helpers/sample/tutorialgroup/tutorialGroupExampleModels';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TutorialGroupSessionRowStubComponent, TutorialGroupSessionsTableStubComponent } from 'test/helpers/stubs/tutorialgroup/tutorial-group-sessions-table-stub.component';
import { NgbActiveModal, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { CreateTutorialGroupSessionComponent } from 'app/tutorialgroup/manage/tutorial-group-sessions/crud/create-tutorial-group-session/create-tutorial-group-session.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { RemoveSecondsPipe } from 'app/tutorialgroup/shared/pipe/remove-seconds.pipe';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';

describe('TutorialGroupSessionsManagement', () => {
    let fixture: ComponentFixture<TutorialGroupSessionsManagementComponent>;
    let component: TutorialGroupSessionsManagementComponent;
    let modalService: NgbModal;

    const tutorialGroupId = 2;

    let tutorialGroupService: TutorialGroupsService;
    let getOneOfCourseSpy: jest.SpyInstance;

    let pastSession: TutorialGroupSession;
    let upcomingSession: TutorialGroupSession;
    let tutorialGroup: TutorialGroup;

    const course = {
        id: 1,
        timeZone: 'Europe/Berlin',
    } as Course;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [FaIconComponent],
            declarations: [
                TutorialGroupSessionsManagementComponent,
                TutorialGroupSessionRowStubComponent,
                TutorialGroupSessionsTableStubComponent,
                LoadingIndicatorContainerStubComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(RemoveSecondsPipe),
                MockRouterLinkDirective,
            ],
            providers: [
                MockProvider(TutorialGroupsService),
                MockProvider(AlertService),
                MockProvider(NgbActiveModal),
                MockProvider(NgbModal),
                SortService,
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(CalendarService),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorialGroupSessionsManagementComponent);
                component = fixture.componentInstance;
                modalService = TestBed.inject(NgbModal);

                pastSession = generateExampleTutorialGroupSession({
                    id: 1,
                    start: dayjs('2021-01-01T12:00:00.000Z').tz('Europe/Berlin'),
                    end: dayjs('2021-01-01T13:00:00.000Z').tz('Europe/Berlin'),
                    location: 'Room 1',
                });
                upcomingSession = generateExampleTutorialGroupSession({
                    id: 2,
                    start: dayjs('2021-01-03T12:00:00.000Z').tz('Europe/Berlin'),
                    end: dayjs('2021-01-03T13:00:00.000Z').tz('Europe/Berlin'),
                    location: 'Room 1',
                });
                tutorialGroup = generateExampleTutorialGroup({ id: tutorialGroupId });
                tutorialGroup.tutorialGroupSessions = [pastSession, upcomingSession];

                tutorialGroupService = TestBed.inject(TutorialGroupsService);
                getOneOfCourseSpy = jest.spyOn(tutorialGroupService, 'getOneOfCourse').mockReturnValue(of(new HttpResponse({ body: tutorialGroup })));

                component.course = course;
                component.tutorialGroupId = tutorialGroupId;
                component.initialize();
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
        expect(getOneOfCourseSpy).toHaveBeenCalledOnce();
        expect(getOneOfCourseSpy).toHaveBeenCalledWith(course.id!, tutorialGroupId);
        expect(component.tutorialGroup).toEqual(tutorialGroup);
        expect(component.tutorialGroupSchedule).toEqual(tutorialGroup.tutorialGroupSchedule);
        expect(component.course).toEqual(course);
    });

    it('should open create session dialog', fakeAsync(() => {
        const openSpy = jest
            .spyOn(modalService, 'open')
            .mockReturnValue({ componentInstance: { tutorialGroup: undefined, course: undefined, initialize: () => {} }, result: of() } as any);

        const editButton = fixture.debugElement.nativeElement.querySelector('#create-session-button');
        editButton.click();

        fixture.whenStable().then(() => {
            expect(openSpy).toHaveBeenCalledOnce();
            expect(openSpy).toHaveBeenCalledWith(CreateTutorialGroupSessionComponent, { size: 'xl', scrollable: false, backdrop: 'static', animation: false });
        });
    }));

    it('should call calendarService.refresh in loadAll', fakeAsync(() => {
        const calendarService = TestBed.inject(CalendarService);
        const refreshSpy = jest.spyOn(calendarService, 'reloadEvents').mockImplementation(() => {});

        component.course = course;
        component.tutorialGroupId = tutorialGroupId;

        const getOneOfCourseSpy = jest.spyOn(tutorialGroupService, 'getOneOfCourse').mockReturnValue(of(new HttpResponse({ body: tutorialGroup })));

        component.loadAll();
        tick();
        fixture.changeDetectorRef.detectChanges();

        expect(getOneOfCourseSpy).toHaveBeenCalledWith(course.id!, tutorialGroupId);
        expect(refreshSpy).toHaveBeenCalledOnce();
    }));
});
