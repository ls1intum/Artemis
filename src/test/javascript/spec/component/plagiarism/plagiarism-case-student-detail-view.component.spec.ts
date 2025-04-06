import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { PlagiarismCaseStudentDetailViewComponent } from 'app/plagiarism/overview/detail-view/plagiarism-case-student-detail-view.component';
import { EntityResponseType, PlagiarismCasesService } from 'app/plagiarism/shared/services/plagiarism-cases.service';
import { ActivatedRoute, Params } from '@angular/router';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { PlagiarismCase } from 'app/plagiarism/shared/entities/PlagiarismCase';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockLocalStorageService } from '../../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { PlagiarismVerdict } from 'app/plagiarism/shared/entities/PlagiarismVerdict';
import dayjs from 'dayjs/esm';
import { MockNotificationService } from '../../helpers/mocks/service/mock-notification.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { NotificationService } from 'app/core/notification/shared/notification.service';

describe('Plagiarism Cases Student View Component', () => {
    let component: PlagiarismCaseStudentDetailViewComponent;
    let fixture: ComponentFixture<PlagiarismCaseStudentDetailViewComponent>;
    let plagiarismCasesService: PlagiarismCasesService;
    let plagiarismCasesServiceSpy: jest.SpyInstance<Observable<EntityResponseType>>;

    const ancestorRouteParamsSubject = new BehaviorSubject<Params>({ courseId: 1 });
    const routeParamsSubject = new BehaviorSubject<Params>({ plagiarismCaseId: 1 });

    const parentRoute = {
        params: ancestorRouteParamsSubject.asObservable(),
    } as any as ActivatedRoute;
    const route = { parent: parentRoute, params: routeParamsSubject.asObservable() } as any as ActivatedRoute;

    const exercise = {
        id: 1,
        title: 'Test Exercise',
        course: { id: 1, title: 'Test Course' },
    } as TextExercise;

    const plagiarismCase = {
        id: 1,
        exercise,
        verdict: PlagiarismVerdict.PLAGIARISM,
        post: { id: 1 },
        student: { name: 'Test User' },
    } as PlagiarismCase;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: NotificationService, useClass: MockNotificationService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockLocalStorageService },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(PlagiarismCaseStudentDetailViewComponent);
        component = fixture.componentInstance;
        plagiarismCasesService = fixture.debugElement.injector.get(PlagiarismCasesService);
        plagiarismCasesServiceSpy = jest.spyOn(plagiarismCasesService, 'getPlagiarismCaseDetailForStudent');
        plagiarismCasesServiceSpy.mockImplementation(
            (courseId, plagiarismCaseId) =>
                of({
                    body: {
                        ...plagiarismCase,
                        id: plagiarismCaseId,
                    },
                }) as Observable<HttpResponse<PlagiarismCase>>,
        );
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set plagiarism case on initialization', fakeAsync(() => {
        component.ngOnInit();
        tick();
        expect(component.courseId).toBe(1);
        expect(component.plagiarismCaseId).toBe(1);
        tick();
        expect(component.plagiarismCase).toEqual(plagiarismCase);
    }));

    it('should set isAfterDueDate', fakeAsync(() => {
        const now = dayjs();
        exercise.dueDate = now.add(1, 'day');
        component.ngOnInit();
        tick();
        expect(component.isAfterDueDate).toBeFalse();
    }));

    it('should unset isAfterDueDate', fakeAsync(() => {
        const now = dayjs();
        exercise.dueDate = now.subtract(1, 'day');
        component.ngOnInit();
        tick();
        expect(component.isAfterDueDate).toBeTrue();
    }));

    it('should load plagiarism case on route update', fakeAsync(() => {
        component.ngOnInit();
        tick();

        // Test courseId change
        ancestorRouteParamsSubject.next({ courseId: 2 });
        tick();

        expect(component.courseId).toBe(2);
        expect(component.plagiarismCaseId).toBe(1);
        tick();
        expect(component.plagiarismCase?.id).toBe(1);

        expect(plagiarismCasesServiceSpy).toHaveBeenCalledOnce();

        // Test plagiarismCaseId update with the same id
        routeParamsSubject.next({ plagiarismCaseId: 1 });
        tick();

        // plagiarismCaseId does not change so it should not update.
        expect(plagiarismCasesServiceSpy).toHaveBeenCalledOnce();

        // Test plagiarismCaseId change
        routeParamsSubject.next({ plagiarismCaseId: 2 });
        tick();

        expect(component.courseId).toBe(2);
        expect(component.plagiarismCaseId).toBe(2);
        tick();
        expect(component.plagiarismCase?.id).toBe(2);

        expect(plagiarismCasesServiceSpy).toHaveBeenCalledTimes(2);

        // Test both courseId and plagiarismCaseId change
        ancestorRouteParamsSubject.next({ courseId: 3 });
        routeParamsSubject.next({ plagiarismCaseId: 4 });
        tick();

        expect(component.courseId).toBe(3);
        expect(component.plagiarismCaseId).toBe(4);
        tick();
        expect(component.plagiarismCase?.id).toBe(4);

        expect(plagiarismCasesServiceSpy).toHaveBeenCalledTimes(3);
    }));
});
