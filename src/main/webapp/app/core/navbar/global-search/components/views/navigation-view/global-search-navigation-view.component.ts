import { ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, effect, forwardRef, inject, input, viewChildren } from '@angular/core';
import { SkeletonModule } from 'primeng/skeleton';
import {
    faBook,
    faCalendarCheck,
    faCheckDouble,
    faComment,
    faFileUpload,
    faFont,
    faGraduationCap,
    faHashtag,
    faKeyboard,
    faPhotoFilm,
    faProjectDiagram,
    faQuestion,
    faQuestionCircle,
} from '@fortawesome/free-solid-svg-icons';
import { MIN_SEARCH_QUERY_LENGTH, SHORT_QUERY_MAX_LENGTH, SearchResultView } from 'app/core/navbar/global-search/components/views/search-result-view.directive';
import { LECTURE_CONTENT_TYPE } from 'app/core/navbar/global-search/models/lecture-content-result.util';
import { MODULE_FEATURE_IRIS } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { AccountService } from 'app/core/auth/account.service';
import { LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
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
    imports: [GlobalSearchIrisAnswerComponent, SearchResultItemComponent, SkeletonModule, ArtemisTranslatePipe],
    templateUrl: './global-search-navigation-view.component.html',
    styleUrls: ['./global-search-navigation-view.component.scss'],
    providers: [{ provide: SearchResultView, useExisting: forwardRef(() => GlobalSearchNavigationViewComponent) }],
})
export class GlobalSearchNavigationViewComponent extends SearchResultView {
    private readonly profileService = inject(ProfileService);
    private readonly accountService = inject(AccountService);

    readonly searchQuery = input.required<string>();
    readonly selectedIndex = input<number>(-1);
    readonly results = input<GlobalSearchResult[]>([]);
    readonly hasSearched = input<boolean>(false);
    readonly showResults = input<boolean>(false);
    readonly isLoading = input<boolean>(false);
    readonly searchError = input<string | undefined>(undefined);
    /** True while the slides and videos filter is the active one, which searches content instead of metadata. */
    readonly contentSearchActive = input<boolean>(false);

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

    private readonly router = inject(Router);
    private readonly overlay = inject(SearchOverlayService);

    // Query all selectable items for auto-scroll functionality
    private readonly selectableItems = viewChildren<ElementRef<HTMLElement>>('selectableItem');

    // False when artemis.iris.enabled = false in the server config.
    private readonly irisModuleEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_IRIS);
    // True only when the module is enabled AND the user has opted into AI usage (LOCAL_AI or CLOUD_AI).
    protected readonly irisEnabled = computed(() => {
        if (!this.irisModuleEnabled) return false;
        const usage = this.accountService.userIdentity()?.selectedLLMUsage;
        return usage === LLMSelectionDecision.LOCAL_AI || usage === LLMSelectionDecision.CLOUD_AI;
    });
    /** True when the slides and videos filter is active without a search term, which content search cannot run without. */
    protected readonly isContentSearchPrompt = computed(() => this.contentSearchActive() && this.searchQuery().trim().length === 0);

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

    // Total selectable items reported to the modal to bound ArrowDown/ArrowUp.
    readonly itemCount = computed(() => this.results().length);

    protected readonly faHashtag = faHashtag;

    protected getIconForType(type?: string, badge?: string): IconDefinition {
        if (type === LECTURE_CONTENT_TYPE) {
            return faPhotoFilm;
        }
        if (type === 'exercise') {
            const normalizedBadge = badge?.toLowerCase();
            if (normalizedBadge === 'programming') return this.faKeyboard;
            if (normalizedBadge === 'modeling') return this.faProjectDiagram;
            if (normalizedBadge === 'text') return this.faFont;
            if (normalizedBadge === 'file-upload') return this.faFileUpload;
            if (normalizedBadge === 'quiz') return this.faCheckDouble;
            return this.faQuestion;
        }
        if (type === 'lecture' || type === 'lecture_unit') {
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
        // A content hit points at a slide or a video timestamp, so it carries its own deep link instead of an entity id.
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
        // The badge key is the canonical exercise-type key (e.g. "programming", "file-upload"), which is exactly the
        // exam exercise-group route segment prefix. A row without a recognisable type (the generic "exercise" fallback)
        // carries no segment to build, and guessing one would open another type's detail page for this exercise id.
        // The exam's exercise-group list is the closest page that is always right, and the exercise is one click away.
        const validExerciseSegments = new Set(['programming', 'modeling', 'text', 'file-upload', 'quiz']);
        if (!result.badgeKey || !validExerciseSegments.has(result.badgeKey)) {
            void this.router.navigate(['/course-management', courseId, 'exams', examId, 'exercise-groups']);
            return;
        }
        void this.router.navigate(['/course-management', courseId, 'exams', examId, 'exercise-groups', exerciseGroupId, result.badgeKey + '-exercises', result.id]);
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

        event.preventDefault();
        const result = this.results()[idx];
        if (result) {
            this.navigateToResult(result);
        }
    }
}
