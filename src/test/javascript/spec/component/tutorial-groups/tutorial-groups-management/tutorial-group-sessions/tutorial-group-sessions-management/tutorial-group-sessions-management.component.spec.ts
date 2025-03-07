import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockRouterLinkDirective } from '../../../../../helpers/mocks/directive/mock-router-link.directive';
import { SortService } from 'app/shared/service/sort.service';
import { LoadingIndicatorContainerStubComponent } from '../../../../../helpers/stubs/loading-indicator-container-stub.component';
import dayjs from 'dayjs/esm';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { TutorialGroupSessionsManagementComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-sessions/tutorial-group-sessions-management/tutorial-group-sessions-management.component';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { generateExampleTutorialGroupSession } from '../../../helpers/tutorialGroupSessionExampleModels';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { generateExampleTutorialGroup } from '../../../helpers/tutorialGroupExampleModels';
import { Course } from 'app/entities/course.model';
import { TutorialGroupSessionRowStubComponent, TutorialGroupSessionsTableStubComponent } from '../../../stubs/tutorial-group-sessions-table-stub.component';
import { NgbActiveModal, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { CreateTutorialGroupSessionComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-sessions/crud/create-tutorial-group-session/create-tutorial-group-session.component';
import { RemoveSecondsPipe } from 'app/course/tutorial-groups/shared/remove-seconds.pipe';
import { MockTranslateService } from '../../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';

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
            declarations: [
                TutorialGroupSessionsManagementComponent,
                TutorialGroupSessionRowStubComponent,
                TutorialGroupSessionsTableStubComponent,
                LoadingIndicatorContainerStubComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(RemoveSecondsPipe),
                MockComponent(FaIconComponent),
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

                fixture.componentRef.setInput('tutorialGroupId', tutorialGroupId);
                fixture.componentRef.setInput('course', course);
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
        expect(component.course()).toEqual(course);
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
});
