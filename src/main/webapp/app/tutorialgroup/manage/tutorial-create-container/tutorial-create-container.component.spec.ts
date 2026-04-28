import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { signal } from '@angular/core';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MockComponent } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { TutorialGroupApiService } from 'app/openapi/api/tutorialGroupApi.service';
import { TutorialCreateContainerComponent } from 'app/tutorialgroup/manage/tutorial-create-container/tutorial-create-container.component';
import { CreateTutorialGroupEvent, TutorialCreateOrEditComponent } from 'app/tutorialgroup/manage/tutorial-create-or-edit/tutorial-create-or-edit.component';
import { TutorialGroupTutorsService } from 'app/tutorialgroup/manage/service/tutorial-group-tutors.service';
import { TutorialGroupTutor } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { LoadingIndicatorOverlayComponent } from 'app/shared/loading-indicator-overlay/loading-indicator-overlay.component';
import { LoadingIndicatorOverlayStubComponent } from 'test/helpers/stubs/tutorialgroup/loading-indicator-overlay-stub.component';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { mockedActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';

describe('TutorialCreateContainerComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<TutorialCreateContainerComponent>;
    let component: TutorialCreateContainerComponent;

    let tutorialGroupApiService: {
        createTutorialGroup: ReturnType<typeof vi.fn>;
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
            createTutorialGroup: vi.fn(),
        };
        tutorialGroupTutorsService = {
            tutors: signal<TutorialGroupTutor[]>([
                { id: 11, nameAndLogin: 'Ada Lovelace (ada)' },
                { id: 12, nameAndLogin: 'Grace Hopper (grace)' },
            ]),
            isLoading: signal(false),
            loadTutors: vi.fn(),
        };
        alertService = new MockAlertService();
        vi.spyOn(alertService, 'addErrorAlert');

        await TestBed.configureTestingModule({
            imports: [TutorialCreateContainerComponent],
            providers: [
                mockedActivatedRoute({}, {}, {}, { courseId: '2' }),
                { provide: Router, useClass: MockRouter },
                { provide: TutorialGroupApiService, useValue: tutorialGroupApiService },
                { provide: TutorialGroupTutorsService, useValue: tutorialGroupTutorsService },
                { provide: AlertService, useValue: alertService },
            ],
        })
            .overrideComponent(TutorialCreateContainerComponent, {
                remove: { imports: [TutorialCreateOrEditComponent, LoadingIndicatorOverlayComponent] },
                add: { imports: [MockComponent(TutorialCreateOrEditComponent), LoadingIndicatorOverlayStubComponent] },
            })
            .compileComponents();

        activatedRoute = TestBed.inject(ActivatedRoute);
        activatedRoute.snapshot = { paramMap: convertToParamMap({}) } as ActivatedRoute['snapshot'];
        if (activatedRoute.parent) {
            activatedRoute.parent.snapshot = { paramMap: convertToParamMap({ courseId: '2' }) } as ActivatedRoute['snapshot'];
        }
    });

    afterEach(() => {
        vi.clearAllMocks();
        vi.restoreAllMocks();
    });

    function createCreateEvent(): CreateTutorialGroupEvent {
        return {
            courseId: 2,
            createTutorialGroupDTO: {
                title: 'TG 1',
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
        fixture = TestBed.createComponent(TutorialCreateContainerComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    }

    it('should load tutors on init', () => {
        createComponent();

        expect(component.courseId()).toBe(2);
        expect(tutorialGroupTutorsService.loadTutors).toHaveBeenCalledOnce();
        expect(tutorialGroupTutorsService.loadTutors).toHaveBeenCalledWith(2);
        expect(component.tutors()).toEqual(tutorialGroupTutorsService.tutors());
    });

    it('should create the tutorial group and navigate to the created tutorial group on success', async () => {
        const create$ = new Subject<number>();
        const createEvent = createCreateEvent();
        const router = TestBed.inject(Router);

        tutorialGroupApiService.createTutorialGroup.mockReturnValue(create$.asObservable());

        createComponent();

        component.createTutorialGroup(createEvent);

        expect(tutorialGroupApiService.createTutorialGroup).toHaveBeenCalledOnce();
        expect(tutorialGroupApiService.createTutorialGroup).toHaveBeenCalledWith(2, createEvent.createTutorialGroupDTO);
        expect(component.isTutorialGroupLoading()).toBe(true);

        create$.next(42);
        create$.complete();
        await fixture.whenStable();

        expect(component.isTutorialGroupLoading()).toBe(false);
        expect(router.navigate).toHaveBeenCalledWith(['..', 42], { relativeTo: activatedRoute });
        expect(alertService.addErrorAlert).not.toHaveBeenCalled();
    });

    it('should show an error alert and stop loading when creating the tutorial group fails', () => {
        const create$ = new Subject<void>();
        const createEvent = createCreateEvent();
        const router = TestBed.inject(Router);

        tutorialGroupApiService.createTutorialGroup.mockReturnValue(create$.asObservable());

        createComponent();

        component.createTutorialGroup(createEvent);

        expect(component.isTutorialGroupLoading()).toBe(true);

        create$.error(new Error('network error'));

        expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.pages.createOrEditTutorialGroup.networkError.createGroup');
        expect(component.isTutorialGroupLoading()).toBe(false);
        expect(router.navigate).not.toHaveBeenCalled();
    });
});
