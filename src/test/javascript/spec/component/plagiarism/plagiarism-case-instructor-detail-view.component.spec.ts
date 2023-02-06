import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { PlagiarismCaseInstructorDetailViewComponent } from 'app/course/plagiarism-cases/instructor-view/detail-view/plagiarism-case-instructor-detail-view.component';
import { ArtemisTestModule } from '../../test.module';
import { MockTranslateService, TranslateTestingModule } from '../../helpers/mocks/service/mock-translate.service';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { HttpResponse } from '@angular/common/http';
import { Observable, ReplaySubject, of } from 'rxjs';
import { TextExercise } from 'app/entities/text-exercise.model';
import { PlagiarismVerdict } from 'app/exercises/shared/plagiarism/types/PlagiarismVerdict';
import { MockLocalStorageService } from '../../helpers/mocks/service/mock-local-storage.service';
import { MetisService } from 'app/shared/metis/metis.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { Post } from 'app/entities/metis/post.model';
import { AlertService } from 'app/core/util/alert.service';
import { MockProvider } from 'ng-mocks';
import { MockMetisService } from '../../helpers/mocks/service/mock-metis-service.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { User } from 'app/core/user/user.model';

describe('Plagiarism Cases Instructor View Component', () => {
    let component: PlagiarismCaseInstructorDetailViewComponent;
    let fixture: ComponentFixture<PlagiarismCaseInstructorDetailViewComponent>;
    let plagiarismCasesService: PlagiarismCasesService;
    let saveVerdictSpy: jest.SpyInstance;

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
            imports: [ArtemisTestModule, TranslateTestingModule],
            declarations: [PlagiarismCaseInstructorDetailViewComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockLocalStorageService },
                { provide: MetisService, useClass: MockMetisService },
                { provide: AccountService, useClass: MockAccountService },
                MockProvider(AlertService),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(PlagiarismCaseInstructorDetailViewComponent);
        component = fixture.componentInstance;
        plagiarismCasesService = fixture.debugElement.injector.get(PlagiarismCasesService);
        jest.spyOn(plagiarismCasesService, 'getPlagiarismCaseDetailForInstructor').mockReturnValue(of({ body: plagiarismCase }) as Observable<HttpResponse<PlagiarismCase>>);
        saveVerdictSpy = jest.spyOn(plagiarismCasesService, 'saveVerdict');
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set plagiarism case and exercises on initialization', fakeAsync(() => {
        component.ngOnInit();
        tick();
        expect(component.courseId).toBe(1);
        expect(component.plagiarismCaseId).toBe(1);
        expect(component.plagiarismCase).toEqual(plagiarismCase);
        expect(component.currentAccount?.id).toBe(99);
    }));

    it('should throw when saving plagiarism case plagiarism verdict before student is notified', () => {
        component.courseId = 1;
        component.plagiarismCaseId = 1;
        component.plagiarismCase = { id: 1 };
        expect(() => component.saveVerdict()).toThrow(Error);
        expect(() => component.savePointDeductionVerdict()).toThrow(Error);
        expect(() => component.saveWarningVerdict()).toThrow(Error);
        expect(() => component.saveNoPlagiarismVerdict()).toThrow(Error);
    });

    it('should save plagiarism case plagiarism verdict', fakeAsync(() => {
        saveVerdictSpy.mockReturnValue(of({ body: { verdict: PlagiarismVerdict.PLAGIARISM } }) as Observable<HttpResponse<PlagiarismCase>>);
        component.posts = [{ id: 1, plagiarismCase: { id: 1 } }];
        component.courseId = 1;
        component.plagiarismCaseId = 1;
        component.plagiarismCase = { id: 1 };
        component.saveVerdict();
        tick();
        expect(component.plagiarismCase).toEqual({ id: 1, verdict: PlagiarismVerdict.PLAGIARISM });
    }));

    it('should save plagiarism case warning verdict', fakeAsync(() => {
        saveVerdictSpy.mockReturnValue(of({ body: { verdict: PlagiarismVerdict.WARNING, verdictMessage: 'message' } }) as Observable<HttpResponse<PlagiarismCase>>);
        component.posts = [{ id: 1, plagiarismCase: { id: 1 } }];
        component.courseId = 1;
        component.plagiarismCaseId = 1;
        component.plagiarismCase = { id: 1 };
        component.verdictMessage = 'message';
        component.saveWarningVerdict();
        tick();
        expect(component.plagiarismCase).toEqual({ id: 1, verdict: PlagiarismVerdict.WARNING, verdictMessage: 'message' });
    }));

    it('should save plagiarism case point deduction verdict', fakeAsync(() => {
        saveVerdictSpy.mockReturnValue(of({ body: { verdict: PlagiarismVerdict.POINT_DEDUCTION, verdictPointDeduction: 80 } }) as Observable<HttpResponse<PlagiarismCase>>);
        component.posts = [{ id: 1, plagiarismCase: { id: 1 } }];
        component.courseId = 1;
        component.plagiarismCaseId = 1;
        component.plagiarismCase = { id: 1 };
        component.verdictPointDeduction = 80;
        component.savePointDeductionVerdict();
        tick();
        expect(component.plagiarismCase).toEqual({ id: 1, verdict: PlagiarismVerdict.POINT_DEDUCTION, verdictPointDeduction: 80 });
    }));

    it('should save plagiarism case no plagiarism verdict', fakeAsync(() => {
        saveVerdictSpy.mockReturnValue(of({ body: { verdict: PlagiarismVerdict.NO_PLAGIARISM } }) as Observable<HttpResponse<PlagiarismCase>>);
        component.posts = [{ id: 1, plagiarismCase: { id: 1 } }];
        component.courseId = 1;
        component.plagiarismCaseId = 1;
        component.plagiarismCase = { id: 1 };
        component.saveNoPlagiarismVerdict();
        tick();
        expect(component.plagiarismCase).toEqual({ id: 1, verdict: PlagiarismVerdict.NO_PLAGIARISM });
    }));

    it('should create student notification for course exercise', () => {
        const translateService = fixture.debugElement.injector.get(TranslateService);
        const translateServiceSpy = jest.spyOn(translateService, 'instant');

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
        const translateService = fixture.debugElement.injector.get(TranslateService);
        const translateServiceSpy = jest.spyOn(translateService, 'instant');

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
        const translateService = fixture.debugElement.injector.get(TranslateService);
        const translateServiceSpy = jest.spyOn(translateService, 'instant');

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
        const successSpy = jest.spyOn(fixture.debugElement.injector.get(AlertService), 'success');

        component.courseId = 1;
        const newPost = { id: 3, plagiarismCase: { id: 1 } } as Post;
        component.onStudentNotified(newPost);

        expect(component.posts).toHaveLength(1);
        expect(component.posts[0].id).toBe(newPost.id);

        expect(successSpy).toHaveBeenCalledOnce();
        expect(successSpy).toHaveBeenCalledWith('artemisApp.plagiarism.plagiarismCases.studentNotified');
    });

    it('should not display post unrelated to the current plagiarism case', fakeAsync(() => {
        const metisPostsSpy = jest.spyOn(fixture.debugElement.injector.get(MetisService), 'posts', 'get');
        const postsSubject = new ReplaySubject<Post[]>(1);
        metisPostsSpy.mockReturnValue(postsSubject.asObservable());

        postsSubject.next([]);

        component.ngOnInit();
        tick();

        expect(component.posts).toBeEmpty();

        const relevantPost = { id: 1, plagiarismCase: { id: component.plagiarismCaseId } };
        postsSubject.next([relevantPost]);
        tick();

        expect(component.posts).toHaveLength(1);
        expect(component.posts[0].id).toBe(relevantPost.id);

        const irrelevantPost = { id: 2 };
        postsSubject.next([irrelevantPost]);
        tick();

        expect(component.posts).toHaveLength(1);
        expect(component.posts[0].id).toBe(relevantPost.id);
    }));

    it('should delete post successfully', fakeAsync(() => {
        const metisPostsSpy = jest.spyOn(fixture.debugElement.injector.get(MetisService), 'posts', 'get');
        const postsSubject = new ReplaySubject<Post[]>(1);
        metisPostsSpy.mockReturnValue(postsSubject.asObservable());

        postsSubject.next([]);

        component.ngOnInit();
        tick();

        expect(component.posts).toBeEmpty();

        const relevantPost = { id: 1, plagiarismCase: { id: component.plagiarismCaseId } };
        postsSubject.next([relevantPost]);
        tick();

        expect(component.posts).toHaveLength(1);
        expect(component.posts[0].id).toBe(relevantPost.id);

        postsSubject.next([]);
        expect(component.posts).toBeEmpty();
    }));
});
