import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { Faq, FaqState, UpdateFaqDTO } from 'app/communication/shared/entities/faq.model';
import { faCancel, faCheck, faEdit, faFileExport, faFilter, faPencilAlt, faPlus, faQuestion, faSort, faTrash } from '@fortawesome/free-solid-svg-icons';
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
import { MODULE_FEATURE_IRIS } from 'app/app.constants';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
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
    ],
})
export class FaqComponent implements OnInit, OnDestroy {
    protected readonly FaqState = FaqState;
    faqs: Faq[];
    course: Course;
    readonly filteredFaqs = signal<Faq[]>([]);
    readonly existingCategories = signal<FaqCategory[]>([]);
    readonly courseId = signal<number>(undefined!);
    readonly hasCategories = signal(false);
    readonly isAtLeastInstructor = signal(false);
    readonly irisEnabled = signal(false);

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();
    private routeDataSubscription: Subscription;

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
    protected readonly faFileExport = faFileExport;
    protected readonly faQuestion = faQuestion;

    private faqService = inject(FaqService);
    private route = inject(ActivatedRoute);
    private alertService = inject(AlertService);
    private sortService = inject(SortService);
    private accountService = inject(AccountService);
    private profileService = inject(ProfileService);
    private irisSettingsService = inject(IrisSettingsService);

    private profileInfoSubscription: Subscription;

    constructor() {
        this.predicate = 'id';
        this.ascending = true;
    }

    ngOnInit() {
        this.courseId.set(Number(this.route.snapshot.paramMap.get('courseId')));
        this.loadAll();
        this.loadCourseFaqCategories(this.courseId());
        this.searchInput.pipe(debounceTime(300)).subscribe((searchTerm: string) => {
            this.refreshFaqList(searchTerm);
        });
        this.routeDataSubscription = this.route.data.subscribe((data) => {
            const course = data['course'];
            if (course) {
                this.course = course;
                this.isAtLeastInstructor.set(this.accountService.isAtLeastInstructorInCourse(course));
            }
        });
        const irisEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_IRIS);
        if (irisEnabled) {
            this.irisSettingsService.getCourseSettingsWithRateLimit(this.courseId()).subscribe((response) => {
                this.irisEnabled.set(response?.settings?.enabled || false);
            });
        }
    }

    ngOnDestroy(): void {
        this.dialogErrorSource.complete();
        this.searchInput.complete();
        this.routeDataSubscription?.unsubscribe();
        this.profileInfoSubscription?.unsubscribe();
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
        this.loadCourseFaqCategories(this.courseId());
    }

    toggleFilters(category: string) {
        this.activeFilters.set(this.faqService.toggleFilter(category, this.activeFilters()));
        this.refreshFaqList(this.searchInput.getValue());
    }

    private applyFilters(): void {
        this.filteredFaqs.set(this.faqService.applyFilters(this.activeFilters(), this.faqs));
    }

    sortRows() {
        // sortByProperty sorts in place and returns the same array reference; spread into a new
        // array so the signal emits a fresh reference and the template re-renders (zoneless).
        this.filteredFaqs.set([...this.sortService.sortByProperty(this.filteredFaqs(), this.predicate, this.ascending)]);
    }

    private loadAll() {
        this.faqService
            .findAllByCourseId(this.courseId())
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
            this.existingCategories.set(existingCategories);
            this.hasCategories.set(existingCategories.length > 0);
            this.checkAppliedFilter(this.activeFilters(), this.existingCategories());
        });
    }

    private checkAppliedFilter(activeFilters: Set<string>, existingCategories: FaqCategory[]) {
        const updatedFilters = new Set(activeFilters);
        updatedFilters.forEach((activeFilter) => {
            if (!existingCategories.some((category) => category.category === activeFilter)) {
                updatedFilters.delete(activeFilter);
            }
        });
        this.activeFilters.set(updatedFilters);
        this.applyFilters();
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

    ingestFaqsInPyris() {
        if (this.faqs.first()) {
            this.faqService.ingestFaqsInPyris(this.courseId()).subscribe({
                next: () => this.alertService.success('artemisApp.iris.ingestionAlert.allFaqsSuccess'),
                error: () => {
                    this.alertService.error('artemisApp.iris.ingestionAlert.allFaqsError');
                },
            });
        }
    }
}
