import { ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, effect, forwardRef, inject, input, output, viewChildren } from '@angular/core';
import { SkeletonModule } from 'primeng/skeleton';
import {
    faBook,
    faCalendarCheck,
    faCheckDouble,
    faComment,
    faComments,
    faCube,
    faFileUpload,
    faFont,
    faGraduationCap,
    faHashtag,
    faKeyboard,
    faProjectDiagram,
    faQuestion,
    faQuestionCircle,
} from '@fortawesome/free-solid-svg-icons';
import { MIN_SEARCH_QUERY_LENGTH, SHORT_QUERY_MAX_LENGTH, SearchResultView } from 'app/core/navbar/global-search/components/views/search-result-view.directive';
import { LECTURE_CONTENT_TYPE } from 'app/core/navbar/global-search/models/lecture-content-result.util';
import { IrisSearchAvailabilityService } from 'app/core/navbar/global-search/services/iris-search-availability.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { SearchableEntity } from 'app/core/navbar/global-search/models/searchable-entity.model';
import { SearchableEntityItemComponent } from 'app/core/navbar/global-search/components/modal/searchable-entity-item/searchable-entity-item.component';
import { GlobalSearchResult } from 'app/openapi/model/global-search-result';
import { SearchResultItemComponent } from 'app/core/navbar/global-search/components/modal/search-result-item/search-result-item.component';
import { Router } from '@angular/router';
import { SearchOverlayService } from 'app/core/navbar/global-search/services/search-overlay.service';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { GlobalSearchIrisAnswerComponent } from 'app/core/navbar/global-search/components/views/iris-answer/global-search-iris-answer.component';

@Component({
    selector: 'jhi-global-search-navigation-view',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [GlobalSearchIrisAnswerComponent, SearchableEntityItemComponent, SearchResultItemComponent, SkeletonModule, ArtemisTranslatePipe],
    templateUrl: './global-search-navigation-view.component.html',
    styleUrls: ['./global-search-navigation-view.component.scss'],
    providers: [{ provide: SearchResultView, useExisting: forwardRef(() => GlobalSearchNavigationViewComponent) }],
})
export class GlobalSearchNavigationViewComponent extends SearchResultView {
    private readonly availability = inject(IrisSearchAvailabilityService);

    readonly searchQuery = input.required<string>();
    readonly selectedIndex = input<number>(-1);
    readonly results = input<GlobalSearchResult[]>([]);
    readonly hasSearched = input<boolean>(false);
    readonly showResults = input<boolean>(false);
    readonly isLoading = input<boolean>(false);
    readonly searchError = input<string | undefined>(undefined);
    readonly activeFilters = input<string[]>([]);

    /**
     * True when the query is too short to send to the server (1-2 chars).
     * The template shows a "please enter a longer search term" message.
     */
    protected readonly isTooShortQuery = computed(() => {
        const len = this.searchQuery().trim().length;
        return len > 0 && len < MIN_SEARCH_QUERY_LENGTH;
    });

    /**
     * True when the query meets the minimum length but may still yield poor results
     * due to few trigrams (3-5 chars). The template shows an additional hint.
     */
    protected readonly isShortQuery = computed(() => {
        const len = this.searchQuery().trim().length;
        return len >= MIN_SEARCH_QUERY_LENGTH && len <= SHORT_QUERY_MAX_LENGTH;
    });

    // Skeleton placeholder array for loading animation
    protected readonly skeletonItems = Array(5);

    readonly entityClick = output<SearchableEntity>();

    private readonly router = inject(Router);
    private readonly overlay = inject(SearchOverlayService);

    // Query all selectable items for auto-scroll functionality
    private readonly selectableItems = viewChildren<ElementRef<HTMLElement>>('selectableItem');

    // True only when the Iris module is enabled AND the user has opted into AI usage (LOCAL_AI or CLOUD_AI).
    protected readonly irisEnabled = this.availability.contentSearchAvailable;

    constructor() {
        super();
        effect(() => {
            const idx = this.selectedIndex();
            const items = this.selectableItems();
            if (idx >= 0 && idx < items.length) {
                const element = items[idx]?.nativeElement;
                if (element) {
                    element.scrollIntoView({
                        behavior: 'smooth',
                        block: 'nearest',
                        inline: 'nearest',
                    });
                }
            }
        });
    }

    // Icons
    protected readonly faKeyboard = faKeyboard;
    protected readonly faProjectDiagram = faProjectDiagram;
    protected readonly faFont = faFont;
    protected readonly faFileUpload = faFileUpload;
    protected readonly faCheckDouble = faCheckDouble;
    protected readonly faQuestion = faQuestion;
    protected readonly faCalendarCheck = faCalendarCheck;

    // Searchable entities for initial view
    protected searchableEntities: SearchableEntity[] = [
        {
            id: 'courses',
            title: 'global.search.entities.coursesTitle',
            description: 'global.search.entities.coursesDescription',
            icon: faGraduationCap,
            type: 'filter',
            enabled: true,
            filterTags: ['course'],
        },
        {
            id: 'exercises',
            title: 'global.search.entities.exercisesTitle',
            description: 'global.search.entities.exercisesDescription',
            icon: faCube,
            type: 'filter',
            enabled: true,
            filterTags: ['exercise'],
        },
        {
            id: 'lectures',
            title: 'global.search.entities.lecturesTitle',
            description: 'global.search.entities.lecturesDescription',
            icon: faBook,
            type: 'filter',
            enabled: true,
            filterTags: ['lecture', 'lecture_unit'],
        },
        {
            id: 'communication',
            title: 'global.search.entities.communicationTitle',
            description: 'global.search.entities.communicationDescription',
            icon: faComments,
            type: 'filter',
            enabled: true,
            filterTags: ['channel', 'post', 'answer_post'],
        },
        {
            id: 'faqs',
            title: 'global.search.entities.faqsTitle',
            description: 'global.search.entities.faqsDescription',
            icon: faQuestionCircle,
            type: 'filter',
            enabled: true,
            filterTags: ['faq'],
        },
        {
            id: 'exams',
            title: 'global.search.entities.examsTitle',
            description: 'global.search.entities.examsDescription',
            icon: faCalendarCheck,
            type: 'filter',
            enabled: true,
            filterTags: ['exam'],
        },
    ];

    // Total selectable items reported to the modal to bound ArrowDown/ArrowUp.
    readonly itemCount = computed(() => (this.showResults() ? this.results().length : this.searchableEntities.length));

    protected readonly faHashtag = faHashtag;

    protected onEntityItemClick(entity: SearchableEntity) {
        this.entityClick.emit(entity);
    }

    protected getIconForType(type?: string, badge?: string): IconDefinition {
        if (type === 'exercise') {
            const normalizedBadge = badge?.toLowerCase();
            if (normalizedBadge === 'programming') return this.faKeyboard;
            if (normalizedBadge === 'modeling') return this.faProjectDiagram;
            if (normalizedBadge === 'text') return this.faFont;
            if (normalizedBadge === 'file upload') return this.faFileUpload;
            if (normalizedBadge === 'quiz') return this.faCheckDouble;
            return this.faQuestion;
        }
        if (type === 'lecture' || type === 'lecture_unit' || type === LECTURE_CONTENT_TYPE) {
            return faBook;
        }
        if (type === 'channel') {
            return faHashtag;
        }
        if (type === 'post' || type === 'answer_post') {
            return faComment;
        }
        if (type === 'faq') {
            return faQuestionCircle;
        }
        if (type === 'exam') {
            return this.faCalendarCheck;
        }
        if (type === 'course') {
            return faGraduationCap;
        }
        return this.faQuestion;
    }

    protected navigateToResult(result: GlobalSearchResult) {
        if (result.type === LECTURE_CONTENT_TYPE) {
            const link = result.metadata?.['link'];
            const queryParams = result.metadata?.['queryParams'];
            if (link) {
                void this.router.navigate([link], { queryParams });
            }
            this.overlay.close();
            return;
        }

        const courseId = result.metadata?.['courseId'];
        if (!courseId) {
            this.overlay.close();
            return;
        }

        switch (result.type) {
            case 'course':
                void this.router.navigate(['/courses', courseId]);
                break;
            case 'exercise':
                if (result.id) this.navigateToExercise(result, courseId);
                break;
            case 'lecture':
                if (result.id) this.navigateToLecture(courseId, result.id);
                break;
            case 'lecture_unit':
                if (result.id) this.navigateToLectureUnit(result, courseId);
                break;
            case 'exam':
                if (result.id) this.navigateToExam(result, courseId);
                break;
            case 'faq':
                void this.router.navigate(['/courses', courseId, 'faq']);
                break;
            case 'channel':
                if (result.id) this.navigateToChannel(courseId, result.id);
                break;
            case 'post':
                this.navigateToPost(result, courseId);
                break;
            case 'answer_post':
                this.navigateToAnswerPost(result, courseId);
                break;
        }

        this.overlay.close();
    }

    private navigateToExercise(result: GlobalSearchResult, courseId: string) {
        const examId = result.metadata?.['examId'];
        const exerciseGroupId = result.metadata?.['exerciseGroupId'];
        const isAtLeastEditor = result.metadata?.['isAtLeastEditor'];
        const isAtLeastTutor = result.metadata?.['isAtLeastTutor'];

        if (examId && isAtLeastEditor && exerciseGroupId) {
            // Editors/instructors: exam exercise details page
            this.navigateToExamExerciseDetailsPage(courseId, examId, exerciseGroupId, result);
        } else if (examId && isAtLeastTutor) {
            // Tutors: exam exercise assessment dashboard
            void this.router.navigate(['/course-management', courseId, 'exams', examId, 'assessment-dashboard', result.id]);
        } else if (examId) {
            // Students: student exam view
            void this.router.navigate(['/courses', courseId, 'exams', examId]);
        } else {
            // Students: student exercise view
            void this.router.navigate(['/courses', courseId, 'exercises', result.id]);
        }
    }

    private navigateToExamExerciseDetailsPage(courseId: string, examId: string, exerciseGroupId: string, result: GlobalSearchResult) {
        const typeSegment = (result.badge?.toLowerCase().replace(/ /g, '-') ?? 'text') + '-exercises';
        void this.router.navigate(['/course-management', courseId, 'exams', examId, 'exercise-groups', exerciseGroupId, typeSegment, result.id]);
    }

    private navigateToStudentExamView(courseId: string, examId: string) {
        void this.router.navigate(['/courses', courseId, 'exams', examId]);
    }

    private navigateToLecture(courseId: string, lectureId: string) {
        void this.router.navigate(['/courses', courseId, 'lectures', lectureId]);
    }

    private navigateToLectureUnit(result: GlobalSearchResult, courseId: string) {
        const lectureId = result.metadata?.['lectureId'];
        if (lectureId) {
            this.navigateToLecture(courseId, lectureId);
        }
    }

    private navigateToExam(result: GlobalSearchResult, courseId: string) {
        const isAtLeastEditor = !!result.metadata?.['isAtLeastEditor'];
        const isAtLeastTutor = !!result.metadata?.['isAtLeastTutor'];
        if (isAtLeastEditor) {
            void this.router.navigate(['/course-management', courseId, 'exams', result.id]);
        } else if (isAtLeastTutor) {
            void this.router.navigate(['/course-management', courseId, 'exams', result.id, 'assessment-dashboard']);
        } else {
            this.navigateToStudentExamView(courseId, result.id!);
        }
    }

    private navigateToChannel(courseId: string, channelId: string) {
        void this.router.navigate(['/courses', courseId, 'communication'], { queryParams: { conversationId: channelId } });
    }

    private navigateToPost(result: GlobalSearchResult, courseId: string) {
        const channelId = result.metadata?.['channelId'];
        if (channelId) {
            void this.router.navigate(['/courses', courseId, 'communication'], { queryParams: { conversationId: channelId, focusPostId: result.id } });
        }
    }

    private navigateToAnswerPost(result: GlobalSearchResult, courseId: string) {
        const channelId = result.metadata?.['channelId'];
        const postId = result.metadata?.['postId'];
        if (channelId && postId) {
            void this.router.navigate(['/courses', courseId, 'communication'], { queryParams: { conversationId: channelId, messageId: postId, focusReplyId: result.id } });
        }
    }

    @HostListener('window:keydown', ['$event'])
    handleKeydown(event: KeyboardEvent): void {
        if (event.key !== 'Enter') return;
        const idx = this.selectedIndex();
        if (idx < 0) return;

        if (this.showResults()) {
            event.preventDefault();
            const result = this.results()[idx];
            if (result) {
                this.navigateToResult(result);
            }
        } else {
            event.preventDefault();
            const entity = this.searchableEntities[idx];
            if (entity && entity.enabled) {
                this.entityClick.emit(entity);
            }
        }
    }
}
