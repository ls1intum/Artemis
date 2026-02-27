import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowLeft, faFileLines } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { LectureSearchResult } from 'app/core/navbar/global-search/models/lecture-search-result.model';
import { LectureSearchService } from 'app/core/navbar/global-search/services/lecture-search.service';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { debounceTime, filter, switchMap } from 'rxjs';

@Component({
    selector: 'jhi-global-search-lecture-results',
    standalone: true,
    templateUrl: 'global-search-lecture-results.component.html',
    styleUrls: ['./global-search-lecture-results.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe, FaIconComponent],
})
export class GlobalSearchLectureResultsComponent {
    protected readonly back = output<void>();
    readonly searchQuery = input.required<string>();
    private readonly searchService = inject(LectureSearchService);
    protected readonly lectureResults = signal<LectureSearchResult[]>([]);

    protected readonly faArrowLeft = faArrowLeft;
    protected readonly faFileLines = faFileLines;

    constructor() {
        toObservable(this.searchQuery)
            .pipe(
                filter((query) => query.length > 0),
                debounceTime(300),
                switchMap((query) => this.searchService.search(query)),
                takeUntilDestroyed(),
            )
            .subscribe((results) => this.lectureResults.set(results));
    }
}
