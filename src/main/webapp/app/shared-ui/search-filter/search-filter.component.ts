import { Component, DestroyRef, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';

@Component({
    selector: 'jhi-search-filter',
    templateUrl: './search-filter.component.html',
    styleUrls: ['./search-filter.component.scss'],
    imports: [IconFieldModule, InputIconModule, InputTextModule],
})
export class SearchFilterComponent implements OnInit {
    private readonly translateService = inject(TranslateService);
    private readonly destroyRef = inject(DestroyRef);

    readonly placeholderKey = input<string>('artemisApp.course.exercise.search.searchPlaceholder');
    readonly disabled = input(false);
    readonly newSearchEvent = output<string>();

    readonly searchValue = signal('');

    /** Bumped on language / translation changes so the (eagerly resolved) placeholder re-translates in this zoneless app. */
    private readonly languageChange = signal(0);
    readonly placeholder = computed(() => {
        this.languageChange();
        return this.translateService.instant(this.placeholderKey());
    });

    ngOnInit(): void {
        // Subscribed in ngOnInit (not the constructor) so that partial TranslateService doubles used in some specs are
        // not touched on mere construction. See TranslateDirective for the same rationale.
        this.translateService.onLangChange.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.languageChange.update((version) => version + 1));
        this.translateService.onTranslationChange.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.languageChange.update((version) => version + 1));
    }

    setSearchValue(value: string) {
        this.searchValue.set(value);
        this.newSearchEvent.emit(value);
    }

    resetSearchValue() {
        this.searchValue.set('');
        this.newSearchEvent.emit('');
    }
}
