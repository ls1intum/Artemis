import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { GuardsCheckStart, NavigationEnd, Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { ApplicationRef, Component, DestroyRef, inject, viewChild } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/course/shared/entities/course.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { InitializationState, ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import dayjs from 'dayjs/esm';
import { filter, take } from 'rxjs/operators';
import { Subject, firstValueFrom } from 'rxjs';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { UMLDiagramType } from '@tumaet/apollon';
import { provideHttpClient } from '@angular/common/http';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { StartPracticeModeButtonComponent } from 'app/course/overview/exercise-details/start-practice-mode-button/start-practice-mode-button.component';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockProvider } from 'ng-mocks';

describe('Course Management Service', () => {
    let service: CourseExerciseService;
    let httpMock: HttpTestingController;
    let exerciseId: number;
    let course: Course;
    let exercises: Exercise[];
    let returnedFromService: any;
    let programmingExercise: ProgrammingExercise;
    let modelingExercise: ModelingExercise;

    let textExercise: TextExercise;

    let fileUploadExercise: FileUploadExercise;
    let releaseDate: dayjs.Dayjs;
    let dueDate: dayjs.Dayjs;
    let assessmentDueDate: dayjs.Dayjs;

    let releaseDateString: string;
    let dueDateString: string;
    let assessmentDueDateString: string;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: Router, useClass: MockRouter },
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        service = TestBed.inject(CourseExerciseService);
        httpMock = TestBed.inject(HttpTestingController);
        exerciseId = 123;

        course = new Course();
        course.id = 1234;
        course.title = 'testTitle';
        const releaseDateRaw = new Date();
        releaseDateRaw.setMonth(3);
        releaseDate = dayjs(releaseDateRaw);
        const dueDateRaw = new Date();
        dueDateRaw.setMonth(6);
        dueDate = dayjs(dueDateRaw);
        const assessmentDueDateRaw = new Date();
        assessmentDueDate = dayjs(assessmentDueDateRaw);

        releaseDateString = releaseDateRaw.toISOString();
        dueDateString = dueDateRaw.toISOString();
        assessmentDueDateString = assessmentDueDateRaw.toISOString();

        modelingExercise = new ModelingExercise(UMLDiagramType.ComponentDiagram, undefined, undefined);
        modelingExercise.releaseDate = releaseDate;
        modelingExercise.dueDate = dueDate;
        modelingExercise.assessmentDueDate = assessmentDueDate;
        modelingExercise = JSON.parse(JSON.stringify(modelingExercise));

        programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.releaseDate = releaseDate;
        programmingExercise.dueDate = dueDate;
        programmingExercise.assessmentDueDate = assessmentDueDate;
        programmingExercise = JSON.parse(JSON.stringify(programmingExercise));

        textExercise = new TextExercise(course, undefined);
        textExercise.releaseDate = releaseDate;
        textExercise.dueDate = dueDate;
        textExercise.assessmentDueDate = assessmentDueDate;
        textExercise = JSON.parse(JSON.stringify(textExercise));

        fileUploadExercise = new FileUploadExercise(course, undefined);
        fileUploadExercise.releaseDate = releaseDate;
        fileUploadExercise.dueDate = dueDate;
        fileUploadExercise.assessmentDueDate = assessmentDueDate;
        fileUploadExercise = JSON.parse(JSON.stringify(fileUploadExercise));

        exercises = [];
        course.exercises = exercises;
        returnedFromService = { ...course };
    });

    const expectDateConversionToBeDone = (exerciseToCheck: Exercise, withoutAssessmentDueDate?: boolean) => {
        expect(dayjs.isDayjs(exerciseToCheck.releaseDate)).toBe(true);
        expect(exerciseToCheck.releaseDate?.toISOString()).toBe(releaseDateString);
        expect(dayjs.isDayjs(exerciseToCheck.dueDate)).toBe(true);
        expect(exerciseToCheck.dueDate?.toISOString()).toBe(dueDateString);
        if (!withoutAssessmentDueDate) {
            expect(dayjs.isDayjs(exerciseToCheck.assessmentDueDate)).toBe(true);
            expect(exerciseToCheck.assessmentDueDate?.toISOString()).toBe(assessmentDueDateString);
        }
    };

    const requestAndExpectDateConversion = (
        method: string,
        url: string,
        flushedObject: any = returnedFromService,
        exerciseToCheck: Exercise,
        withoutAssessmentDueDate?: boolean,
    ) => {
        const req = httpMock.expectOne({ method, url });
        req.flush(flushedObject);
        expectDateConversionToBeDone(exerciseToCheck, withoutAssessmentDueDate);
    };

    it('should find all programming exercises', () => {
        returnedFromService = [programmingExercise];
        service
            .findAllProgrammingExercisesForCourse(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual([programmingExercise]));

        requestAndExpectDateConversion('GET', `api/programming/courses/${course.id}/programming-exercises`, returnedFromService, programmingExercise);
    });

    it('should find all modeling exercises', () => {
        returnedFromService = [modelingExercise];
        service
            .findAllModelingExercisesForCourse(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual([modelingExercise]));

        requestAndExpectDateConversion('GET', `api/modeling/courses/${course.id}/modeling-exercises`, returnedFromService, modelingExercise);
    });

    it('should find all text exercises', () => {
        returnedFromService = [textExercise];
        service
            .findAllTextExercisesForCourse(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual([textExercise]));

        requestAndExpectDateConversion('GET', `api/text/courses/${course.id}/text-exercises`, returnedFromService, textExercise);
    });

    it('should find all file upload exercises', () => {
        returnedFromService = [fileUploadExercise];
        service
            .findAllFileUploadExercisesForCourse(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual([fileUploadExercise]));

        requestAndExpectDateConversion('GET', `api/fileupload/courses/${course.id}/file-upload-exercises`, returnedFromService, fileUploadExercise);
    });

    it('should start exercise', () => {
        const participationId = 12345;
        const participation = new StudentParticipation();
        participation.id = participationId;
        participation.exercise = programmingExercise;
        returnedFromService = { ...participation };
        const expected = Object.assign(
            {
                initializationDate: undefined,
            },
            participation,
        );
        vi.spyOn(TestBed.inject(ProfileService), 'getProfileInfo').mockReturnValue({ buildPlanURLTemplate: 'testci.fake' } as ProfileInfo);

        service
            .startExercise(exerciseId)
            .pipe(take(1))
            .subscribe((res) => expect(res).toEqual(expected));

        requestAndExpectDateConversion('POST', `api/exercise/exercises/${exerciseId}/participations`, returnedFromService, participation.exercise, true);
        expect(programmingExercise.studentParticipations?.[0]?.id).toBe(participationId);
    });

    it.each([true, false])('should start practice', (useGradedParticipation: boolean) => {
        const participationId = 12345;
        const participation = new StudentParticipation();
        participation.id = participationId;
        participation.exercise = programmingExercise;
        returnedFromService = { ...participation };
        const expected = Object.assign(
            {
                initializationDate: undefined,
            },
            participation,
        );
        vi.spyOn(TestBed.inject(ProfileService), 'getProfileInfo').mockReturnValue({ buildPlanURLTemplate: 'testci.fake' } as ProfileInfo);

        service
            .startPractice(exerciseId, useGradedParticipation, programmingExercise)
            .pipe(take(1))
            .subscribe((res) => expect(res).toEqual(expected));

        requestAndExpectDateConversion(
            'POST',
            `api/exercise/exercises/${exerciseId}/participations/practice?useGradedParticipation=${useGradedParticipation}`,
            returnedFromService,
            participation.exercise,
            true,
        );
        expect(programmingExercise.studentParticipations?.[0]?.id).toBe(participationId);
    });

    it('should resume programming exercise', () => {
        const participationId = 12345;
        const participation = new StudentParticipation();
        participation.id = participationId;
        participation.exercise = programmingExercise;
        returnedFromService = { ...participation };
        const expected = Object.assign(
            {
                initializationDate: undefined,
            },
            participation,
        );
        vi.spyOn(TestBed.inject(ProfileService), 'getProfileInfo').mockReturnValue({ buildPlanURLTemplate: 'testci.fake' } as ProfileInfo);

        service
            .resumeProgrammingExercise(exerciseId, participationId)
            .pipe(take(1))
            .subscribe((res) => expect(res).toEqual(expected));

        requestAndExpectDateConversion(
            'PUT',
            `api/exercise/exercises/${exerciseId}/participations/${participationId}/resume-programming-participation`,
            returnedFromService,
            participation.exercise,
            true,
        );
        expect(programmingExercise.studentParticipations?.[0]?.id).toBe(participationId);
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });
});

@Component({
    template: '<jhi-start-practice-mode-button [exercise]="exercise" [smallButtons]="false" (practiceModeStarted)="startedParticipation = $event" />',
    imports: [StartPracticeModeButtonComponent],
})
class PracticeStartRouteComponent {
    readonly destroyRef = inject(DestroyRef);
    readonly button = viewChild.required(StartPracticeModeButtonComponent);
    readonly exercise = new ProgrammingExercise(new Course(), undefined);
    startedParticipation?: StudentParticipation;

    constructor() {
        this.exercise.id = 43;
        this.exercise.allowOnlineEditor = true;
        this.exercise.studentParticipations = [{ id: 10, testRun: false } as StudentParticipation];
    }
}

@Component({ template: 'Editor' })
class PracticeEditorRouteComponent {}

describe('Starting practice across the exercise editor redirect', () => {
    let httpMock: HttpTestingController;
    let router: Router;
    let harness: RouterTestingHarness;
    let completedNavigations: string[];
    let editorNavigationGate: Subject<boolean> | undefined;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                provideRouter([
                    { path: 'courses/:courseId/exercises/:exerciseId', component: PracticeStartRouteComponent },
                    { path: 'courses/:courseId/exercises/programming-exercises/:exerciseId', component: PracticeStartRouteComponent },
                    {
                        path: 'courses/:courseId/exercises/programming-exercises/:exerciseId/code-editor/:participationId',
                        component: PracticeEditorRouteComponent,
                        canActivate: [() => editorNavigationGate ?? true],
                    },
                    { path: 'courses/:courseId', component: PracticeStartRouteComponent },
                ]),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                MockProvider(AlertService, { error: vi.fn(), success: vi.fn() }),
            ],
        }).overrideComponent(StartPracticeModeButtonComponent, { set: { template: '' } });
        httpMock = TestBed.inject(HttpTestingController);
        router = TestBed.inject(Router);
        completedNavigations = [];
        editorNavigationGate = undefined;
        router.events.subscribe((event) => {
            if (event instanceof NavigationEnd) {
                completedNavigations.push(event.urlAfterRedirects);
            }
        });
        harness = await RouterTestingHarness.create();
    });

    afterEach(() => {
        httpMock.verify();
    });

    for (const origin of ['/courses/1/exercises/43', '/courses/1/exercises/programming-exercises/43']) {
        it(`should show the new practice participation after the initiating button was destroyed (${origin})`, async () => {
            const details = await harness.navigateByUrl(origin, PracticeStartRouteComponent);
            details.button().startPractice(false);
            const request = httpMock.expectOne('api/exercise/exercises/43/participations/practice?useGradedParticipation=false');

            // The split panel routes the existing graded participation while the practice POST is pending.
            await harness.navigateByUrl('/courses/1/exercises/programming-exercises/43/code-editor/10', PracticeEditorRouteComponent);
            expect(details.destroyRef.destroyed).toBe(true);
            request.flush({ id: 20, type: ParticipationType.PROGRAMMING, testRun: true, initializationState: InitializationState.INITIALIZED, exercise: { id: 43 } });
            await TestBed.inject(ApplicationRef).whenStable();

            expect(
                TestBed.inject(ParticipationWebsocketService)
                    .getParticipationsForExercise(43)
                    .some((participation) => participation.id === 20 && participation.testRun),
            ).toBe(true);
            expect(router.url).toBe('/courses/1/exercises/programming-exercises/43/code-editor/20');
            expect(completedNavigations.at(-1)).toBe('/courses/1/exercises/programming-exercises/43/code-editor/20');
            expect(details.startedParticipation).toBeUndefined();
        });
    }

    it('should follow an editor redirect that was already pending when practice was started', async () => {
        const details = await harness.navigateByUrl('/courses/1/exercises/43', PracticeStartRouteComponent);
        const gate = new Subject<boolean>();
        editorNavigationGate = gate;
        const guardStarted = firstValueFrom(
            router.events.pipe(
                filter((event) => event instanceof GuardsCheckStart),
                take(1),
            ),
        );
        const editorNavigation = router.navigateByUrl('/courses/1/exercises/programming-exercises/43/code-editor/10');
        await guardStarted;
        expect(router.currentNavigation()).not.toBeNull();
        details.button().startPractice(false);
        const request = httpMock.expectOne('api/exercise/exercises/43/participations/practice?useGradedParticipation=false');
        editorNavigationGate = undefined;
        gate.next(true);
        gate.complete();
        await editorNavigation;
        expect(details.destroyRef.destroyed).toBe(true);

        request.flush({ id: 20, type: ParticipationType.PROGRAMMING, testRun: true, initializationState: InitializationState.INITIALIZED, exercise: { id: 43 } });
        await TestBed.inject(ApplicationRef).whenStable();

        expect(router.url).toBe('/courses/1/exercises/programming-exercises/43/code-editor/20');
    });

    for (const laterPaths of [
        ['/courses/2'],
        ['/courses/1/exercises/programming-exercises/43/code-editor/30'],
        ['/courses/2', '/courses/1/exercises/programming-exercises/43/code-editor/10'],
        ['/courses/1/exercises/programming-exercises/43/code-editor/10'],
    ]) {
        it(`should respect a later navigation while practice is starting (${laterPaths.join(' → ')})`, async () => {
            const details = await harness.navigateByUrl('/courses/1/exercises/43', PracticeStartRouteComponent);
            details.button().startPractice(false);
            const request = httpMock.expectOne('api/exercise/exercises/43/participations/practice?useGradedParticipation=false');
            await harness.navigateByUrl('/courses/1/exercises/programming-exercises/43/code-editor/10', PracticeEditorRouteComponent);
            for (const laterPath of laterPaths) {
                await router.navigateByUrl(laterPath);
            }
            const navigationCount = completedNavigations.length;

            request.flush({ id: 20, type: ParticipationType.PROGRAMMING, testRun: true, initializationState: InitializationState.INITIALIZED, exercise: { id: 43 } });
            await TestBed.inject(ApplicationRef).whenStable();

            expect(router.url).toBe(laterPaths.at(-1));
            expect(completedNavigations).toHaveLength(navigationCount);
            expect(details.startedParticipation).toBeUndefined();
        });
    }

    it('should leave a course overview start on the overview', async () => {
        const overview = await harness.navigateByUrl('/courses/1', PracticeStartRouteComponent);
        overview.button().startPractice(false);
        httpMock.expectOne('api/exercise/exercises/43/participations/practice?useGradedParticipation=false').flush({
            id: 20,
            type: ParticipationType.PROGRAMMING,
            testRun: true,
            initializationState: InitializationState.INITIALIZED,
            exercise: { id: 43 },
        });
        await TestBed.inject(ApplicationRef).whenStable();

        expect(router.url).toBe('/courses/1');
        expect(overview.startedParticipation?.id).toBe(20);
    });

    it('should not navigate or alert a destroyed view when the practice start fails', async () => {
        const details = await harness.navigateByUrl('/courses/1/exercises/43', PracticeStartRouteComponent);
        details.button().startPractice(false);
        const request = httpMock.expectOne('api/exercise/exercises/43/participations/practice?useGradedParticipation=false');
        await harness.navigateByUrl('/courses/1/exercises/programming-exercises/43/code-editor/10', PracticeEditorRouteComponent);

        request.flush('Failed start', { status: 500, statusText: 'Internal Server Error' });
        await TestBed.inject(ApplicationRef).whenStable();

        expect(router.url).toBe('/courses/1/exercises/programming-exercises/43/code-editor/10');
        expect(TestBed.inject(AlertService).error).not.toHaveBeenCalled();
        expect(details.startedParticipation).toBeUndefined();
    });
});
