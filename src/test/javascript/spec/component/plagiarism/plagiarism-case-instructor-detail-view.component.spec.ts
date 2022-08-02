import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { PlagiarismCaseInstructorDetailViewComponent } from 'app/course/plagiarism-cases/instructor-view/detail-view/plagiarism-case-instructor-detail-view.component';
import { ArtemisTestModule } from '../../test.module';
import { MockTranslateService, TranslateTestingModule } from '../../helpers/mocks/service/mock-translate.service';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { HttpResponse } from '@angular/common/http';
import { Observable, of, ReplaySubject } from 'rxjs';
import { TextExercise } from 'app/entities/text-exercise.model';
import { PlagiarismVerdict } from 'app/exercises/shared/plagiarism/types/PlagiarismVerdict';
import { MockLocalStorageService } from '../../helpers/mocks/service/mock-local-storage.service';
import { MetisService } from 'app/shared/metis/metis.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockMetisService } from '../../helpers/mocks/service/mock-metis-service.service';
import { Post } from 'app/entities/metis/post.model';

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
    }));

    it('should save plagiarism case plagiarism verdict', fakeAsync(() => {
        saveVerdictSpy.mockReturnValue(of({ body: { verdict: PlagiarismVerdict.PLAGIARISM } }) as Observable<HttpResponse<PlagiarismCase>>);
        component.courseId = 1;
        component.posts = [];
        component.posts.push({ id: 1 });
        component.plagiarismCaseId = 1;
        component.plagiarismCase = { id: 1 };
        component.saveVerdict();
        tick();
        expect(component.plagiarismCase).toEqual({ id: 1, verdict: PlagiarismVerdict.PLAGIARISM });
    }));

    it('should save plagiarism case plagiarism verdict (not notified yet)', fakeAsync(() => {
        saveVerdictSpy.mockReturnValue(of({ body: { verdict: PlagiarismVerdict.PLAGIARISM } }) as Observable<HttpResponse<PlagiarismCase>>);
        component.courseId = 1;
        component.plagiarismCaseId = 1;
        component.plagiarismCase = { id: 1 };
        component.saveVerdict();
        tick();
        // the verdict is not available yet, because the spy function is not invoked
        expect(component.plagiarismCase).toEqual({ id: 1 });
    }));

    it('should save plagiarism case warning verdict', fakeAsync(() => {
        saveVerdictSpy.mockReturnValue(of({ body: { verdict: PlagiarismVerdict.WARNING, verdictMessage: 'message' } }) as Observable<HttpResponse<PlagiarismCase>>);
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
        component.courseId = 1;
        component.plagiarismCaseId = 1;
        component.plagiarismCase = { id: 1 };
        component.verdictPointDeduction = 80;
        component.savePointDeductionVerdict();
        tick();
        expect(component.plagiarismCase).toEqual({ id: 1, verdict: PlagiarismVerdict.POINT_DEDUCTION, verdictPointDeduction: 80 });
    }));

    it('should create empty post', () => {
        component.plagiarismCase = plagiarismCase;
        component.createEmptyPost();
        expect(component.createdPost.plagiarismCase).toEqual({ id: 1 });
        expect(component.createdPost.title).toBe('Plagiarism Case Test Exercise');
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
