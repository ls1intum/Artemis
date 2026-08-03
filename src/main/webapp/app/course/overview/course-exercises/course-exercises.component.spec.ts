import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { BehaviorSubject, of } from 'rxjs';
import { Course } from 'app/course/shared/entities/course.model';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { MockHasAnyAuthorityDirective } from 'test/helpers/mocks/directive/mock-has-any-authority.directive';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { CourseExercisesComponent } from 'app/course/overview/course-exercises/course-exercises.component';
import { CourseExerciseRowComponent } from 'app/course/overview/course-exercises/course-exercise-row/course-exercise-row.component';
import { SidePanelComponent } from 'app/shared-ui/side-panel/side-panel.component';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import dayjs from 'dayjs/esm';
import { MockTranslateValuesDirective } from 'test/helpers/mocks/directive/mock-translate-values.directive';
import { SortByDirective } from 'app/foundation/sort/directive/sort-by.directive';
import { SortDirective } from 'app/foundation/sort/directive/sort.directive';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { UMLDiagramType } from '@tumaet/apollon';
import { SidebarComponent } from 'app/course/sidebar/sidebar.component';
import { SearchFilterPipe } from 'app/foundation/pipes/search-filter.pipe';
import { SearchFilterComponent } from 'app/shared-ui/search-filter/search-filter.component';
import { By } from '@angular/platform-browser';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { MockMetisConversationService } from 'test/helpers/mocks/service/mock-metis-conversation.service';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { InitializationState, Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';

describe('CourseExercisesComponent', () => {
    let fixture: ComponentFixture<CourseExercisesComponent>;
    let component: CourseExercisesComponent;
    let courseStorageService: CourseStorageService;
    let exerciseService: ExerciseService;
    let participationWebsocketBehaviorSubject: BehaviorSubject<Participation | undefined>;

    let course: Course;
    let exercise: Exercise;
    let exerciseServiceStub: ReturnType<typeof vi.spyOn>;

    const parentRoute = { params: of({ courseId: 123 }) } as any as ActivatedRoute;
    const queryParamsSubject = new BehaviorSubject({ exercises: '', isMultiLaunch: 'false' });
    const route = {
        parent: parentRoute,
        queryParams: queryParamsSubject,
    } as any as ActivatedRoute;

    beforeEach(async () => {
        participationWebsocketBehaviorSubject = new BehaviorSubject<Participation | undefined>(undefined);
        TestBed.configureTestingModule({
            imports: [
                FormsModule,
                RouterModule.forRoot([]),
                MockModule(ReactiveFormsModule),
                MockDirective(TranslateDirective),
                FaIconComponent,
                CourseExercisesComponent,
                SidebarComponent,
                MockComponent(CourseExerciseRowComponent),
                MockComponent(SidePanelComponent),
                MockDirective(MockHasAnyAuthorityDirective),
                MockDirective(SortByDirective),
                TranslatePipeMock,
                MockDirective(SortDirective),
                MockPipe(ArtemisDatePipe),
                MockDirective(DeleteButtonDirective),
                MockTranslateValuesDirective,
                MockPipe(SearchFilterPipe),
                MockComponent(SearchFilterComponent),
            ],
            providers: [
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: MetisConversationService, useClass: MockMetisConversationService },
                {
                    provide: ParticipationWebsocketService,
                    useValue: {
                        addParticipation: vi.fn(),
                        getParticipationsForExercise: vi.fn(),
                        subscribeForParticipationChanges: () => participationWebsocketBehaviorSubject,
                        subscribeForLatestResultOfParticipation: vi.fn(() => new BehaviorSubject<Result | undefined>(undefined)),
                        unsubscribeForLatestResultOfParticipation: vi.fn(),
                        notifyAllResultSubscribers: vi.fn(),
                        resetLocalCache: vi.fn(),
                    },
                },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        });
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(CourseExercisesComponent);
        component = fixture.componentInstance;
        courseStorageService = TestBed.inject(CourseStorageService);
        exerciseService = TestBed.inject(ExerciseService);

        (component as any)._sidebarData.set({ groupByCategory: true, sidebarType: 'exercise', storageId: 'exercise' });
        course = new Course();
        course.id = 123;
        exercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined) as Exercise;
        exercise.id = 456;
        exercise.dueDate = dayjs('2021-01-13T16:11:00+01:00').add(1, 'days');
        exercise.releaseDate = dayjs('2021-01-13T16:11:00+01:00').subtract(1, 'days');
        course.exercises = [exercise];
        vi.spyOn(courseStorageService, 'subscribeToCourseUpdates').mockReturnValue(of(course));
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue(course);
        exerciseServiceStub = vi.spyOn(exerciseService, 'find').mockReturnValue(
            of(
                new HttpResponse({
                    body: exercise,
                }),
            ),
        );

        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        // Ensure course is set
        (component as any)._course.set(course);
        TestBed.tick();
        expect(component.course()).toEqual(course);
        // Component should be properly initialized with the course
        expect(component.courseId()).toBe(course.id);
    });

    it('should display sidebar when course is provided', () => {
        // Ensure course is set
        (component as any)._course.set(course);
        TestBed.tick();
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('jhi-sidebar')).not.toBeNull();
    });

    it('should toggle sidebar collapsed state based on isCollapsed property', () => {
        // Ensure course is set and LTI is not shown
        (component as any)._course.set(course);
        (component as any)._isShownViaLti.set(false);
        (component as any)._isCollapsed.set(true);
        TestBed.tick();
        fixture.changeDetectorRef.detectChanges();
        expect(fixture.nativeElement.querySelector('.sidebar-collapsed')).not.toBeNull();

        (component as any)._isCollapsed.set(false);
        TestBed.tick();
        fixture.changeDetectorRef.detectChanges();
        expect(fixture.nativeElement.querySelector('.sidebar-collapsed')).toBeNull();
    });

    it('should toggle isNavbarCollapsed when toggleCollapseState is called', () => {
        (component as any)._isCollapsed.set(false);
        TestBed.tick();

        component.toggleSidebar();
        TestBed.tick();
        expect(component.isCollapsed()).toBe(true);

        component.toggleSidebar();
        TestBed.tick();
        expect(component.isCollapsed()).toBe(false);
    });

    it('should set the page title for the sidebar header', () => {
        component.setPageTitle('overview.exercises');

        expect(component.pageTitle()).toBe('overview.exercises');
    });

    it('should toggle the sidebar from a child sidebar control', () => {
        (component as any)._isCollapsed.set(false);

        component.toggleSidebar();

        expect(component.isCollapsed()).toBe(true);
    });

    it('should pass the sidebar toggle state to the sidebar', () => {
        (component as any)._course.set(course);
        (component as any)._isShownViaLti.set(false);
        (component as any)._isCollapsed.set(false);
        component.setPageTitle('overview.exercises');
        TestBed.tick();
        fixture.changeDetectorRef.detectChanges();

        const sidebarComponent = fixture.debugElement.query(By.directive(SidebarComponent)).componentInstance as SidebarComponent;

        expect(sidebarComponent.showSidebarToggle()).toBe(true);
        expect(sidebarComponent.isSidebarCollapsed()).toBe(false);
    });

    it('should provide sidebar toggle state to active exercise details', () => {
        const exerciseDetails = { setSidebarToggle: vi.fn() };
        (component as any)._isCollapsed.set(false);

        component.onSubRouteActivate(exerciseDetails);
        TestBed.tick();

        expect(exerciseDetails.setSidebarToggle).toHaveBeenCalledWith(false, expect.any(Function));
    });

    it('should display "Please Select an Exercise" when no exercise is selected', () => {
        // Ensure course is set
        (component as any)._course.set(course);
        (component as any)._exerciseSelected.set(false);
        TestBed.tick();
        fixture.changeDetectorRef.detectChanges();
        const noExerciseElement = fixture.debugElement.query(By.css('[jhiTranslate$=selectExercise]'));
        expect(noExerciseElement).toBeTruthy();
        expect(noExerciseElement.nativeElement.getAttribute('jhiTranslate')).toBe('artemisApp.courseOverview.exerciseDetails.selectExercise');
    });

    it('should display the exercise details when an exercise is selected', () => {
        // Ensure course is set
        (component as any)._course.set(course);
        (component as any)._exerciseSelected.set(true);
        TestBed.tick();
        fixture.changeDetectorRef.detectChanges();
        expect(fixture.nativeElement.querySelector('router-outlet')).not.toBeNull();
    });

    it('should call exerciseService if multiLaunchExercises are present', () => {
        (component as any)._isMultiLaunch.set(true);
        (component as any)._multiLaunchExerciseIDs.set([1, 2]);

        component.prepareSidebarData();

        expect(exerciseServiceStub).toHaveBeenCalledTimes(2);
        expect(exerciseServiceStub).toHaveBeenCalledWith(1);
        expect(exerciseServiceStub).toHaveBeenCalledWith(2);
    });

    it('should update sidebar participation data when a websocket participation change is received', () => {
        const initialParticipation = new StudentParticipation();
        initialParticipation.id = 1;
        initialParticipation.testRun = false;
        initialParticipation.initializationState = InitializationState.INITIALIZED;
        initialParticipation.exercise = exercise;
        exercise.studentParticipations = [initialParticipation];
        (component as any)._course.set(course);
        component.processExercises(course.exercises!);

        const result = new Result();
        result.id = 99;
        const updatedParticipation = new StudentParticipation();
        updatedParticipation.id = initialParticipation.id;
        updatedParticipation.testRun = false;
        updatedParticipation.initializationState = InitializationState.FINISHED;
        updatedParticipation.exercise = exercise;
        updatedParticipation.submissions = [{ id: 12, participation: updatedParticipation, results: [result] } as Submission];

        participationWebsocketBehaviorSubject.next(updatedParticipation);

        const ungroupedParticipation = component.sidebarData()?.ungroupedData?.[0].studentParticipation;
        expect(ungroupedParticipation?.initializationState).toBe(InitializationState.FINISHED);
        expect(ungroupedParticipation?.submissions?.[0].results?.[0]).toBe(result);

        const groupedItems = Object.values(component.sidebarData()?.groupedData ?? {}).flatMap((group) => group.entityData);
        const groupedParticipation = groupedItems.find((item) => item.id === exercise.id)?.studentParticipation;
        expect(groupedParticipation?.initializationState).toBe(InitializationState.FINISHED);
        expect(groupedParticipation?.submissions?.[0].results?.[0]).toBe(result);
    });

    it('should preserve an existing sidebar result when another exercise receives a websocket participation change', () => {
        const textExercise = {
            id: 789,
            title: 'Text exercise',
            type: ExerciseType.TEXT,
            dueDate: dayjs('2021-01-13T16:11:00+01:00').add(1, 'days'),
        } as Exercise;
        const textResult = new Result();
        textResult.id = 100;
        const textParticipation = new StudentParticipation();
        textParticipation.id = 10;
        textParticipation.testRun = false;
        textParticipation.initializationState = InitializationState.FINISHED;
        textParticipation.exercise = textExercise;
        textParticipation.submissions = [{ id: 20, participation: textParticipation, results: [textResult] } as Submission];
        textExercise.studentParticipations = [textParticipation];

        const modelingParticipation = new StudentParticipation();
        modelingParticipation.id = 11;
        modelingParticipation.testRun = false;
        modelingParticipation.initializationState = InitializationState.INITIALIZED;
        modelingParticipation.exercise = exercise;
        exercise.studentParticipations = [modelingParticipation];

        course.exercises = [textExercise, exercise];
        (component as any)._course.set(course);
        component.processExercises(course.exercises);

        const staleTextExercise = {
            ...textExercise,
            studentParticipations: [
                {
                    id: textParticipation.id,
                    testRun: false,
                    initializationState: InitializationState.INITIALIZED,
                    exercise: textExercise,
                } as StudentParticipation,
            ],
        } as Exercise;
        (component as any)._sortedExercises.set([staleTextExercise, exercise]);

        const modelingResult = new Result();
        modelingResult.id = 101;
        const updatedModelingParticipation = new StudentParticipation();
        updatedModelingParticipation.id = modelingParticipation.id;
        updatedModelingParticipation.testRun = false;
        updatedModelingParticipation.initializationState = InitializationState.FINISHED;
        updatedModelingParticipation.exercise = exercise;
        updatedModelingParticipation.submissions = [{ id: 21, participation: updatedModelingParticipation, results: [modelingResult] } as Submission];

        participationWebsocketBehaviorSubject.next(updatedModelingParticipation);

        const sidebarItems = component.sidebarData()?.ungroupedData ?? [];
        const textSidebarParticipation = sidebarItems.find((item) => item.id === textExercise.id)?.studentParticipation;
        expect(textSidebarParticipation?.initializationState).toBe(InitializationState.FINISHED);
        expect(textSidebarParticipation?.submissions?.[0].results?.[0]).toBe(textResult);

        const modelingSidebarParticipation = sidebarItems.find((item) => item.id === exercise.id)?.studentParticipation;
        expect(modelingSidebarParticipation?.initializationState).toBe(InitializationState.FINISHED);
        expect(modelingSidebarParticipation?.submissions?.[0].results?.[0]).toBe(modelingResult);
    });

    it('should not replace a different participation with the same test run flag', () => {
        const firstParticipation = new StudentParticipation();
        firstParticipation.id = 1;
        firstParticipation.testRun = false;
        firstParticipation.initializationState = InitializationState.FINISHED;
        firstParticipation.exercise = exercise;

        const secondParticipation = new StudentParticipation();
        secondParticipation.id = 2;
        secondParticipation.testRun = false;
        secondParticipation.initializationState = InitializationState.INITIALIZED;
        secondParticipation.exercise = exercise;

        exercise.studentParticipations = [firstParticipation, secondParticipation];
        (component as any)._course.set(course);
        component.processExercises(course.exercises!);

        const updatedSecondParticipation = new StudentParticipation();
        updatedSecondParticipation.id = secondParticipation.id;
        updatedSecondParticipation.testRun = false;
        updatedSecondParticipation.initializationState = InitializationState.FINISHED;
        updatedSecondParticipation.exercise = exercise;

        participationWebsocketBehaviorSubject.next(updatedSecondParticipation);

        const participations = (component as any)._sortedExercises()[0].studentParticipations as StudentParticipation[];
        expect(participations).toHaveLength(2);
        expect(participations[0].id).toBe(firstParticipation.id);
        expect(participations[0].initializationState).toBe(InitializationState.FINISHED);
        expect(participations[1].id).toBe(secondParticipation.id);
        expect(participations[1].initializationState).toBe(InitializationState.FINISHED);
    });
});
