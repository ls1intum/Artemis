import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { Faq, FaqState, UpdateFaqDTO } from 'app/communication/shared/entities/faq.model';
import { faCancel, faCheck, faEdit, faFileImport, faFilter, faPencilAlt, faPlus, faQuestion, faSort, faTrash } from '@fortawesome/free-solid-svg-icons';
import { BehaviorSubject, Subject, Subscription } from 'rxjs';
import { debounceTime, map } from 'rxjs/operators';
import { AlertService } from 'app/foundation/service/alert.service';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { FaqService } from 'app/communication/faq/faq.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { onError } from 'app/foundation/util/global.utils';
import { FaqCategory } from 'app/communication/shared/entities/faq-category.model';
import { loadCourseFaqCategories } from 'app/communication/faq/faq.utils';
import { SortService } from 'app/foundation/service/sort.service';
import { SearchFilterComponent } from 'app/shared-ui/search-filter/search-filter.component';
import { AccountService } from 'app/core/auth/account.service';
import { Course } from 'app/course/shared/entities/course.model';
import { NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { SortByDirective } from 'app/foundation/sort/directive/sort-by.directive';
import { SortDirective } from 'app/foundation/sort/directive/sort.directive';
import { CommonModule } from '@angular/common';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { CustomExerciseCategoryBadgeComponent } from 'app/exercise/exercise-categories/custom-exercise-category-badge/custom-exercise-category-badge.component';
import { CourseTitleBarActionsDirective } from 'app/course/shared/directives/course-title-bar-actions.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FaqImportDialogComponent } from './import/faq-import-dialog.component';
import { TumAetUiButtonComponent, TumAetUiEmptyStateComponent } from '@tumaet/ui-angular';
@Component({
    selector: 'jhi-faq',
    templateUrl: './faq.component.html',
    styleUrls: [],
    imports: [
        CustomExerciseCategoryBadgeComponent,
        SearchFilterComponent,
        NgbDropdownModule,
        MarkdownDirective,
        TranslateDirective,
        FontAwesomeModule,
        DeleteButtonDirective,
        RouterModule,
        SortByDirective,
        SortDirective,
        CommonModule,
        CourseTitleBarActionsDirective,
        ArtemisTranslatePipe,
        TumAetUiEmptyStateComponent,
        TumAetUiButtonComponent,
        FaqImportDialogComponent,
    ],
})
export class FaqComponent implements OnInit, OnDestroy {
    private faqService = inject(FaqService);
    private route = inject(ActivatedRoute);
    private alertService = inject(AlertService);
    private sortService = inject(SortService);
    private accountService = inject(AccountService);

    readonly importDialogOpen = signal(false);
    protected readonly faFileImport = faFileImport;

    protected readonly FaqState = FaqState;
    faqs?: Faq[]; // undefined until loaded; code distinguishes "not loaded / load failed" from an empty result
    course!: Course; // set in ngOnInit() from the route data resolver
    readonly filteredFaqs = signal<Faq[]>([]);
    readonly loaded = signal(false);
    readonly hasFaqs = signal(false);
    readonly existingCategories = signal<FaqCategory[]>([]);
    readonly courseId = signal<number>(undefined!);
    readonly hasCategories = signal(false);
    readonly isAtLeastInstructor = signal(false);
    private managementAudience = false;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();
    private routeDataSubscription?: Subscription;

    readonly activeFilters = signal(new Set<string>());
    searchInput = new BehaviorSubject<string>('');
    predicate = 'id';
    ascending = true;

    // Icons
    protected readonly faEdit = faEdit;
    protected readonly faPlus = faPlus;
    protected readonly faTrash = faTrash;
    protected readonly faPencilAlt = faPencilAlt;
    protected readonly faFilter = faFilter;
    protected readonly faSort = faSort;
    protected readonly faCancel = faCancel;
    protected readonly faCheck = faCheck;
    protected readonly faQuestion = faQuestion;

    constructor() {
        this.predicate = 'id';
        this.ascending = true;
    }

    ngOnInit() {
        this.courseId.set(Number(this.route.snapshot.paramMap.get('courseId')));
        this.searchInput.pipe(debounceTime(300)).subscribe((searchTerm: string) => {
            this.refreshFaqList(searchTerm);
        });
        this.routeDataSubscription = this.route.data.subscribe((data) => {
            const course = data['course'];
            if (course) {
                this.course = course;
                this.isAtLeastInstructor.set(this.accountService.isAtLeastInstructorInCourse(course));
                this.managementAudience = this.accountService.isAtLeastTutorInCourse(course);
                this.loadAll();
                this.loadCourseFaqCategories(this.courseId());
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
        this.faqs = this.faqs?.filter((faq) => faq.id !== faqId);
        this.hasFaqs.set((this.faqs?.length ?? 0) > 0);
        this.refreshFaqList(this.searchInput.getValue());
        this.sortRows();
        this.dialogErrorSource.next('');
        this.loadCourseFaqCategories(this.courseId());
    }

    toggleFilters(category: string) {
        this.activeFilters.set(this.faqService.toggleFilter(category, this.activeFilters()));
        this.refreshFaqList(this.searchInput.getValue());
    }

    private applyFilters(): void {
        this.filteredFaqs.set(this.faqService.applyFilters(this.activeFilters(), this.faqs ?? []));
    }

    sortRows() {
        // sortByProperty sorts in place and returns the same array reference; spread into a new
        // array so the signal emits a fresh reference and the template re-renders (zoneless).
        this.filteredFaqs.set([...this.sortService.sortByProperty(this.filteredFaqs(), this.predicate, this.ascending)]);
    }

    /**
     * Reloads FAQs and categories after copying FAQs into this course and reports the created count.
     * Each reload reapplies the visible search and category filters independently of response order.
     * @param count number of FAQs created by the import
     */
    onFaqsImported(count: number): void {
        this.loadAll();
        this.loadCourseFaqCategories(this.courseId());
        this.alertService.success('artemisApp.faq.import.success', { count });
    }

    /** Reloads the course FAQs while preserving the active search, category filters, and sort order. */
    private loadAll() {
        const request = this.managementAudience ? this.faqService.findAllForManagement(this.courseId()) : this.faqService.findAllByCourseId(this.courseId());
        request.pipe(map((res: HttpResponse<Faq[]>) => res.body ?? [])).subscribe({
            next: (res: Faq[]) => {
                this.faqs = res;
                this.hasFaqs.set(res.length > 0);
                this.refreshFaqList(this.searchInput.getValue());
                this.sortRows();
                this.loaded.set(true);
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    /**
     * Reloads categories, removes filters for categories that no longer exist, and reapplies the current search and sort.
     * @param courseId course whose FAQ categories should be loaded
     */
    private loadCourseFaqCategories(courseId: number) {
        loadCourseFaqCategories(courseId, this.alertService, this.faqService, undefined, this.managementAudience ? 'management' : 'student').subscribe((existingCategories) => {
            this.existingCategories.set(existingCategories);
            this.hasCategories.set(existingCategories.length > 0);
            this.checkAppliedFilter(this.activeFilters(), this.existingCategories());
            this.refreshFaqList(this.searchInput.getValue());
            this.sortRows();
        });
    }

    /**
     * Prunes category filters that are absent from the reloaded category list without changing the search query.
     * @param activeFilters currently selected category names
     * @param existingCategories categories still present in the course
     */
    private checkAppliedFilter(activeFilters: Set<string>, existingCategories: FaqCategory[]) {
        const updatedFilters = new Set(activeFilters);
        updatedFilters.forEach((activeFilter) => {
            if (!existingCategories.some((category) => category.category === activeFilter)) {
                updatedFilters.delete(activeFilter);
            }
        });
        this.activeFilters.set(updatedFilters);
    }

    private applySearch(searchTerm: string) {
        this.filteredFaqs.set(
            this.filteredFaqs().filter((faq) => {
                return this.faqService.hasSearchTokens(faq, searchTerm);
            }),
        );
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
        this.faqService.update(courseId, UpdateFaqDTO.toUpdateDto(faq)).subscribe({
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
