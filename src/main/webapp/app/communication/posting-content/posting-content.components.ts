import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, effect, inject, input, output, signal, untracked } from '@angular/core';
import { Params } from '@angular/router';
import { faAngleDown, faAngleUp } from '@fortawesome/free-solid-svg-icons';
import { Post } from 'app/communication/shared/entities/post.model';
import { CommunicationService } from 'app/communication/service/communication.service';
import { Subscription } from 'rxjs';
import { isEqual } from 'lodash-es';
import { PatternMatch, PostingContentPart, ReferenceType } from '../communication.util';
import { User } from 'app/account/user/user.model';
import { Posting } from 'app/communication/shared/entities/posting.model';
import { isCommunicationEnabled } from 'app/course/shared/entities/course.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PostingMarkdownComponent, postingReferencePlaceholder } from 'app/communication/posting-content/posting-markdown/posting-markdown.component';
import { postingReferenceMatches } from './posting-reference.util';
import { LinkPreviewContainerComponent } from 'app/communication/link-preview/components/link-preview-container/link-preview-container.component';

@Component({
    selector: 'jhi-posting-content',
    templateUrl: './posting-content.component.html',
    styleUrls: ['./posting-content.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, FaIconComponent, PostingMarkdownComponent, LinkPreviewContainerComponent],
})
export class PostingContentComponent implements OnInit, OnDestroy {
    private communicationService = inject(CommunicationService);

    content = input<string | undefined>();
    previewMode = input<boolean | undefined>();
    author = input<User | undefined>();
    isEdited = input<boolean>(false);
    posting = input<Posting | undefined>();
    isReply = input<boolean | undefined>();

    userReferenceClicked = output<string>();
    channelReferenceClicked = output<number>();

    isDeleted = input<boolean>(false);
    subscribesToPosts = input<boolean>(true);
    deleteTimerInSeconds = input<number>(0);
    onUndoDeleteEvent = output<void>();

    showContent = false;
    currentlyLoadedPosts: Post[] = [];
    /** The references of the content, in the order of their placeholders in {@link postingMarkdown}. */
    postingContentParts = signal<PostingContentPart[]>([], { equal: isEqual });
    /** The content with a placeholder at the spot of each reference, rendered as one Markdown document. */
    postingMarkdown = signal<string>('');

    private postsSubscription?: Subscription;

    // Directory for attachments. If the endpoint of the file service changes, this needs to be adapted
    private readonly ATTACHMENT_DIR = 'api/core/files/attachments/';
    private readonly SLIDE_DIR = 'api/core/files/slides/';

    // Icons
    faAngleUp = faAngleUp;
    faAngleDown = faAngleDown;

    private initialized = false;

    constructor() {
        effect(() => {
            // Track signal inputs that were monitored in ngOnChanges
            this.content();
            this.posting();
            this.subscribesToPosts();
            this.isEdited();
            this.isDeleted();
            this.deleteTimerInSeconds();
            untracked(() => {
                if (this.initialized) {
                    const patternMatches: PatternMatch[] = this.getPatternMatches();
                    this.computePostingContentParts(patternMatches);
                }
            });
        });
    }

    /**
     * on initialization: calculate posting parts to be displayed
     */
    ngOnInit(): void {
        if (!this.subscribesToPosts()) {
            const patternMatches: PatternMatch[] = this.getPatternMatches();
            this.computePostingContentParts(patternMatches);
        } else {
            this.computeContentPartsOfPosts();
        }
        this.initialized = true;
    }

    /**
     * on initialization & on changes: subscribes to the currently loaded posts in the context, to be available for possible references,
     * computes the PostingContentParts for rendering
     */
    private computeContentPartsOfPosts() {
        this.postsSubscription = this.communicationService.posts.subscribe((posts: Post[]) => {
            this.currentlyLoadedPosts = posts;
            const patternMatches: PatternMatch[] = this.getPatternMatches();
            this.computePostingContentParts(patternMatches);
        });
    }

    /**
     * on leaving the page, the modal should be closed, subscriptions unsubscribed
     */
    ngOnDestroy(): void {
        this.postsSubscription?.unsubscribe();
    }

    /**
     * computes the references of the posting content (denoted by #{PostId}, [user]...[/user] etc.) and the Markdown in which each of them is replaced by a placeholder.
     * The Markdown is rendered as a whole, so that lists, quotes and line breaks around a reference render as they were written.
     */
    computePostingContentParts(patternMatches: PatternMatch[]): void {
        const content = this.content();
        if (content === undefined) {
            this.postingContentParts.set([]);
            this.postingMarkdown.set('');
            return;
        }
        const references: PostingContentPart[] = [];
        let markdown = '';
        let endOfPreviousReference = 0;
        patternMatches.forEach((patternMatch: PatternMatch, index: number) => {
            const referencedId = this.content()!.substring(patternMatch.startIndex + 1, patternMatch.endIndex); // e.g. post id 6
            const referenceType = patternMatch.referenceType;
            let referenceStr; // e.g. '#6', 'Lecture-1.pdf', 'Modeling Exercise'
            let linkToReference;
            let attachmentToReference;
            let slideToReference;
            let queryParams;
            let imageToReference;
            if (ReferenceType.POST === referenceType) {
                // if the referenced Id is within the currently loaded posts, we can create the context-specific link to that post
                // by invoking the respective communication service methods for link and query params and passing the post object;
                // if not, we do not want to fetch the post from the DB and rather always navigate to the course discussion page with the referenceStr as search text
                const referencedPostInLoadedPosts = this.currentlyLoadedPosts.find((post: Post) => post.id! === +referencedId);
                referenceStr = this.content()!.substring(patternMatch.startIndex, patternMatch.endIndex);
                if (isCommunicationEnabled(this.communicationService.getCourse())) {
                    linkToReference = this.communicationService.getLinkForPost();
                    queryParams = referencedPostInLoadedPosts
                        ? this.communicationService.getQueryParamsForPost(referencedPostInLoadedPosts)
                        : ({ searchText: referenceStr } satisfies Params);
                }
            } else if (
                ReferenceType.LECTURE === referenceType ||
                ReferenceType.PROGRAMMING === referenceType ||
                ReferenceType.MODELING === referenceType ||
                ReferenceType.QUIZ === referenceType ||
                ReferenceType.TEXT === referenceType ||
                ReferenceType.FILE_UPLOAD === referenceType
            ) {
                // reference opening tag: [{referenceType}] (wrapped between 2 characters)
                // reference closing tag: [/referenceType] (wrapped between 3 characters)
                // referenceStr: string to be displayed for the reference
                // linkToReference: link to be navigated to on reference click
                referenceStr = this.content()!.substring(this.content()!.indexOf(']', patternMatch.startIndex) + 1, this.content()!.indexOf('(', patternMatch.startIndex));
                linkToReference = [this.content()!.substring(this.content()!.indexOf('(', patternMatch.startIndex) + 1, this.content()!.indexOf(')', patternMatch.startIndex))];
            } else if (ReferenceType.FAQ === referenceType) {
                referenceStr = this.content()!.substring(this.content()!.indexOf(']', patternMatch.startIndex) + 1, this.content()!.indexOf('(/courses', patternMatch.startIndex));
                linkToReference = [
                    this.content()!.substring(this.content()!.indexOf('(/courses', patternMatch.startIndex) + 1, this.content()!.indexOf('?faqId', patternMatch.startIndex)),
                ];
                queryParams = { faqId: this.content()!.substring(this.content()!.indexOf('=') + 1, this.content()!.indexOf(')')) } satisfies Params;
            } else if (ReferenceType.ATTACHMENT === referenceType || ReferenceType.ATTACHMENT_UNITS === referenceType) {
                // referenceStr: string to be displayed for the reference
                // attachmentToReference: location of attachment to be opened on reference click
                // attachmentRefDir: directory of the attachment
                referenceStr = this.content()!.substring(this.content()!.indexOf(']', patternMatch.startIndex) + 1, this.content()!.indexOf('(', patternMatch.startIndex));
                const attachmentRefDir = this.ATTACHMENT_DIR;
                attachmentToReference =
                    attachmentRefDir + this.content()!.substring(this.content()!.indexOf('(', patternMatch.startIndex) + 1, this.content()!.indexOf(')', patternMatch.startIndex));
            } else if (ReferenceType.SLIDE === referenceType) {
                // referenceStr: string to be displayed for the reference
                // slideToReference: location of attachment to be opened on reference click
                referenceStr = this.content()!.substring(this.content()!.indexOf(']', patternMatch.startIndex) + 1, this.content()!.indexOf('(', patternMatch.startIndex));

                // Get the full path from within the parentheses
                const fullPath = this.content()!.substring(this.content()!.indexOf('(', patternMatch.startIndex) + 1, this.content()!.indexOf(')', patternMatch.startIndex));

                // Two cases are created to handle both old and new version of the pattern
                if (fullPath.startsWith('#')) {
                    // This is a slide ID reference
                    const slideId = fullPath.substring(1);
                    const slideRefDir = this.SLIDE_DIR;
                    slideToReference = slideRefDir + slideId;
                } else {
                    const attachmentVideoUnitRefDir = this.ATTACHMENT_DIR;
                    slideToReference = attachmentVideoUnitRefDir + fullPath;
                }
            } else if (ReferenceType.USER === referenceType) {
                // referenceStr: string to be displayed for the reference
                referenceStr = this.content()!.substring(this.content()!.indexOf(']', patternMatch.startIndex) + 1, this.content()!.indexOf('(', patternMatch.startIndex));
                queryParams = {
                    referenceUserLogin: this.content()!.substring(this.content()!.indexOf('(', patternMatch.startIndex) + 1, this.content()!.indexOf(')', patternMatch.startIndex)),
                } satisfies Params;
            } else if (ReferenceType.CHANNEL === referenceType) {
                // referenceStr: string to be displayed for the reference
                referenceStr = this.content()!.substring(this.content()!.indexOf(']', patternMatch.startIndex) + 1, this.content()!.indexOf('(', patternMatch.startIndex));
                const channelId = parseInt(
                    this.content()!.substring(this.content()!.indexOf('(', patternMatch.startIndex) + 1, this.content()!.indexOf(')', patternMatch.startIndex)),
                );
                queryParams = {
                    channelId: isNaN(channelId) ? undefined : channelId,
                } satisfies Params;
            } else if (ReferenceType.IMAGE === referenceType) {
                // get filename of the image
                referenceStr = this.content()!.substring(this.content()!.indexOf('![') + 2, this.content()!.indexOf('](', patternMatch.startIndex));
                imageToReference = this.content()!.substring(this.content()!.indexOf('(', patternMatch.startIndex) + 1, this.content()!.indexOf(')', patternMatch.startIndex));
            }

            references.push({ linkToReference, attachmentToReference, slideToReference, queryParams, referenceStr, referenceType, imageToReference });
            markdown += content.substring(endOfPreviousReference, patternMatch.startIndex) + postingReferencePlaceholder(index);
            endOfPreviousReference = patternMatch.endIndex;
        });
        this.postingContentParts.set(references);
        this.postingMarkdown.set(markdown + content.substring(endOfPreviousReference));
    }

    /** Finds references in their Markdown context while preserving wire-content indices. */
    getPatternMatches(): PatternMatch[] {
        return postingReferenceMatches(this.content() ?? '');
    }
}
