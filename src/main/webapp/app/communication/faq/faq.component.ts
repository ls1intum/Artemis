import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { Faq, FaqState, UpdateFaqDTO } from 'app/communication/shared/entities/faq.model';
import { faCancel, faCheck, faFileImport, faFilter, faPencilAlt, faPlus, faQuestion, faTrash } from '@fortawesome/free-solid-svg-icons';
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
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { CustomExerciseCategoryBadgeComponent } from 'app/exercise/exercise-categories/custom-exercise-category-badge/custom-exercise-category-badge.component';
import { CourseTitleBarActionsDirective } from 'app/course/shared/directives/course-title-bar-actions.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FaqImportDialogComponent } from './import/faq-import-dialog.component';
import { TumAetUiButtonComponent, TumAetUiButtonDirective } from '@tumaet/ui-angular/button';
import { TumAetUiCheckboxComponent } from '@tumaet/ui-angular/checkbox';
import { TumAetUiEmptyStateComponent } from '@tumaet/ui-angular/empty-state';
import { TumAetUiPopoverComponent, TumAetUiPopoverTriggerDirective } from '@tumaet/ui-angular/popover';
import { TumAetUiTableDirective, TumAetUiTableSortEvent, TumAetUiTableSortableColumnComponent } from '@tumaet/ui-angular/table-directive';
import { TumAetUiTableLayoutComponent } from '@tumaet/ui-angular/table-layout';

@Component({
    selector: 'jhi-faq',
    templateUrl: './faq.component.html',
    imports: [
        TumAetUiButtonDirective,
        CustomExerciseCategoryBadgeComponent,
        SearchFilterComponent,
        MarkdownDirective,
        TranslateDirective,
        FontAwesomeModule,
        DeleteButtonDirective,
        RouterModule,
        TumAetUiTableDirective,
        TumAetUiTableLayoutComponent,
        TumAetUiTableSortableColumnComponent,
        CourseTitleBarActionsDirective,
        ArtemisTranslatePipe,
        TumAetUiEmptyStateComponent,
        TumAetUiButtonComponent,
        TumAetUiCheckboxComponent,
        TumAetUiPopoverComponent,
        TumAetUiPopoverTriggerDirective,
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
    /** Every FAQ of the course, whatever the search and the filters show. The actions column depends on it, so it does not come and go while the student types. */
    private readonly allFaqs = signal<Faq[]>([]);
    /** Without any row action (a tutor sees none on accepted FAQs) the table has no empty column at its end. */
    protected readonly hasRowActions = computed(() => this.isAtLeastInstructor() || this.allFaqs().some((faq) => faq.faqState !== FaqState.ACCEPTED));
    private managementAudience = false;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();
    private routeDataSubscription?: Subscription;

    readonly activeFilters = signal(new Set<string>());
    searchInput = new BehaviorSubject<string>('');
    readonly predicate = signal('id');
    readonly ascending = signal(true);

    // Icons
    protected readonly faPlus = faPlus;
    protected readonly faTrash = faTrash;
    protected readonly faPencilAlt = faPencilAlt;
    protected readonly faFilter = faFilter;
    protected readonly faCancel = faCancel;
    protected readonly faCheck = faCheck;
    protected readonly faQuestion = faQuestion;

    constructor() {
        this.predicate.set('id');
        this.ascending.set(true);
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
        this.allFaqs.set(this.faqs ?? []);
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

    onSort(event: TumAetUiTableSortEvent): void {
        this.predicate.set(event.field);
        this.ascending.set(event.order === 1);
        this.sortRows();
    }

    sortRows() {
        // sortByProperty sorts in place and returns the same array reference; spread into a new
        // array so the signal emits a fresh reference and the template re-renders (zoneless).
        this.filteredFaqs.set([...this.sortService.sortByProperty(this.filteredFaqs(), this.predicate(), this.ascending())]);
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
                this.allFaqs.set(res);
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
