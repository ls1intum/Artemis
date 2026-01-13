import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { SortService } from 'app/shared/service/sort.service';
import dayjs from 'dayjs/esm';
import { TutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { TutorialGroupSessionsManagementComponent } from 'app/tutorialgroup/manage/tutorial-group-sessions/tutorial-group-sessions-management/tutorial-group-sessions-management.component';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { generateExampleTutorialGroupSession } from 'test/helpers/sample/tutorialgroup/tutorialGroupSessionExampleModels';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { generateExampleTutorialGroup } from 'test/helpers/sample/tutorialgroup/tutorialGroupExampleModels';
import { Course } from 'app/core/course/shared/entities/course.model';
import { CreateTutorialGroupSessionComponent } from 'app/tutorialgroup/manage/tutorial-group-sessions/crud/create-tutorial-group-session/create-tutorial-group-session.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import '@angular/localize/init';

describe('TutorialGroupSessionsManagement', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<TutorialGroupSessionsManagementComponent>;
    let component: TutorialGroupSessionsManagementComponent;

    const tutorialGroupId = 2;

    let tutorialGroupService: TutorialGroupsService;
    let getOneOfCourseSpy: ReturnType<typeof vi.spyOn>;

    let pastSession: TutorialGroupSession;
    let upcomingSession: TutorialGroupSession;
    let tutorialGroup: TutorialGroup;

    const course = {
        id: 1,
        timeZone: 'Europe/Berlin',
    } as Course;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TutorialGroupSessionsManagementComponent, OwlNativeDateTimeModule],
            providers: [
                MockProvider(TutorialGroupsService),
                MockProvider(AlertService),
                SortService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DialogService, useClass: MockDialogService },
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(CalendarService),
            ],
        }).compileComponents();

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
        getOneOfCourseSpy = vi.spyOn(tutorialGroupService, 'getOneOfCourse').mockReturnValue(of(new HttpResponse({ body: tutorialGroup })));

        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('tutorialGroupId', tutorialGroupId);
        component.open();
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
        expect(getOneOfCourseSpy).toHaveBeenCalledOnce();
        expect(getOneOfCourseSpy).toHaveBeenCalledWith(course.id!, tutorialGroupId);
        expect(component.tutorialGroup).toEqual(tutorialGroup);
        expect(component.tutorialGroupSchedule).toEqual(tutorialGroup.tutorialGroupSchedule);
        expect(component.course()).toEqual(course);
    });

    it('should open create session dialog', async () => {
        const mockCreateDialog = { open: vi.fn() } as unknown as CreateTutorialGroupSessionComponent;
        vi.spyOn(component, 'createSessionDialog').mockReturnValue(mockCreateDialog);

        const mockEvent = new MouseEvent('click');
        component.openCreateSessionDialog(mockEvent);

        await fixture.whenStable();
        expect(mockCreateDialog.open).toHaveBeenCalledOnce();
    });

    it('should call calendarService.refresh in loadAll', async () => {
        const calendarService = TestBed.inject(CalendarService);
        const refreshSpy = vi.spyOn(calendarService, 'reloadEvents').mockImplementation(() => {});

        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('tutorialGroupId', tutorialGroupId);

        const getOneOfCourseSpy = vi.spyOn(tutorialGroupService, 'getOneOfCourse').mockReturnValue(of(new HttpResponse({ body: tutorialGroup })));

        component.loadAll();
        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();

        expect(getOneOfCourseSpy).toHaveBeenCalledWith(course.id!, tutorialGroupId);
        expect(refreshSpy).toHaveBeenCalledOnce();
    });
});
