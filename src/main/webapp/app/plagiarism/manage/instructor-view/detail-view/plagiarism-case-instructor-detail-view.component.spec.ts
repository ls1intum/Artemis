import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { PlagiarismCaseInstructorDetailViewComponent } from 'app/plagiarism/manage/instructor-view/detail-view/plagiarism-case-instructor-detail-view.component';
import { PlagiarismCasesService } from 'app/plagiarism/shared/services/plagiarism-cases.service';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { PlagiarismCase } from 'app/plagiarism/shared/entities/PlagiarismCase';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { Observable, ReplaySubject, of } from 'rxjs';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { PlagiarismVerdict } from 'app/plagiarism/shared/entities/PlagiarismVerdict';
import { MetisService } from 'app/communication/service/metis.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { Post } from 'app/communication/shared/entities/post.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { User } from 'app/account/user/user.model';
import { MockProvider } from 'ng-mocks';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';

describe('Plagiarism Cases Instructor View Component', () => {
    setupTestBed({ zoneless: true });

    let component: PlagiarismCaseInstructorDetailViewComponent;
    let fixture: ComponentFixture<PlagiarismCaseInstructorDetailViewComponent>;
    let plagiarismCasesService: PlagiarismCasesService;
    let saveVerdictSpy: ReturnType<typeof vi.spyOn>;

    const route = { snapshot: { paramMap: convertToParamMap({ courseId: 1, plagiarismCaseId: 1 }) } } as any as ActivatedRoute;

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
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: WebsocketService, useClass: MockWebsocketService },
                MockProvider(AlertService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(PlagiarismCaseInstructorDetailViewComponent);
        component = fixture.componentInstance;
        plagiarismCasesService = TestBed.inject(PlagiarismCasesService);
        vi.spyOn(plagiarismCasesService, 'getPlagiarismCaseDetailForInstructor').mockReturnValue(of({ body: plagiarismCase }) as Observable<HttpResponse<PlagiarismCase>>);
        saveVerdictSpy = vi.spyOn(plagiarismCasesService, 'saveVerdict');
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should set plagiarism case and exercises on initialization', async () => {
        component.ngOnInit();
        await Promise.resolve();
        expect(component.courseId).toBe(1);
        expect(component.plagiarismCaseId).toBe(1);
        expect(component.plagiarismCase).toEqual(plagiarismCase);
        expect(component.currentAccount?.id).toBe(99);
    });

    it('should throw when saving plagiarism case plagiarism verdict before student is notified', () => {
        component.courseId = 1;
        component.plagiarismCaseId = 1;
        component.plagiarismCase = { id: 1 };
        expect(() => component.saveVerdict()).toThrow(Error);
        expect(() => component.savePointDeductionVerdict()).toThrow(Error);
        expect(() => component.saveWarningVerdict()).toThrow(Error);
        expect(() => component.saveNoPlagiarismVerdict()).toThrow(Error);
    });

    it('should save plagiarism case plagiarism verdict', async () => {
        saveVerdictSpy.mockReturnValue(of({ body: { verdict: PlagiarismVerdict.PLAGIARISM } }) as Observable<HttpResponse<PlagiarismCase>>);
        component.posts = [{ id: 1, plagiarismCase: { id: 1 } }];
        component.courseId = 1;
        component.plagiarismCaseId = 1;
        component.plagiarismCase = { id: 1 };
        component.saveVerdict();
        await Promise.resolve();
        expect(component.plagiarismCase).toEqual({ id: 1, verdict: PlagiarismVerdict.PLAGIARISM });
    });

    it('should save plagiarism case warning verdict', async () => {
        saveVerdictSpy.mockReturnValue(of({ body: { verdict: PlagiarismVerdict.WARNING, verdictMessage: 'message' } }) as Observable<HttpResponse<PlagiarismCase>>);
        component.posts = [{ id: 1, plagiarismCase: { id: 1 } }];
        component.courseId = 1;
        component.plagiarismCaseId = 1;
        component.plagiarismCase = { id: 1 };
        component.verdictMessage = 'message';
        component.saveWarningVerdict();
        await Promise.resolve();
        expect(component.plagiarismCase).toEqual({ id: 1, verdict: PlagiarismVerdict.WARNING, verdictMessage: 'message' });
    });

    it('should save plagiarism case point deduction verdict', async () => {
        saveVerdictSpy.mockReturnValue(of({ body: { verdict: PlagiarismVerdict.POINT_DEDUCTION, verdictPointDeduction: 80 } }) as Observable<HttpResponse<PlagiarismCase>>);
        component.posts = [{ id: 1, plagiarismCase: { id: 1 } }];
        component.courseId = 1;
        component.plagiarismCaseId = 1;
        component.plagiarismCase = { id: 1 };
        component.verdictPointDeduction = 80;
        component.savePointDeductionVerdict();
        await Promise.resolve();
        expect(component.plagiarismCase).toEqual({ id: 1, verdict: PlagiarismVerdict.POINT_DEDUCTION, verdictPointDeduction: 80 });
    });

    it('should save plagiarism case no plagiarism verdict', async () => {
        saveVerdictSpy.mockReturnValue(of({ body: { verdict: PlagiarismVerdict.NO_PLAGIARISM } }) as Observable<HttpResponse<PlagiarismCase>>);
        component.posts = [{ id: 1, plagiarismCase: { id: 1 } }];
        component.courseId = 1;
        component.plagiarismCaseId = 1;
        component.plagiarismCase = { id: 1 };
        component.saveNoPlagiarismVerdict();
        await Promise.resolve();
        expect(component.plagiarismCase).toEqual({ id: 1, verdict: PlagiarismVerdict.NO_PLAGIARISM });
    });

    it('should create student notification for course exercise', () => {
        const translateService = TestBed.inject(TranslateService);
        const translateServiceSpy = vi.spyOn(translateService, 'instant');

        component.plagiarismCase = plagiarismCase;
        component.currentAccount = { id: 99, name: 'user' } as User;
        component.createEmptyPost();
        expect(component.createdPost.plagiarismCase).toEqual({ id: 1 });
        expect(component.createdPost.title).toBe('artemisApp.plagiarism.plagiarismCases.notification.title');
        expect(component.createdPost.content).toBe('artemisApp.plagiarism.plagiarismCases.notification.body');

        expect(translateServiceSpy).toHaveBeenCalledTimes(3);
        expect(translateServiceSpy).toHaveBeenCalledWith(
            'artemisApp.plagiarism.plagiarismCases.notification.body',
            expect.objectContaining({
                student: plagiarismCase.student!.name,
                instructor: 'user',
                exercise: plagiarismCase.exercise!.title,
                inCourseOrExam: 'artemisApp.plagiarism.plagiarismCases.notification.inCourse',
                courseOrExam: exercise.course!.title,
            }),
        );
    });

    it('should create student notification for exam exercise', () => {
        const translateService = TestBed.inject(TranslateService);
        const translateServiceSpy = vi.spyOn(translateService, 'instant');

        const examTitle = 'Exam Title';
        const examPlagiarismCase = {
            ...plagiarismCase,
            exercise: { ...exercise, course: undefined, exerciseGroup: { exam: { id: 3, title: examTitle } } },
        };
        component.plagiarismCase = examPlagiarismCase;
        component.currentAccount = { id: 99, name: 'user' } as User;
        component.createEmptyPost();
        expect(component.createdPost.plagiarismCase).toEqual({ id: 1 });
        expect(component.createdPost.title).toBe('artemisApp.plagiarism.plagiarismCases.notification.title');
        expect(component.createdPost.content).toBe('artemisApp.plagiarism.plagiarismCases.notification.body');

        expect(translateServiceSpy).toHaveBeenCalledTimes(3);
        expect(translateServiceSpy).toHaveBeenCalledWith(
            'artemisApp.plagiarism.plagiarismCases.notification.body',
            expect.objectContaining({
                student: examPlagiarismCase.student!.name,
                instructor: 'user',
                exercise: examPlagiarismCase.exercise!.title,
                inCourseOrExam: 'artemisApp.plagiarism.plagiarismCases.notification.inExam',
                courseOrExam: examTitle,
            }),
        );
    });

    it('should create student notification with empty names and titles', () => {
        const translateService = TestBed.inject(TranslateService);
        const translateServiceSpy = vi.spyOn(translateService, 'instant');

        component.plagiarismCase = {
            ...plagiarismCase,
            student: undefined,
            exercise: undefined,
        } as PlagiarismCase;
        component.currentAccount = { id: 99, name: 'user' } as User;

        component.createEmptyPost();
        expect(component.createdPost.plagiarismCase).toEqual({ id: 1 });
        expect(component.createdPost.title).toBe('artemisApp.plagiarism.plagiarismCases.notification.title');
        expect(component.createdPost.content).toBe('artemisApp.plagiarism.plagiarismCases.notification.body');

        expect(translateServiceSpy).toHaveBeenCalledTimes(3);
        expect(translateServiceSpy).toHaveBeenCalledWith(
            'artemisApp.plagiarism.plagiarismCases.notification.body',
            expect.objectContaining({
                student: '',
                instructor: 'user',
                exercise: '',
                courseOrExam: '',
            }),
        );
    });

    it('should notify student', () => {
        const successSpy = vi.spyOn(TestBed.inject(AlertService), 'success');

        component.courseId = 1;
        const newPost = { id: 3, plagiarismCase: { id: 1 } } as Post;
        component.onStudentNotified(newPost);

        expect(component.posts).toHaveLength(1);
        expect(component.posts[0].id).toBe(newPost.id);

        expect(successSpy).toHaveBeenCalledOnce();
        expect(successSpy).toHaveBeenCalledWith('artemisApp.plagiarism.plagiarismCases.studentNotified');
    });

    it('should not display post unrelated to the current plagiarism case', async () => {
        const metisPostsSpy = vi.spyOn(fixture.debugElement.injector.get(MetisService), 'posts', 'get');
        const postsSubject = new ReplaySubject<Post[]>(1);
        metisPostsSpy.mockReturnValue(postsSubject.asObservable());

        postsSubject.next([]);

        component.ngOnInit();
        await Promise.resolve();

        expect(component.posts).toHaveLength(0);

        const relevantPost = { id: 1, plagiarismCase: { id: component.plagiarismCaseId } };
        postsSubject.next([relevantPost]);
        await Promise.resolve();

        expect(component.posts).toHaveLength(1);
        expect(component.posts[0].id).toBe(relevantPost.id);

        const irrelevantPost = { id: 2 };
        postsSubject.next([irrelevantPost]);
        await Promise.resolve();

        expect(component.posts).toHaveLength(1);
        expect(component.posts[0].id).toBe(relevantPost.id);
    });

    it('should delete post successfully', async () => {
        const metisPostsSpy = vi.spyOn(fixture.debugElement.injector.get(MetisService), 'posts', 'get');
        const postsSubject = new ReplaySubject<Post[]>(1);
        metisPostsSpy.mockReturnValue(postsSubject.asObservable());

        postsSubject.next([]);

        component.ngOnInit();
        await Promise.resolve();

        expect(component.posts).toHaveLength(0);

        const relevantPost = { id: 1, plagiarismCase: { id: component.plagiarismCaseId } };
        postsSubject.next([relevantPost]);
        await Promise.resolve();

        expect(component.posts).toHaveLength(1);
        expect(component.posts[0].id).toBe(relevantPost.id);

        postsSubject.next([]);
        expect(component.posts).toHaveLength(0);
    });
});
