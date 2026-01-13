import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { of, throwError } from 'rxjs';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { FaqUpdateComponent } from 'app/communication/faq/faq-update.component';
import { FaqService } from 'app/communication/faq/faq.service';
import { Faq } from 'app/communication/shared/entities/faq.model';
import { AlertService } from 'app/shared/service/alert.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { FaqCategory } from 'app/communication/shared/entities/faq-category.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FaqConsistencyComponent } from './faq-consistency.component';
import { RewriteAction } from '../../shared/monaco-editor/model/actions/artemis-intelligence/rewrite.action';

describe('FaqUpdateComponent', () => {
    let faqUpdateComponentFixture: ComponentFixture<FaqUpdateComponent>;
    let faqUpdateComponent: FaqUpdateComponent;
    let faqService: FaqService;
    let profileService: ProfileService;
    let activatedRoute: ActivatedRoute;
    let router: Router;
    let faq1: Faq;
    let courseId: number;

    let alertServiceStub: jest.SpyInstance;
    let alertService: AlertService;

    beforeEach(() => {
        faq1 = new Faq();
        faq1.id = 1;
        faq1.questionTitle = 'questionTitle';
        faq1.questionAnswer = 'questionAnswer';
        faq1.categories = [new FaqCategory('category1', '#94a11c')];
        courseId = 1;
        const mockProfileInfo = { activeProfiles: ['iris'] } as ProfileInfo;
        TestBed.configureTestingModule({
            imports: [FaIconComponent],
            declarations: [
                FaqUpdateComponent,
                MockComponent(MarkdownEditorMonacoComponent),
                MockPipe(HtmlForMarkdownPipe),
                MockRouterLinkDirective,
                MockComponent(FaqConsistencyComponent),
            ],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            data: of({ course: { id: 1 } }),
                        },
                        snapshot: {
                            paramMap: convertToParamMap({
                                courseId: '1',
                            }),
                        },
                    },
                },
                MockProvider(FaqService, {
                    find: () => {
                        return of(
                            new HttpResponse({
                                body: faq1,
                                status: 200,
                            }),
                        );
                    },
                    findAllCategoriesByCourseId: () => {
                        return of(
                            new HttpResponse({
                                body: [],
                                status: 200,
                            }),
                        );
                    },
                }),

                MockProvider(ProfileService, {
                    getProfileInfo: () => mockProfileInfo,
                }),
                { provide: AccountService, useClass: MockAccountService },
                MockProvider(AlertService),
                provideHttpClient(),
            ],
        }).compileComponents();

        global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
            return new MockResizeObserver(callback);
        });

        faqUpdateComponentFixture = TestBed.createComponent(FaqUpdateComponent);
        faqUpdateComponent = faqUpdateComponentFixture.componentInstance;

        faqService = TestBed.inject(FaqService);
        profileService = TestBed.inject(ProfileService);
        alertService = TestBed.inject(AlertService);

        router = TestBed.inject(Router);
        activatedRoute = TestBed.inject(ActivatedRoute);
        faqUpdateComponentFixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create faq', fakeAsync(() => {
        faqUpdateComponent.faq = { questionTitle: 'test1' } as Faq;
        faqUpdateComponent.isAtLeastInstructor = true;
        const createSpy = jest.spyOn(faqService, 'create').mockReturnValue(
            of(
                new HttpResponse({
                    body: {
                        id: 3,
                        questionTitle: 'test1',
                        course: {
                            id: 1,
                        },
                    } as Faq,
                }),
            ),
        );

        faqUpdateComponentFixture.changeDetectorRef.detectChanges();
        faqUpdateComponent.save();
        tick();

        expect(createSpy).toHaveBeenCalledExactlyOnceWith(courseId, { questionTitle: 'test1', faqState: 'ACCEPTED' });
        expect(faqUpdateComponent.isSaving).toBeFalse();
    }));

    it('should propose faq', fakeAsync(() => {
        faqUpdateComponent.faq = { questionTitle: 'test1' } as Faq;
        faqUpdateComponent.isAtLeastInstructor = false;
        const createSpy = jest.spyOn(faqService, 'create').mockReturnValue(
            of(
                new HttpResponse({
                    body: {
                        id: 3,
                        questionTitle: 'test1',
                        course: {
                            id: 1,
                        },
                    } as Faq,
                }),
            ),
        );

        faqUpdateComponentFixture.changeDetectorRef.detectChanges();
        faqUpdateComponent.save();
        tick();

        expect(createSpy).toHaveBeenCalledExactlyOnceWith(courseId, { questionTitle: 'test1', faqState: 'PROPOSED' });
        expect(faqUpdateComponent.isSaving).toBeFalse();
    }));

    it('should edit a faq', fakeAsync(() => {
        activatedRoute.parent!.data = of({ course: { id: 1 }, faq: { id: 6 } });
        faqUpdateComponent.isAtLeastInstructor = true;
        faqUpdateComponentFixture.changeDetectorRef.detectChanges();
        faqUpdateComponent.faq = { id: 6, questionTitle: 'test1Updated' } as Faq;

        const updateSpy = jest.spyOn(faqService, 'update').mockReturnValue(
            of<HttpResponse<Faq>>(
                new HttpResponse({
                    body: {
                        id: 6,
                        questionTitle: 'test1Updated',
                        questionAnswer: 'answer',
                        course: {
                            id: 1,
                        },
                    } as Faq,
                }),
            ),
        );

        faqUpdateComponent.save();
        tick();
        faqUpdateComponentFixture.changeDetectorRef.detectChanges();
        expect(updateSpy).toHaveBeenCalledExactlyOnceWith(courseId, { id: 6, questionTitle: 'test1Updated', faqState: 'ACCEPTED' });
    }));

    it('should propose to edit a faq', fakeAsync(() => {
        activatedRoute.parent!.data = of({ course: { id: 1 }, faq: { id: 6 } });
        faqUpdateComponent.isAtLeastInstructor = false;
        faqUpdateComponentFixture.changeDetectorRef.detectChanges();
        faqUpdateComponent.faq = { id: 6, questionTitle: 'test1Updated' } as Faq;

        const updateSpy = jest.spyOn(faqService, 'update').mockReturnValue(
            of<HttpResponse<Faq>>(
                new HttpResponse({
                    body: {
                        id: 6,
                        questionTitle: 'test1Updated',
                        questionAnswer: 'answer',
                        course: {
                            id: 1,
                        },
                    } as Faq,
                }),
            ),
        );

        faqUpdateComponent.save();
        tick();
        faqUpdateComponentFixture.changeDetectorRef.detectChanges();
        expect(updateSpy).toHaveBeenCalledExactlyOnceWith(courseId, { id: 6, questionTitle: 'test1Updated', faqState: 'PROPOSED' });
    }));

    it('should navigate to previous state', fakeAsync(() => {
        activatedRoute = TestBed.inject(ActivatedRoute);
        activatedRoute.parent!.data = of({ course: { id: 1 }, faq: { id: 6, questionTitle: '', course: { id: 1 } } });

        faqUpdateComponent.ngOnInit();
        faqUpdateComponentFixture.changeDetectorRef.detectChanges();

        const navigateSpy = jest.spyOn(router, 'navigate');
        const previousState = jest.spyOn(faqUpdateComponent, 'previousState');
        faqUpdateComponent.previousState();
        tick();
        expect(previousState).toHaveBeenCalledOnce();

        const expectedPath = ['course-management', 1, 'faqs'];
        expect(navigateSpy).toHaveBeenCalledWith(expectedPath);
    }));

    it('should update categories', fakeAsync(() => {
        const categories = [new FaqCategory('category1', 'red'), new FaqCategory('category2', 'blue')];
        faqUpdateComponentFixture.changeDetectorRef.detectChanges();
        faqUpdateComponent.updateCategories(categories);
        expect(faqUpdateComponent.faqCategories).toEqual(categories);
        expect(faqUpdateComponent.faq.categories).toEqual(categories);
    }));

    it('should not be able to save unless title and question are filled', fakeAsync(() => {
        faqUpdateComponentFixture.changeDetectorRef.detectChanges();
        faqUpdateComponent.faq = { questionTitle: 'test1' } as Faq;
        faqUpdateComponent.validate();
        expect(faqUpdateComponent.isAllowedToSave).toBeFalse();
        faqUpdateComponent.faq = { questionAnswer: 'test1' } as Faq;
        faqUpdateComponent.validate();
        expect(faqUpdateComponent.isAllowedToSave).toBeFalse();
        faqUpdateComponent.faq = { questionTitle: 'test', questionAnswer: 'test1' } as Faq;
        faqUpdateComponent.validate();
        expect(faqUpdateComponent.isAllowedToSave).toBeTrue();
    }));

    it('should fail while saving with ErrorResponse', fakeAsync(() => {
        alertServiceStub = jest.spyOn(alertService, 'error');
        const error = { status: 404 };
        jest.spyOn(faqService, 'create').mockReturnValue(throwError(() => new HttpErrorResponse(error)));
        faqUpdateComponent.save();
        expect(faqUpdateComponent.isSaving).toBeFalse();
        expect(alertServiceStub).toHaveBeenCalledOnce();
        flush();
    }));

    it('should handleMarkdownChange properly', () => {
        faqUpdateComponent.faq = { questionTitle: 'test1', questionAnswer: 'answer' } as Faq;
        faqUpdateComponent.handleMarkdownChange('test');
        expect(faqUpdateComponent.faq.questionAnswer).toBe('test');
    });

    it('should have no intelligence action when IRIS is not active', () => {
        faqUpdateComponentFixture = TestBed.createComponent(FaqUpdateComponent);
        faqUpdateComponent = faqUpdateComponentFixture.componentInstance;
        faqUpdateComponentFixture.detectChanges();

        expect(faqUpdateComponent.artemisIntelligenceActions()).toEqual([]);
    });

    it('should have intelligence action when IRIS is active', () => {
        const isProfileActiveSpy = jest.spyOn(profileService, 'isProfileActive').mockReturnValue(true);

        faqUpdateComponentFixture = TestBed.createComponent(FaqUpdateComponent);
        faqUpdateComponent = faqUpdateComponentFixture.componentInstance;
        faqUpdateComponent.courseId = 1;
        faqUpdateComponentFixture.detectChanges();

        expect(isProfileActiveSpy).toHaveBeenCalledWith('iris');

        const actions = faqUpdateComponent.artemisIntelligenceActions();
        expect(actions).toHaveLength(1);
        expect(actions[0]).toBeInstanceOf(RewriteAction);
    });

    it('should reset renderedConsistencyCheckResultMarkdown when dismissConsistencyCheck is called', () => {
        faqUpdateComponent.renderedConsistencyCheckResultMarkdown.set({
            result: 'Some result',
            inconsistencies: ['Inconsistency 1'],
            suggestions: ['Suggestion 1'],
            improvement: 'Some improvement',
        });

        faqUpdateComponent.dismissConsistencyCheck();

        expect(faqUpdateComponent.renderedConsistencyCheckResultMarkdown()).toEqual({
            result: '',
            inconsistencies: [],
            suggestions: [],
            improvement: '',
        });
    });
});
