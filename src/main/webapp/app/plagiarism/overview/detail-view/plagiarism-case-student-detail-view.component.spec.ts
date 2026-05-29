import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PlagiarismCaseStudentDetailViewComponent } from 'app/plagiarism/overview/detail-view/plagiarism-case-student-detail-view.component';
import { PlagiarismCasesService } from 'app/plagiarism/shared/services/plagiarism-cases.service';
import { ActivatedRoute, Params } from '@angular/router';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { PlagiarismCase } from 'app/plagiarism/shared/entities/PlagiarismCase';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { PlagiarismVerdict } from 'app/plagiarism/shared/entities/PlagiarismVerdict';
import dayjs from 'dayjs/esm';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { MockMetisConversationService } from 'test/helpers/mocks/service/mock-metis-conversation.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';

describe('Plagiarism Cases Student View Component', () => {
    setupTestBed({ zoneless: true });

    let component: PlagiarismCaseStudentDetailViewComponent;
    let fixture: ComponentFixture<PlagiarismCaseStudentDetailViewComponent>;
    let plagiarismCasesService: PlagiarismCasesService;
    let plagiarismCasesServiceSpy: ReturnType<typeof vi.spyOn<PlagiarismCasesService, 'getPlagiarismCaseDetailForStudent'>>;

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
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: MetisConversationService, useClass: MockMetisConversationService },
                { provide: WebsocketService, useClass: MockWebsocketService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(PlagiarismCaseStudentDetailViewComponent);
        component = fixture.componentInstance;
        plagiarismCasesService = TestBed.inject(PlagiarismCasesService);
        plagiarismCasesServiceSpy = vi.spyOn(plagiarismCasesService, 'getPlagiarismCaseDetailForStudent');
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
        vi.restoreAllMocks();
    });

    it('should set plagiarism case on initialization', async () => {
        component.ngOnInit();
        await Promise.resolve();
        expect(component.courseId).toBe(1);
        expect(component.plagiarismCaseId).toBe(1);
        await Promise.resolve();
        expect(component.plagiarismCase).toEqual(plagiarismCase);
    });

    it('should set isAfterDueDate', async () => {
        const now = dayjs();
        exercise.dueDate = now.add(1, 'day');
        component.ngOnInit();
        await Promise.resolve();
        expect(component.isAfterDueDate).toBe(false);
    });

    it('should unset isAfterDueDate', async () => {
        const now = dayjs();
        exercise.dueDate = now.subtract(1, 'day');
        component.ngOnInit();
        await Promise.resolve();
        expect(component.isAfterDueDate).toBe(true);
    });

    it('should load plagiarism case on route update', async () => {
        component.ngOnInit();
        await Promise.resolve();

        // Test courseId change
        ancestorRouteParamsSubject.next({ courseId: 2 });
        await Promise.resolve();

        expect(component.courseId).toBe(2);
        expect(component.plagiarismCaseId).toBe(1);
        await Promise.resolve();
        expect(component.plagiarismCase?.id).toBe(1);

        expect(plagiarismCasesServiceSpy).toHaveBeenCalledOnce();

        // Test plagiarismCaseId update with the same id
        routeParamsSubject.next({ plagiarismCaseId: 1 });
        await Promise.resolve();

        // plagiarismCaseId does not change so it should not update.
        expect(plagiarismCasesServiceSpy).toHaveBeenCalledOnce();

        // Test plagiarismCaseId change
        routeParamsSubject.next({ plagiarismCaseId: 2 });
        await Promise.resolve();

        expect(component.courseId).toBe(2);
        expect(component.plagiarismCaseId).toBe(2);
        await Promise.resolve();
        expect(component.plagiarismCase?.id).toBe(2);

        expect(plagiarismCasesServiceSpy).toHaveBeenCalledTimes(2);

        // Test both courseId and plagiarismCaseId change
        ancestorRouteParamsSubject.next({ courseId: 3 });
        routeParamsSubject.next({ plagiarismCaseId: 4 });
        await Promise.resolve();

        expect(component.courseId).toBe(3);
        expect(component.plagiarismCaseId).toBe(4);
        await Promise.resolve();
        expect(component.plagiarismCase?.id).toBe(4);

        expect(plagiarismCasesServiceSpy).toHaveBeenCalledTimes(3);
    });
});
