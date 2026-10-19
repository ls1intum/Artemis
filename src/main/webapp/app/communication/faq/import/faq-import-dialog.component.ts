import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, input, model, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Subject, Subscription, debounceTime } from 'rxjs';
import { faFileImport, faSearch } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import {
    TumAetUiButtonComponent,
    TumAetUiCheckboxComponent,
    TumAetUiDialogComponent,
    TumAetUiEmptyStateComponent,
    TumAetUiInputDirective,
    TumAetUiPaginatorComponent,
} from '@tumaet/ui-angular';
import { FaqService } from 'app/communication/faq/faq.service';
import { FaqImportCandidate } from 'app/communication/shared/entities/faq.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';

/** Searches visible FAQs and keeps the user's selection when changing the query or page. */
@Component({
    selector: 'jhi-faq-import-dialog',
    templateUrl: './faq-import-dialog.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        FormsModule,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        MarkdownDirective,
        TumAetUiButtonComponent,
        TumAetUiCheckboxComponent,
        TumAetUiDialogComponent,
        TumAetUiEmptyStateComponent,
        TumAetUiInputDirective,
        TumAetUiPaginatorComponent,
    ],
})
export class FaqImportDialogComponent implements OnInit {
    private readonly faqService = inject(FaqService);
    private readonly destroyRef = inject(DestroyRef);

    readonly courseId = input.required<number>();
    readonly visible = model(true);
    readonly imported = output<number>();

    protected readonly faFileImport = faFileImport;
    protected readonly faSearch = faSearch;
    protected readonly searchTerm = signal('');
    protected readonly faqs = signal<FaqImportCandidate[]>([]);
    protected readonly selectedIds = signal(new Set<number>());
    protected readonly selectedCount = computed(() => this.selectedIds().size);
    protected readonly page = signal(0);
    protected readonly totalElements = signal(0);
    protected readonly loading = signal(false);
    protected readonly importing = signal(false);
    protected readonly searchFailed = signal(false);
    protected readonly importFailed = signal(false);
    protected readonly allOnPageSelected = computed(() => this.faqs().length > 0 && this.faqs().every((faq) => this.selectedIds().has(faq.id)));
    protected readonly someOnPageSelected = computed(() => !this.allOnPageSelected() && this.faqs().some((faq) => this.selectedIds().has(faq.id)));

    private readonly searchChanges = new Subject<void>();
    private searchSubscription?: Subscription;

    /** Debounces query changes and cancels the active request when the dialog is destroyed. */
    constructor() {
        this.searchChanges.pipe(debounceTime(300), takeUntilDestroyed()).subscribe(() => this.loadPage(0));
        this.destroyRef.onDestroy(() => this.searchSubscription?.unsubscribe());
    }

    /** Loads the initial result page once the target course input is available. */
    ngOnInit(): void {
        this.loadPage(0);
    }

    /**
     * Cancels results for the previous query immediately and schedules a search from the first page.
     * Existing selections remain available across queries.
     * @param value the current search input
     */
    protected changeSearch(value: string): void {
        this.searchTerm.set(value);
        // Cancel immediately so an earlier request cannot replace results while the next query is debounced.
        this.searchSubscription?.unsubscribe();
        this.faqs.set([]);
        this.loading.set(true);
        this.searchChanges.next();
    }

    /**
     * Replaces the displayed page, cancelling any older request so its response cannot overwrite the results.
     * A failed request leaves the selection intact and exposes the retry state.
     * @param page zero-based result page
     */
    protected loadPage(page: number): void {
        this.searchSubscription?.unsubscribe();
        this.page.set(page);
        this.loading.set(true);
        this.searchFailed.set(false);
        this.faqs.set([]);
        this.searchSubscription = this.faqService.searchForImport(this.courseId(), this.searchTerm().trim(), page).subscribe({
            next: (result) => {
                this.faqs.set(result.resultsOnPage);
                this.totalElements.set(result.totalElements);
                this.loading.set(false);
            },
            error: () => {
                this.loading.set(false);
                this.searchFailed.set(true);
            },
        });
    }

    /**
     * Updates one FAQ selection without affecting selections on other pages or for other queries.
     * @param id source FAQ ID
     * @param checked whether the FAQ should be included in the import
     */
    protected toggleFaq(id: number, checked: boolean): void {
        this.selectedIds.update((previous) => {
            const next = new Set(previous);
            if (checked) {
                next.add(id);
            } else {
                next.delete(id);
            }
            return next;
        });
        this.importFailed.set(false);
    }

    /**
     * Selects or deselects the displayed results while preserving selections outside the current page.
     * @param checked whether all displayed FAQs should be included in the import
     */
    protected togglePage(checked: boolean): void {
        this.selectedIds.update((previous) => {
            const next = new Set(previous);
            for (const faq of this.faqs()) {
                if (checked) {
                    next.add(faq.id);
                } else {
                    next.delete(faq.id);
                }
            }
            return next;
        });
        this.importFailed.set(false);
    }

    /** Clears the complete selection, including FAQs hidden by pagination or the current search. */
    protected clearSelection(): void {
        this.selectedIds.set(new Set());
        this.importFailed.set(false);
    }

    /**
     * Submits the selected IDs once, ignoring empty selections and concurrent submissions.
     * Success emits the number of created copies and closes the dialog; failure preserves the selection for retry.
     */
    protected importSelected(): void {
        if (!this.selectedCount() || this.importing()) {
            return;
        }
        this.importing.set(true);
        this.importFailed.set(false);
        this.faqService
            .importFaqs(this.courseId(), [...this.selectedIds()])
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (response) => {
                    this.importing.set(false);
                    this.imported.emit(response.body?.length ?? 0);
                    this.visible.set(false);
                },
                error: () => {
                    this.importing.set(false);
                    this.importFailed.set(true);
                },
            });
    }
}
