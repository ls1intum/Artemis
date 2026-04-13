import {
    ChangeDetectionStrategy,
    Component,
    DestroyRef,
    ElementRef,
    HostListener,
    computed,
    effect,
    forwardRef,
    inject,
    input,
    output,
    signal,
    viewChild,
    viewChildren,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faComments, faTimes } from '@fortawesome/free-solid-svg-icons';
import { Skeleton } from 'primeng/skeleton';
import { Subscription } from 'rxjs';
import { catchError, debounceTime, of, switchMap, tap } from 'rxjs';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { IrisCitationTextComponent } from 'app/iris/overview/citation-text/iris-citation-text.component';
import { IrisCitationMetaDTO } from 'app/iris/shared/entities/iris-citation-meta-dto.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { IrisSearchWebsocketDTO } from 'app/core/navbar/global-search/models/iris-search-result.model';
import { LectureSearchResult } from 'app/core/navbar/global-search/models/lecture-search-result.model';
import { LectureSearchService } from 'app/core/navbar/global-search/services/lecture-search.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { htmlForMarkdown } from 'app/shared/util/markdown.conversion.util';
import { SEARCH_DEBOUNCE_MS, SearchResultView } from 'app/core/navbar/global-search/components/views/search-result-view.directive';

type LoadingState = 'idle' | 'loading' | 'partial' | 'complete';

const CITE_LOADING_REGEX = /\[cite-loading:[^\]]*\]/g;
const CITE_LOADING_SKELETON = '<span class="iris-citation-skeleton"></span>';

@Component({
    selector: 'jhi-global-search-iris-answer',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe, FaIconComponent, Skeleton, RouterLink, IrisLogoComponent, IrisCitationTextComponent],
    providers: [{ provide: SearchResultView, useExisting: forwardRef(() => GlobalSearchIrisAnswerComponent) }],
    templateUrl: './global-search-iris-answer.component.html',
    styleUrls: ['./global-search-iris-answer.component.scss'],
})
export class GlobalSearchIrisAnswerComponent extends SearchResultView {
    readonly query = input.required<string>();
    readonly selectedSourceIndex = input<number>(-1);
    readonly active = input<boolean>(false);
    readonly closeDrawer = output<void>();

    private readonly lectureSearchService = inject(LectureSearchService);
    private readonly websocketService = inject(WebsocketService);
    private readonly router = inject(Router);
    private readonly domSanitizer = inject(DomSanitizer);
    private readonly destroyRef = inject(DestroyRef);

    protected readonly loadingState = signal<LoadingState>('idle');
    protected readonly hasError = signal(false);
    protected readonly sources = signal<LectureSearchResult[]>([]);
    protected readonly partialHtml = signal<SafeHtml | undefined>(undefined);
    protected readonly citedText = signal<string | undefined>(undefined);

    protected readonly citationInfo = computed<IrisCitationMetaDTO[]>(() =>
        this.sources().map((source) => ({
            entityId: source.lectureUnit.id,
            lectureTitle: source.lecture.name,
            lectureUnitTitle: source.lectureUnit.name,
            lectureId: source.lecture.id,
            courseId: source.course.id,
        })),
    );

    readonly itemCount = computed(() => this.sources().length);
    private readonly selectableItems = viewChildren<ElementRef>('selectableItem');
    private readonly contentArea = viewChild<ElementRef<HTMLElement>>('contentArea');

    protected readonly faTimes = faTimes;
    protected readonly faComments = faComments;
    protected readonly IrisLogoSize = IrisLogoSize;

    private wsSubscription?: Subscription;

    constructor() {
        super();
        this.destroyRef.onDestroy(() => this.wsSubscription?.unsubscribe());

        effect(() => {
            const index = this.selectedSourceIndex();
            if (index < 0) {
                if (this.active()) {
                    this.contentArea()?.nativeElement.scrollTo({ top: 0 });
                }
            } else {
                this.selectableItems()[index]?.nativeElement.scrollIntoView({ block: 'nearest' });
            }
        });

        toObservable(this.query)
            .pipe(
                debounceTime(SEARCH_DEBOUNCE_MS),
                tap((query) => {
                    this.wsSubscription?.unsubscribe();
                    this.hasError.set(false);
                    this.sources.set([]);
                    this.partialHtml.set(undefined);
                    this.citedText.set(undefined);
                    this.loadingState.set(query.trim() ? 'loading' : 'idle');
                }),
                switchMap((query) => {
                    if (!query.trim()) {
                        return of(undefined);
                    }
                    return this.lectureSearchService.ask(query).pipe(
                        catchError(() => {
                            this.hasError.set(true);
                            this.loadingState.set('idle');
                            return of(undefined);
                        }),
                    );
                }),
                takeUntilDestroyed(),
            )
            .subscribe((result) => {
                if (!result) return;
                this.subscribeToWebSocket(result.token);
            });
    }

    private subscribeToWebSocket(token: string): void {
        const channel = `/user/topic/iris/search/${token}`;
        this.wsSubscription = this.websocketService.subscribe<IrisSearchWebsocketDTO>(channel).subscribe({
            next: (dto) => {
                if (!dto.cited) {
                    const md = htmlForMarkdown(dto.answer, [], undefined, undefined, true);
                    const withSkeletons = md.replace(CITE_LOADING_REGEX, CITE_LOADING_SKELETON);
                    this.partialHtml.set(this.domSanitizer.bypassSecurityTrustHtml(withSkeletons));
                    this.sources.set(dto.sources ?? []);
                    this.loadingState.set('partial');
                } else {
                    this.citedText.set(dto.answer);
                    this.sources.set(dto.sources ?? []);
                    this.loadingState.set('complete');
                    this.wsSubscription?.unsubscribe();
                }
            },
            error: () => {
                this.hasError.set(true);
                this.loadingState.set('idle');
            },
        });
    }

    @HostListener('window:keydown', ['$event'])
    handleKeydown(event: KeyboardEvent): void {
        if (event.key !== 'Enter') return;
        const index = this.selectedSourceIndex();
        if (index < 0) return;
        const source = this.sources()[index];
        if (source) {
            event.preventDefault();
            this.router.navigate(['/courses', source.course.id, 'lectures', source.lecture.id], {
                queryParams: { unit: source.lectureUnit.id, page: source.lectureUnit.pageNumber, timestamp: source.lectureUnit.startTime },
            });
        }
    }
}
