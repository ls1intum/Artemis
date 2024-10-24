import { Component, ElementRef, OnDestroy, OnInit, ViewEncapsulation, effect, inject, viewChildren } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { debounceTime, map, takeUntil } from 'rxjs/operators';
import { BehaviorSubject, Subject, Subscription } from 'rxjs';
import { faFilter } from '@fortawesome/free-solid-svg-icons';
import { ButtonType } from 'app/shared/components/button.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CourseFaqAccordionComponent } from 'app/overview/course-faq/course-faq-accordion-component';
import { Faq, FaqState } from 'app/entities/faq.model';
import { FaqService } from 'app/faq/faq.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { FaqCategory } from 'app/entities/faq-category.model';
import { loadCourseFaqCategories } from 'app/faq/faq.utils';
import { CustomExerciseCategoryBadgeComponent } from 'app/shared/exercise-categories/custom-exercise-category-badge/custom-exercise-category-badge.component';
import { onError } from 'app/shared/util/global.utils';
import { SearchFilterComponent } from 'app/shared/search-filter/search-filter.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { SortService } from 'app/shared/service/sort.service';
import { Renderer2 } from '@angular/core';

@Component({
    selector: 'jhi-course-faq',
    templateUrl: './course-faq.component.html',
    styleUrls: ['../course-overview.scss', 'course-faq.component.scss'],
    encapsulation: ViewEncapsulation.None,
    standalone: true,
    imports: [ArtemisSharedComponentModule, ArtemisSharedModule, CourseFaqAccordionComponent, CustomExerciseCategoryBadgeComponent, SearchFilterComponent, ArtemisMarkdownModule],
})
export class CourseFaqComponent implements OnInit, OnDestroy {
    faqElements = viewChildren<ElementRef>('faqElement');
    private ngUnsubscribe = new Subject<void>();
    private parentParamSubscription: Subscription;

    courseId: number;
    faqId: number;
    faqs: Faq[];

    filteredFaqs: Faq[];
    existingCategories: FaqCategory[];
    activeFilters = new Set<string>();

    hasCategories = false;
    isCollapsed = false;

    searchInput = new BehaviorSubject<string>('');

    readonly ButtonType = ButtonType;

    // Icons
    readonly faFilter = faFilter;

    private route = inject(ActivatedRoute);

    private faqService = inject(FaqService);
    private alertService = inject(AlertService);
    private sortService = inject(SortService);
    private renderer = inject(Renderer2);

    constructor() {
        effect(() => {
            if (this.faqId) {
                this.scrollToFaq(this.faqId);
            }
        });
    }

    ngOnInit(): void {
        this.parentParamSubscription = this.route.parent!.params.subscribe((params) => {
            this.courseId = Number(params.courseId);
            this.loadFaqs();
            this.loadCourseExerciseCategories(this.courseId);
        });

        this.route.queryParams.pipe(takeUntil(this.ngUnsubscribe)).subscribe((params) => {
            this.faqId = params['faqId'];
        });

        this.searchInput.pipe(debounceTime(300)).subscribe((searchTerm: string) => {
            this.refreshFaqList(searchTerm);
        });
    }

    private loadCourseExerciseCategories(courseId: number) {
        loadCourseFaqCategories(courseId, this.alertService, this.faqService).subscribe((existingCategories) => {
            this.existingCategories = existingCategories;
            this.hasCategories = existingCategories.length > 0;
        });
    }

    private loadFaqs() {
        this.faqService
            .findAllByCourseIdAndState(this.courseId, FaqState.ACCEPTED)
            .pipe(map((res: HttpResponse<Faq[]>) => res.body))
            .subscribe({
                next: (res: Faq[]) => {
                    this.faqs = res;
                    this.applyFilters();
                    this.sortFaqs();
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    ngOnDestroy() {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
        this.parentParamSubscription?.unsubscribe();
        this.searchInput.complete();
    }

    toggleFilters(category: string) {
        this.activeFilters = this.faqService.toggleFilter(category, this.activeFilters);
        this.refreshFaqList(this.searchInput.getValue());
    }

    private applyFilters(): void {
        this.filteredFaqs = this.faqService.applyFilters(this.activeFilters, this.faqs);
    }

    private applySearch(searchTerm: string) {
        this.filteredFaqs = this.filteredFaqs.filter((faq) => {
            return this.faqService.hasSearchTokens(faq, searchTerm);
        });
    }

    setSearchValue(searchValue: string) {
        this.searchInput.next(searchValue);
    }

    refreshFaqList(searchTerm: string) {
        this.applyFilters();
        this.applySearch(searchTerm);
    }

    sortFaqs() {
        this.sortService.sortByProperty(this.filteredFaqs, 'id', true);
    }

    scrollToFaq(faqId: number): void {
        const faqElement = this.faqElements().find((faq) => faq.nativeElement.id === 'faq-' + String(faqId));
        if (faqElement) {
            this.renderer.selectRootElement(faqElement.nativeElement).scrollIntoView({ behavior: 'smooth', block: 'start' });
            this.renderer.selectRootElement(faqElement.nativeElement).focus();
        }
    }
}
