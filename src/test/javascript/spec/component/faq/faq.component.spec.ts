import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { MockComponent, MockModule, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../test.module';
import { FAQService } from 'app/faq/faq.service';
import { FAQ } from 'app/entities/faq.model';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { MockResizeObserver } from '../../helpers/mocks/service/mock-resize-observer';

import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { FAQComponent } from 'app/faq/faq.component';
import { FAQCategory } from 'app/entities/faq-category.model';
import { CustomExerciseCategoryBadgeComponent } from 'app/shared/exercise-categories/custom-exercise-category-badge/custom-exercise-category-badge.component';

describe('FaqComponent', () => {
    let faqComponentFixture: ComponentFixture<FAQComponent>;
    let faqComponent: FAQComponent;

    let faqService: FAQService;

    let faq1: FAQ;
    let faq2: FAQ;
    let faq3: FAQ;

    beforeEach(() => {
        faq1 = new FAQ();
        faq1.id = 1;
        faq1.questionTitle = 'questionTitle';
        faq1.questionAnswer = 'questionAnswer';
        faq1.categories = [new FAQCategory('category1', '#94a11c')];

        faq2 = new FAQ();
        faq2.id = 2;
        faq2.questionTitle = 'questionTitle';
        faq2.questionAnswer = 'questionAnswer';
        faq2.categories = [new FAQCategory('category2', '#0ab84f')];

        faq3 = new FAQ();
        faq3.id = 3;
        faq3.questionTitle = 'questionTitle';
        faq3.questionAnswer = 'questionAnswer';
        faq3.categories = [new FAQCategory('category3', '#0ab84f')];
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisMarkdownEditorModule, MockModule(BrowserAnimationsModule)],
            declarations: [FAQComponent, MockRouterLinkDirective, MockComponent(CustomExerciseCategoryBadgeComponent)],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            data: of({ course: { id: 1 } }),
                        },
                        queryParams: of({
                            params: {},
                        }),
                        snapshot: {
                            paramMap: convertToParamMap({
                                courseId: '1',
                            }),
                        },
                    },
                },
                MockProvider(FAQService, {
                    findAllByCourseId: () => {
                        return of(
                            new HttpResponse({
                                body: [faq1, faq2, faq3],
                                status: 200,
                            }),
                        );
                    },
                    delete: () => {
                        return of(new HttpResponse({ status: 200 }));
                    },
                    findAllCategoriesByCourseId: () => {
                        return of(
                            new HttpResponse({
                                body: [],
                                status: 200,
                            }),
                        );
                    },
                    applyFilters: () => {
                        return [faq2, faq3];
                    },
                }),
            ],
        }).compileComponents();

        global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
            return new MockResizeObserver(callback);
        });
        faqComponentFixture = TestBed.createComponent(FAQComponent);
        faqComponent = faqComponentFixture.componentInstance;

        faqService = TestBed.inject(FAQService);
        faqComponentFixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should fetch faqs when initialized', () => {
        const findAllSpy = jest.spyOn(faqService, 'findAllByCourseId');

        faqComponentFixture.detectChanges();
        //is actually called when debugging, i dont get why it is 0. Need help
        expect(findAllSpy).toHaveBeenCalledOnce();
        expect(findAllSpy).toHaveBeenCalledWith(1);
        expect(faqComponent.faqs).toBeArrayOfSize(3);
    });

    it('should delete faq', () => {
        const deleteSpy = jest.spyOn(faqService, 'delete');

        faqComponentFixture.detectChanges();
        faqComponent.deleteFaq(faq1.id!);

        expect(deleteSpy).toHaveBeenCalledOnce();
        expect(deleteSpy).toHaveBeenCalledWith(faq1.id!);
        expect(faqComponent.faqs).toBeArrayOfSize(2);
        expect(faqComponent.faqs).not.toContain(faq1);
        expect(faqComponent.faqs).toEqual(faqComponent.filteredFaqs);
    });
});
