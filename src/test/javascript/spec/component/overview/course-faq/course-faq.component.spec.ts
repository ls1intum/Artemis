import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { of, throwError } from 'rxjs';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { CourseFaqComponent } from 'app/overview/course-faq/course-faq.component';
import { AlertService } from 'app/core/util/alert.service';
import { FaqService } from 'app/faq/faq.service';
import { MockRouter } from '../../../helpers/mocks/mock-router';

import { CustomExerciseCategoryBadgeComponent } from 'app/shared/exercise-categories/custom-exercise-category-badge/custom-exercise-category-badge.component';
import { CourseFaqAccordionComponent } from 'app/overview/course-faq/course-faq-accordion-component';
import { Faq, FaqState } from 'app/entities/faq.model';
import { FaqCategory } from 'app/entities/faq-category.model';
import { SearchFilterComponent } from 'app/shared/search-filter/search-filter.component';
import { SortService } from 'app/shared/service/sort.service';
import { ElementRef, signal } from '@angular/core';

function createFaq(id: number, category: string, color: string): Faq {
    const faq = new Faq();
    faq.id = id;
    faq.questionTitle = 'questionTitle ' + id;
    faq.questionAnswer = 'questionAnswer ' + id;
    faq.categories = [new FaqCategory(category, color)];
    return faq;
}

describe('CourseFaqs', () => {
    let courseFaqComponentFixture: ComponentFixture<CourseFaqComponent>;
    let courseFaqComponent: CourseFaqComponent;

    let faqService: FaqService;
    let alertServiceStub: jest.SpyInstance;
    let alertService: AlertService;
    let sortService: SortService;

    let faq1: Faq;
    let faq2: Faq;
    let faq3: Faq;

    beforeEach(() => {
        // In beforeEach:
        faq1 = createFaq(1, 'category1', '#94a11c');
        faq2 = createFaq(2, 'category2', '#0ab84f');
        faq3 = createFaq(3, 'category3', '#0ab84f');

        TestBed.configureTestingModule({
            imports: [MockComponent(CustomExerciseCategoryBadgeComponent), MockComponent(CourseFaqAccordionComponent)],
            declarations: [
                CourseFaqComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(FaIconComponent),
                MockDirective(TranslateDirective),
                MockComponent(SearchFilterComponent),
            ],
            providers: [
                MockProvider(FaqService),
                { provide: Router, useClass: MockRouter },
                { provide: TranslateService, useClass: MockTranslateService },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            params: of({ courseId: '1' }),
                        },
                        queryParams: of({ faqId: '1' }),
                    },
                },
                MockProvider(FaqService, {
                    findAllByCourseIdAndState: () => {
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
                    hasSearchTokens: () => {
                        return true;
                    },
                }),
            ],
        })
            .compileComponents()
            .then(() => {
                courseFaqComponentFixture = TestBed.createComponent(CourseFaqComponent);
                courseFaqComponent = courseFaqComponentFixture.componentInstance;

                faqService = TestBed.inject(FaqService);
                alertService = TestBed.inject(AlertService);
                sortService = TestBed.inject(SortService);
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
        const findAllSpy = jest.spyOn(faqService, 'findAllByCourseIdAndState');

        courseFaqComponentFixture.detectChanges();
        expect(findAllSpy).toHaveBeenCalledExactlyOnceWith(1, FaqState.ACCEPTED);
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

    it('should search through already filtered array', () => {
        const searchSpy = jest.spyOn(faqService, 'hasSearchTokens');
        const applyFilterSpy = jest.spyOn(faqService, 'applyFilters');
        courseFaqComponent.setSearchValue('questionTitle');
        courseFaqComponent.refreshFaqList(courseFaqComponent.searchInput.getValue());
        expect(applyFilterSpy).toHaveBeenCalledOnce();
        expect(searchSpy).toHaveBeenCalledTimes(2);
        expect(searchSpy).toHaveBeenCalledWith(faq2, 'questionTitle');
        expect(searchSpy).toHaveBeenCalledWith(faq3, 'questionTitle');
        expect(courseFaqComponent.filteredFaqs).toHaveLength(2);
        expect(courseFaqComponent.filteredFaqs).not.toContain(faq1);
        expect(courseFaqComponent.filteredFaqs).toEqual([faq2, faq3]);
    });

    it('should catch error if no categories are found', () => {
        alertServiceStub = jest.spyOn(alertService, 'error');
        const error = { status: 404 };
        jest.spyOn(faqService, 'findAllCategoriesByCourseIdAndCategory').mockReturnValue(throwError(() => new HttpErrorResponse(error)));
        courseFaqComponentFixture.detectChanges();
        expect(alertServiceStub).toHaveBeenCalledOnce();
    });

    it('should call sortService when sortRows is called', () => {
        jest.spyOn(sortService, 'sortByProperty').mockReturnValue([]);
        courseFaqComponent.sortFaqs();
        expect(sortService.sortByProperty).toHaveBeenCalledOnce();
    });

    it('should scroll and focus on the faq element with given id', () => {
        const nativeElement1 = { id: 'faq-1', scrollIntoView: jest.fn(), focus: jest.fn() };
        const nativeElement2 = { id: 'faq-2', scrollIntoView: jest.fn(), focus: jest.fn() };

        const elementRef1 = new ElementRef(nativeElement1);
        const elementRef2 = new ElementRef(nativeElement2);

        courseFaqComponent.faqElements = signal([elementRef1, elementRef2]);

        courseFaqComponent.scrollToFaq(1);

        expect(nativeElement1.scrollIntoView).toHaveBeenCalledExactlyOnceWith({ behavior: 'smooth', block: 'start' });
    });
});
