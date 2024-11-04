import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Faq, FaqState } from 'app/entities/faq.model';
import { faCancel, faCheck, faEdit, faFilter, faPencilAlt, faPlus, faSort, faTrash } from '@fortawesome/free-solid-svg-icons';
import { BehaviorSubject, Subject, Subscription } from 'rxjs';
import { debounceTime, map } from 'rxjs/operators';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute } from '@angular/router';
import { FaqService } from 'app/faq/faq.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { FaqCategory } from 'app/entities/faq-category.model';
import { loadCourseFaqCategories } from 'app/faq/faq.utils';
import { SortService } from 'app/shared/service/sort.service';
import { CustomExerciseCategoryBadgeComponent } from 'app/shared/exercise-categories/custom-exercise-category-badge/custom-exercise-category-badge.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { SearchFilterComponent } from 'app/shared/search-filter/search-filter.component';
import { AccountService } from 'app/core/auth/account.service';
import { Course } from 'app/entities/course.model';
import { TranslateService } from '@ngx-translate/core';

@Component({
    selector: 'jhi-faq',
    templateUrl: './faq.component.html',
    styleUrls: [],
    standalone: true,
    imports: [ArtemisSharedModule, CustomExerciseCategoryBadgeComponent, ArtemisSharedComponentModule, ArtemisMarkdownModule, SearchFilterComponent],
})
export class FaqComponent implements OnInit, OnDestroy {
    protected readonly FaqState = FaqState;
    faqs: Faq[];
    course: Course;
    filteredFaqs: Faq[];
    existingCategories: FaqCategory[];
    courseId: number;
    hasCategories: boolean = false;
    isAtLeastInstructor = false;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();
    private routeDataSubscription: Subscription;

    activeFilters = new Set<string>();
    searchInput = new BehaviorSubject<string>('');
    predicate: string;
    ascending: boolean;

    // Icons
    protected readonly faEdit = faEdit;
    protected readonly faPlus = faPlus;
    protected readonly faTrash = faTrash;
    protected readonly faPencilAlt = faPencilAlt;
    protected readonly faFilter = faFilter;
    protected readonly faSort = faSort;
    protected readonly faCancel = faCancel;
    protected readonly faCheck = faCheck;

    private faqService = inject(FaqService);
    private route = inject(ActivatedRoute);
    private alertService = inject(AlertService);
    private sortService = inject(SortService);
    private accountService = inject(AccountService);
    private translateService = inject(TranslateService);

    constructor() {
        this.predicate = 'id';
        this.ascending = true;
    }

    ngOnInit() {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.loadAll();
        this.loadCourseFaqCategories(this.courseId);
        this.searchInput.pipe(debounceTime(300)).subscribe((searchTerm: string) => {
            this.refreshFaqList(searchTerm);
        });
        this.routeDataSubscription = this.route.data.subscribe((data) => {
            const course = data['course'];
            if (course) {
                this.course = course;
                this.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(course);
            }
        });
    }

    ngOnDestroy(): void {
        this.dialogErrorSource.complete();
        this.searchInput.complete();
        this.routeDataSubscription?.unsubscribe();
    }

    deleteFaq(courseId: number, faqId: number) {
        this.faqService.delete(courseId, faqId).subscribe({
            next: () => this.handleDeleteSuccess(faqId),
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    private handleDeleteSuccess(faqId: number) {
        this.faqs = this.faqs.filter((faq) => faq.id !== faqId);
        this.dialogErrorSource.next('');
        this.loadCourseFaqCategories(this.courseId);
    }

    toggleFilters(category: string) {
        this.activeFilters = this.faqService.toggleFilter(category, this.activeFilters);
        this.refreshFaqList(this.searchInput.getValue());
    }

    private applyFilters(): void {
        this.filteredFaqs = this.faqService.applyFilters(this.activeFilters, this.faqs);
    }

    sortRows() {
        this.sortService.sortByProperty(this.filteredFaqs, this.predicate, this.ascending);
    }

    private loadAll() {
        this.faqService
            .findAllByCourseId(this.courseId)
            .pipe(map((res: HttpResponse<Faq[]>) => res.body))
            .subscribe({
                next: (res: Faq[]) => {
                    this.faqs = res;
                    this.applyFilters();
                    this.sortRows();
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    private loadCourseFaqCategories(courseId: number) {
        loadCourseFaqCategories(courseId, this.alertService, this.faqService).subscribe((existingCategories) => {
            this.existingCategories = existingCategories;
            this.hasCategories = existingCategories.length > 0;
            this.checkAppliedFilter(this.activeFilters, this.existingCategories);
        });
    }

    private checkAppliedFilter(activeFilters: Set<string>, existingCategories: FaqCategory[]) {
        activeFilters.forEach((activeFilter) => {
            if (!existingCategories.some((category) => category.category === activeFilter)) {
                activeFilters.delete(activeFilter);
            }
        });
        this.applyFilters();
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

    updateFaqState(courseId: number, faq: Faq, newState: FaqState, successMessageKey: string) {
        const previousState = faq.faqState;
        faq.faqState = newState;
        faq.course = this.course;
        this.faqService.update(courseId, faq).subscribe({
            next: () => this.alertService.success(successMessageKey, { title: faq.questionTitle }),
            error: (error: HttpErrorResponse) => {
                this.dialogErrorSource.next(error.message);
                faq.faqState = previousState;
            },
        });
    }

    rejectFaq(courseId: number, faq: Faq) {
        this.updateFaqState(courseId, faq, FaqState.REJECTED, 'artemisApp.faq.rejected');
    }

    acceptProposedFaq(courseId: number, faq: Faq) {
        this.updateFaqState(courseId, faq, FaqState.ACCEPTED, 'artemisApp.faq.accepted');
    }
}
