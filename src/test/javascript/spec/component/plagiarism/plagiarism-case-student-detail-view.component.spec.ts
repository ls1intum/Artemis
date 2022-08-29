import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { PlagiarismCaseStudentDetailViewComponent } from 'app/course/plagiarism-cases/student-view/detail-view/plagiarism-case-student-detail-view.component';
import { ArtemisTestModule } from '../../test.module';
import { MockTranslateService, TranslateTestingModule } from '../../helpers/mocks/service/mock-translate.service';
import { EntityResponseType, PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { ActivatedRoute, Params } from '@angular/router';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockLocalStorageService } from '../../helpers/mocks/service/mock-local-storage.service';
import { MetisService } from 'app/shared/metis/metis.service';
import { TranslateService } from '@ngx-translate/core';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TextExercise } from 'app/entities/text-exercise.model';
import { PlagiarismVerdict } from 'app/exercises/shared/plagiarism/types/PlagiarismVerdict';

describe('Plagiarism Cases Student View Component', () => {
    let component: PlagiarismCaseStudentDetailViewComponent;
    let fixture: ComponentFixture<PlagiarismCaseStudentDetailViewComponent>;
    let plagiarismCasesService: PlagiarismCasesService;
    let plagiarismCasesServiceSpy: jest.SpyInstance<Observable<EntityResponseType>>;

    const ancestorRouteParamsSubject = new BehaviorSubject<Params>({ courseId: 1 });
    const routeParamsSubject = new BehaviorSubject<Params>({ plagiarismCaseId: 1 });

    const parentRoute = {
        parent: {
            params: ancestorRouteParamsSubject.asObservable(),
        },
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
            imports: [ArtemisTestModule, TranslateTestingModule],
            declarations: [PlagiarismCaseStudentDetailViewComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockLocalStorageService },
                { provide: MetisService, useClass: MetisService },
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
        expect(component.plagiarismCaseId).toBe(1);
        tick();
        expect(component.plagiarismCase?.id).toBe(2);

        expect(plagiarismCasesServiceSpy).toHaveBeenCalledTimes(2);

        // Test both courseId and plagiarismCaseId change
        ancestorRouteParamsSubject.next({ courseId: 3 });
        routeParamsSubject.next({ plagiarismCaseId: 4 });
        tick();
        expect(component.plagiarismCase?.id).toBe(4);

        expect(plagiarismCasesServiceSpy).toHaveBeenCalledTimes(3);
    }));
});
