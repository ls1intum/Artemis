import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HttpResponse } from '@angular/common/http';
import { CourseFaqComponent } from 'app/overview/course-faq/course-faq.component';
import { AlertService } from 'app/core/util/alert.service';
import { FAQService } from 'app/faq/faq.service';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CustomExerciseCategoryBadgeComponent } from 'app/shared/exercise-categories/custom-exercise-category-badge/custom-exercise-category-badge.component';
import { CourseFaqAccordionComponent } from 'app/overview/course-faq/course-faq-accordion-component';
import { FAQ } from 'app/entities/faq.model';
import { FAQCategory } from 'app/entities/faq-category.model';

describe('CourseFaqs', () => {
    let courseFaqComponentFixture: ComponentFixture<CourseFaqComponent>;
    let courseFaqComponent: CourseFaqComponent;

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
            imports: [ArtemisSharedComponentModule, ArtemisSharedModule, MockComponent(CustomExerciseCategoryBadgeComponent), MockComponent(CourseFaqAccordionComponent)],
            declarations: [CourseFaqComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent), MockDirective(TranslateDirective)],
            providers: [
                MockProvider(AlertService),
                MockProvider(FAQService),
                { provide: Router, useClass: MockRouter },
                { provide: TranslateService, useClass: MockTranslateService },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            params: of({ courseId: '1' }),
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
        })
            .compileComponents()
            .then(() => {
                courseFaqComponentFixture = TestBed.createComponent(CourseFaqComponent);
                courseFaqComponent = courseFaqComponentFixture.componentInstance;

                faqService = TestBed.inject(FAQService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        courseFaqComponentFixture.detectChanges();
        expect(courseFaqComponent).not.toBeNull();
        courseFaqComponent.ngOnDestroy();
    });

    it('should fetch faqs when initialized', () => {
        const findAllSpy = jest.spyOn(faqService, 'findAllByCourseId');

        courseFaqComponentFixture.detectChanges();
        expect(findAllSpy).toHaveBeenCalledOnce();
        expect(findAllSpy).toHaveBeenCalledWith(1);
        expect(courseFaqComponent.faqs).toHaveLength(3);
    });

    it('should toggle filter correctly', () => {
        const toggleFilterSpy = jest.spyOn(faqService, 'toggleFilter');
        courseFaqComponentFixture.detectChanges();
        courseFaqComponent.toggleFilters('category2');
        expect(toggleFilterSpy).toHaveBeenCalledOnce();
        expect(courseFaqComponent.filteredFaqs).toHaveLength(2);
        expect(courseFaqComponent.filteredFaqs).not.toContain(faq1);
        expect(courseFaqComponent.filteredFaqs).toEqual([faq2, faq3]);
    });
});
