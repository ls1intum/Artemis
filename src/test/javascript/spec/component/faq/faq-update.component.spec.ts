import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { of, throwError } from 'rxjs';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../test.module';
import { FaqUpdateComponent } from 'app/faq/faq-update.component';
import { FaqService } from 'app/faq/faq.service';
import { Faq } from 'app/entities/faq.model';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { AlertService } from 'app/core/util/alert.service';
import { FaqCategory } from 'app/entities/faq-category.model';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';

describe('FaqUpdateComponent', () => {
    let faqUpdateComponentFixture: ComponentFixture<FaqUpdateComponent>;
    let faqUpdateComponent: FaqUpdateComponent;
    let faqService: FaqService;
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
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(ArtemisMarkdownEditorModule), MockModule(BrowserAnimationsModule)],
            declarations: [FaqUpdateComponent, MockPipe(HtmlForMarkdownPipe), MockRouterLinkDirective],
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
            ],
        }).compileComponents();

        faqUpdateComponentFixture = TestBed.createComponent(FaqUpdateComponent);
        faqUpdateComponent = faqUpdateComponentFixture.componentInstance;

        faqService = TestBed.inject(FaqService);
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

        faqUpdateComponentFixture.detectChanges();
        faqUpdateComponent.save();
        tick();

        expect(createSpy).toHaveBeenCalledExactlyOnceWith(courseId, { questionTitle: 'test1', faqState: 'PROPOSED' });
        expect(faqUpdateComponent.isSaving).toBeFalse();
    }));

    it('should edit a faq', fakeAsync(() => {
        activatedRoute.parent!.data = of({ course: { id: 1 }, faq: { id: 6 } });

        faqUpdateComponentFixture.detectChanges();
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
        faqUpdateComponentFixture.detectChanges();

        expect(updateSpy).toHaveBeenCalledExactlyOnceWith(courseId, { id: 6, questionTitle: 'test1Updated', faqState: 'PROPOSED' });
    }));

    it('should navigate to previous state', fakeAsync(() => {
        activatedRoute = TestBed.inject(ActivatedRoute);
        activatedRoute.parent!.data = of({ course: { id: 1 }, faq: { id: 6, questionTitle: '', course: { id: 1 } } });

        faqUpdateComponent.ngOnInit();
        faqUpdateComponentFixture.detectChanges();

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
        faqUpdateComponentFixture.detectChanges();
        faqUpdateComponent.updateCategories(categories);
        expect(faqUpdateComponent.faqCategories).toEqual(categories);
        expect(faqUpdateComponent.faq.categories).toEqual(categories);
    }));

    it('should not be able to save unless title and question are filled', fakeAsync(() => {
        faqUpdateComponentFixture.detectChanges();
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
});
