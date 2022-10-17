import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { MockRouter } from '../../../../../helpers/mocks/mock-router';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HttpResponse } from '@angular/common/http';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockRouterLinkDirective } from '../../../../../helpers/mocks/directive/mock-router-link.directive';
import { SortService } from 'app/shared/service/sort.service';
import { LoadingIndicatorContainerStubComponent } from '../../../../../helpers/stubs/loading-indicator-container-stub.component';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Router } from '@angular/router';
import { generateExampleTutorialGroupsConfiguration } from '../../../helpers/tutorialGroupsConfigurationExampleModels';
import dayjs from 'dayjs/esm';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { TutorialGroupSessionsManagementComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-sessions/tutorial-group-sessions-management/tutorial-group-sessions-management.component';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { generateExampleTutorialGroupSession } from '../../../helpers/tutorialGroupSessionExampleModels';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { generateExampleTutorialGroup } from '../../../helpers/tutorialGroupExampleModels';
import { By } from '@angular/platform-browser';
import { mockedActivatedRoute } from '../../../../../helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { Course } from 'app/entities/course.model';

@Component({ selector: '[jhi-session-row]', template: '' })
class TutorialGroupSessionRowStubComponent {
    @Input() session: TutorialGroupSession;
    @Input() courseId: number;
    @Input() tutorialGroupId: number;
    @Input() timeZone: string;

    @Output() actionPerformed = new EventEmitter<void>();
}

describe('TutorialGroupSessionsManagement', () => {
    let fixture: ComponentFixture<TutorialGroupSessionsManagementComponent>;
    let component: TutorialGroupSessionsManagementComponent;
    const courseId = 1;
    const tutorialGroupId = 2;

    let tutorialGroupService: TutorialGroupsService;
    let getOneOfCourseSpy: jest.SpyInstance;

    let pastSession: TutorialGroupSession;
    let upcomingSession: TutorialGroupSession;
    let tutorialGroup: TutorialGroup;
    const currentDate = dayjs(new Date(Date.UTC(2021, 0, 2, 12, 0, 0)));
    const course = {
        id: courseId,
        tutorialGroupsConfiguration: generateExampleTutorialGroupsConfiguration({}),
    } as Course;

    const router = new MockRouter();
    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [
                TutorialGroupSessionsManagementComponent,
                TutorialGroupSessionRowStubComponent,
                LoadingIndicatorContainerStubComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(FaIconComponent),
                MockRouterLinkDirective,
            ],
            providers: [
                MockProvider(TutorialGroupsService),
                MockProvider(AlertService),
                SortService,
                { provide: Router, useValue: router },
                mockedActivatedRoute({ tutorialGroupId }, {}, { course }, {}),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorialGroupSessionsManagementComponent);
                component = fixture.componentInstance;

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
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).toBeTruthy();
        expect(getOneOfCourseSpy).toHaveBeenCalledOnce();
        expect(getOneOfCourseSpy).toHaveBeenCalledWith(courseId, tutorialGroupId);
        expect(component.tutorialGroup).toEqual(tutorialGroup);
        expect(component.tutorialGroupSchedule).toEqual(tutorialGroup.tutorialGroupSchedule);
        expect(component.courseId).toEqual(courseId);
    });

    it('should spit sessions into upcoming and past', () => {
        jest.spyOn(component, 'getCurrentDate').mockReturnValue(currentDate);

        fixture.detectChanges();
        expect(component.upcomingSessions).toHaveLength(1);
        expect(component.upcomingSessions).toEqual([upcomingSession]);
        expect(component.pastSessions).toHaveLength(1);
        expect(component.pastSessions).toEqual([pastSession]);
    });

    it('should load all when user action is performed on a row', () => {
        jest.spyOn(component, 'getCurrentDate').mockReturnValue(currentDate);
        fixture.detectChanges();

        const loadAllSpy = jest.spyOn(component, 'loadAll');

        const sessionRows = fixture.debugElement
            .queryAll(By.directive(TutorialGroupSessionRowStubComponent))
            .map((row) => row.componentInstance) as TutorialGroupSessionRowStubComponent[];
        expect(sessionRows).toHaveLength(2);
        const firstRow = sessionRows[0];
        firstRow.actionPerformed.emit();
        expect(loadAllSpy).toHaveBeenCalledOnce();
    });

    it('should navigate to create session page', fakeAsync(() => {
        fixture.detectChanges();
        const navigateSpy = jest.spyOn(router, 'navigateByUrl');

        const editButton = fixture.debugElement.nativeElement.querySelector('#create-session-button');
        editButton.click();

        fixture.whenStable().then(() => {
            expect(navigateSpy).toHaveBeenCalledOnce();
            expect(navigateSpy).toHaveBeenCalledWith(['/course-management', courseId, 'tutorial-groups-management', tutorialGroup.id, 'sessions', 'create']);
        });
    }));
});
