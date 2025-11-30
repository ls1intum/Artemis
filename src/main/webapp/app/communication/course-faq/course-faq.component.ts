import { Component, ElementRef, OnDestroy, OnInit, Renderer2, ViewEncapsulation, effect, inject, viewChildren } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { debounceTime, map } from 'rxjs/operators';
import { BehaviorSubject } from 'rxjs';
import { faFilter } from '@fortawesome/free-solid-svg-icons';
import { ButtonType } from 'app/shared/components/buttons/button/button.component';

import { CourseFaqAccordionComponent } from 'app/communication/course-faq/course-faq-accordion-component';
import { Faq, FaqState } from 'app/communication/shared/entities/faq.model';
import { FaqService } from 'app/communication/faq/faq.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/shared/service/alert.service';
import { FaqCategory } from 'app/communication/shared/entities/faq-category.model';
import { loadCourseFaqCategories } from 'app/communication/faq/faq.utils';
import { onError } from 'app/shared/util/global.utils';
import { SearchFilterComponent } from 'app/shared/search-filter/search-filter.component';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { CommonModule } from '@angular/common';
import { CustomExerciseCategoryBadgeComponent } from 'app/exercise/exercise-categories/custom-exercise-category-badge/custom-exercise-category-badge.component';

@Component({
    selector: 'jhi-course-faq',
    templateUrl: './course-faq.component.html',
    styleUrls: ['../../core/course/overview/course-overview/course-overview.scss', 'course-faq.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [CourseFaqAccordionComponent, CustomExerciseCategoryBadgeComponent, SearchFilterComponent, NgbModule, TranslateDirective, FontAwesomeModule, CommonModule],
})
export class CourseFaqComponent implements OnInit, OnDestroy {
    faqElements = viewChildren<ElementRef>('faqElement');

    courseId: number;
    referencedFaqId: number;
    faqs: Faq[];
    faqState = FaqState.ACCEPTED;

    filteredFaqs: Faq[];
    existingCategories: FaqCategory[];
    activeFilters = new Set<string>();

    hasCategories = false;
    isCollapsed = false;

    searchInput = new BehaviorSubject<string>('');

    readonly ButtonType = ButtonType;
    readonly faFilter = faFilter;

    private route = inject(ActivatedRoute);
    private faqService = inject(FaqService);
    private alertService = inject(AlertService);
    private renderer = inject(Renderer2);

    constructor() {
        effect(() => {
            if (this.referencedFaqId) {
                this.scrollToFaq(this.referencedFaqId);
            }
        });
    }

    ngOnInit(): void {
        this.courseId = Number(this.route.parent!.snapshot.paramMap.get('courseId'));
        this.referencedFaqId = Number(this.route.snapshot.queryParamMap.get('faqId'));

        this.loadFaqs();
        this.loadCourseExerciseCategories(this.courseId);

        this.searchInput.pipe(debounceTime(300)).subscribe((searchTerm: string) => {
            this.refreshFaqList(searchTerm);
        });
    }

    private loadCourseExerciseCategories(courseId: number) {
        loadCourseFaqCategories(courseId, this.alertService, this.faqService, this.faqState).subscribe((existingCategories) => {
            this.existingCategories = existingCategories;
            this.hasCategories = existingCategories.length > 0;
        });
    }

    private loadFaqs() {
        this.faqService
            .findAllByCourseIdAndState(this.courseId, this.faqState)
            .pipe(map((res: HttpResponse<Faq[]>) => res.body))
            .subscribe({
                next: (res: Faq[]) => {
                    this.faqs = res;
                    this.applyFilters();
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    ngOnDestroy() {
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

    scrollToFaq(faqId: number): void {
        const faqElement = this.faqElements().find((faq) => faq.nativeElement.id === 'faq-' + String(faqId));
        if (faqElement) {
            this.renderer.selectRootElement(faqElement.nativeElement, true).scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    }
}
