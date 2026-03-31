import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { signal } from '@angular/core';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MockComponent } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { TutorialGroupApiService } from 'app/openapi/api/tutorialGroupApi.service';
import { TutorialEditContainerComponent } from 'app/tutorialgroup/manage/tutorial-edit-container/tutorial-edit-container.component';
import { TutorialCreateOrEditComponent, UpdateTutorialGroupEvent } from 'app/tutorialgroup/manage/tutorial-create-or-edit/tutorial-create-or-edit.component';
import { TutorialGroupTutorsService } from 'app/tutorialgroup/manage/service/tutorial-group-tutors.service';
import { TutorialGroupDetailData, TutorialGroupTutor } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupCourseAndGroupService } from 'app/tutorialgroup/shared/service/tutorial-group-course-and-group.service';
import { LoadingIndicatorOverlayComponent } from 'app/shared/loading-indicator-overlay/loading-indicator-overlay.component';
import { LoadingIndicatorOverlayStubComponent } from 'test/helpers/stubs/tutorialgroup/loading-indicator-overlay-stub.component';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { mockedActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { TutorialGroupSchedule } from 'app/openapi/model/tutorialGroupSchedule';

describe('TutorialEditContainerComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<TutorialEditContainerComponent>;
    let component: TutorialEditContainerComponent;

    let tutorialGroupApiService: {
        updateTutorialGroup: ReturnType<typeof vi.fn>;
        getTutorialGroupSchedule: ReturnType<typeof vi.fn>;
    };
    let tutorialGroupCourseAndGroupService: {
        tutorialGroup: ReturnType<typeof signal<TutorialGroupDetailData | undefined>>;
        isTutorialGroupLoading: ReturnType<typeof signal<boolean>>;
        fetchTutorialGroup: ReturnType<typeof vi.fn>;
    };
    let tutorialGroupTutorsService: {
        tutors: ReturnType<typeof signal<TutorialGroupTutor[]>>;
        isLoading: ReturnType<typeof signal<boolean>>;
        loadTutors: ReturnType<typeof vi.fn>;
    };
    let alertService: MockAlertService;
    let activatedRoute: ActivatedRoute;

    beforeEach(async () => {
        tutorialGroupApiService = {
            updateTutorialGroup: vi.fn(),
            getTutorialGroupSchedule: vi.fn(),
        };
        tutorialGroupCourseAndGroupService = {
            tutorialGroup: signal<TutorialGroupDetailData | undefined>(undefined),
            isTutorialGroupLoading: signal(false),
            fetchTutorialGroup: vi.fn(),
        };
        tutorialGroupTutorsService = {
            tutors: signal<TutorialGroupTutor[]>([]),
            isLoading: signal(false),
            loadTutors: vi.fn(),
        };
        alertService = new MockAlertService();
        vi.spyOn(alertService, 'addErrorAlert');

        await TestBed.configureTestingModule({
            imports: [TutorialEditContainerComponent],
            providers: [
                mockedActivatedRoute({ tutorialGroupId: '17' }, {}, {}, { courseId: '2' }),
                { provide: Router, useClass: MockRouter },
                { provide: TutorialGroupApiService, useValue: tutorialGroupApiService },
                { provide: TutorialGroupCourseAndGroupService, useValue: tutorialGroupCourseAndGroupService },
                { provide: TutorialGroupTutorsService, useValue: tutorialGroupTutorsService },
                { provide: AlertService, useValue: alertService },
            ],
        })
            .overrideComponent(TutorialEditContainerComponent, {
                remove: { imports: [TutorialCreateOrEditComponent, LoadingIndicatorOverlayComponent] },
                add: { imports: [MockComponent(TutorialCreateOrEditComponent), LoadingIndicatorOverlayStubComponent] },
            })
            .compileComponents();

        activatedRoute = TestBed.inject(ActivatedRoute);
        activatedRoute.snapshot = { paramMap: convertToParamMap({ tutorialGroupId: '17' }) } as ActivatedRoute['snapshot'];
        if (activatedRoute.parent) {
            activatedRoute.parent.snapshot = { paramMap: convertToParamMap({ courseId: '2' }) } as ActivatedRoute['snapshot'];
        }
    });

    afterEach(() => {
        vi.clearAllMocks();
        vi.restoreAllMocks();
    });

    function createTutorialGroup(): TutorialGroupDetailData {
        return new TutorialGroupDetailData({
            id: 17,
            title: 'TG 1',
            language: 'English',
            isOnline: false,
            sessions: [],
            tutorName: 'Grace Hopper',
            tutorLogin: 'grace',
            tutorId: 12,
            tutorImageUrl: undefined,
            capacity: 15,
            campus: 'Garching',
            additionalInformation: 'Bring laptop',
            groupChannelId: undefined,
            tutorChatId: undefined,
        });
    }

    function createSchedule(): TutorialGroupSchedule {
        return {
            firstSessionStart: '2026-04-20T10:15:00',
            firstSessionEnd: '2026-04-20T11:45:00',
            repetitionFrequency: 2,
            tutorialPeriodEnd: '2026-07-20',
            location: 'Room 101',
        };
    }

    function createUpdateEvent(): UpdateTutorialGroupEvent {
        return {
            courseId: 2,
            tutorialGroupId: 17,
            updateTutorialGroupDTO: {
                title: 'Updated TG 1',
                tutorId: 12,
                language: 'English',
                isOnline: false,
                campus: 'Garching',
                capacity: 20,
                additionalInformation: 'Bring worksheet',
            },
        };
    }

    function createComponent() {
        fixture = TestBed.createComponent(TutorialEditContainerComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    }

    it('should load tutors, fetch the tutorial group when missing, and load the schedule on init', () => {
        const schedule$ = new Subject<TutorialGroupSchedule>();
        const schedule = createSchedule();
        tutorialGroupApiService.getTutorialGroupSchedule.mockReturnValue(schedule$.asObservable());

        createComponent();

        expect(tutorialGroupTutorsService.loadTutors).toHaveBeenCalledOnce();
        expect(tutorialGroupTutorsService.loadTutors).toHaveBeenCalledWith(2);
        expect(tutorialGroupCourseAndGroupService.fetchTutorialGroup).toHaveBeenCalledOnce();
        expect(tutorialGroupCourseAndGroupService.fetchTutorialGroup).toHaveBeenCalledWith(2, 17);
        expect(tutorialGroupApiService.getTutorialGroupSchedule).toHaveBeenCalledOnce();
        expect(tutorialGroupApiService.getTutorialGroupSchedule).toHaveBeenCalledWith(2, 17);
        expect(component.isScheduleLoading()).toBe(true);

        schedule$.next(schedule);
        schedule$.complete();

        expect(component.schedule()).toEqual(schedule);
        expect(component.isScheduleLoading()).toBe(false);
    });

    it('should not refetch the tutorial group on init when it is already available', () => {
        tutorialGroupCourseAndGroupService.tutorialGroup.set(createTutorialGroup());
        tutorialGroupApiService.getTutorialGroupSchedule.mockReturnValue(of(createSchedule()));

        createComponent();

        expect(tutorialGroupTutorsService.loadTutors).toHaveBeenCalledWith(2);
        expect(tutorialGroupCourseAndGroupService.fetchTutorialGroup).not.toHaveBeenCalled();
        expect(tutorialGroupApiService.getTutorialGroupSchedule).toHaveBeenCalledWith(2, 17);
        expect(component.schedule()).toEqual(createSchedule());
    });

    it('should show an error alert when loading the schedule fails on init', () => {
        const schedule$ = new Subject<TutorialGroupSchedule>();
        tutorialGroupApiService.getTutorialGroupSchedule.mockReturnValue(schedule$.asObservable());

        createComponent();

        expect(component.isScheduleLoading()).toBe(true);

        schedule$.error(new Error('network error'));

        expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.pages.createOrEditTutorialGroup.networkError.fetchSchedule');
        expect(component.schedule()).toBeUndefined();
        expect(component.isScheduleLoading()).toBe(false);
    });

    it('should update the tutorial group, refresh it, and navigate back on success', async () => {
        const update$ = new Subject<void>();
        const updateEvent = createUpdateEvent();
        const router = TestBed.inject(Router);

        tutorialGroupApiService.updateTutorialGroup.mockReturnValue(update$.asObservable());
        tutorialGroupApiService.getTutorialGroupSchedule.mockReturnValue(of(createSchedule()));

        createComponent();
        tutorialGroupCourseAndGroupService.fetchTutorialGroup.mockClear();

        component.updateTutorialGroup(updateEvent);

        expect(tutorialGroupApiService.updateTutorialGroup).toHaveBeenCalledOnce();
        expect(tutorialGroupApiService.updateTutorialGroup).toHaveBeenCalledWith(2, 17, updateEvent.updateTutorialGroupDTO);
        expect(tutorialGroupCourseAndGroupService.isTutorialGroupLoading()).toBe(true);

        update$.next();
        update$.complete();
        await fixture.whenStable();

        expect(tutorialGroupCourseAndGroupService.isTutorialGroupLoading()).toBe(false);
        expect(tutorialGroupCourseAndGroupService.fetchTutorialGroup).toHaveBeenCalledOnce();
        expect(tutorialGroupCourseAndGroupService.fetchTutorialGroup).toHaveBeenCalledWith(2, 17);
        expect(router.navigate).toHaveBeenCalledWith(['..'], { relativeTo: activatedRoute });
        expect(alertService.addErrorAlert).not.toHaveBeenCalled();
    });

    it('should show an error alert and stop loading when updating the tutorial group fails', () => {
        const update$ = new Subject<void>();
        const updateEvent = createUpdateEvent();
        const router = TestBed.inject(Router);

        tutorialGroupApiService.updateTutorialGroup.mockReturnValue(update$.asObservable());
        tutorialGroupApiService.getTutorialGroupSchedule.mockReturnValue(of(createSchedule()));

        createComponent();
        tutorialGroupCourseAndGroupService.fetchTutorialGroup.mockClear();

        component.updateTutorialGroup(updateEvent);

        expect(tutorialGroupCourseAndGroupService.isTutorialGroupLoading()).toBe(true);

        update$.error(new Error('network error'));

        expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.pages.createOrEditTutorialGroup.networkError.updateGroup');
        expect(tutorialGroupCourseAndGroupService.isTutorialGroupLoading()).toBe(false);
        expect(tutorialGroupCourseAndGroupService.fetchTutorialGroup).not.toHaveBeenCalled();
        expect(router.navigate).not.toHaveBeenCalled();
    });

    it('should show an error alert when loading the schedule fails immediately', () => {
        tutorialGroupApiService.getTutorialGroupSchedule.mockReturnValue(throwError(() => new Error('network error')));

        createComponent();

        expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.pages.createOrEditTutorialGroup.networkError.fetchSchedule');
        expect(component.isScheduleLoading()).toBe(false);
    });
});
