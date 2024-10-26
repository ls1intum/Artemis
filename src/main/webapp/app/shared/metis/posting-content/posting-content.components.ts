import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, signal } from '@angular/core';
import { Params } from '@angular/router';
import { faAngleDown, faAngleUp } from '@fortawesome/free-solid-svg-icons';
import { Post } from 'app/entities/metis/post.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { Subscription } from 'rxjs';
import { PatternMatch, PostingContentPart, ReferenceType } from '../metis.util';
import { User } from 'app/core/user/user.model';
import { Posting } from 'app/entities/metis/posting.model';
import { isCommunicationEnabled } from 'app/entities/course.model';

@Component({
    selector: 'jhi-posting-content',
    templateUrl: './posting-content.component.html',
    styleUrls: ['./posting-content.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PostingContentComponent implements OnInit, OnChanges, OnDestroy {
    @Input() content?: string;
    @Input() previewMode?: boolean;
    @Input() author?: User;
    @Input() isEdited = false;
    @Input() posting?: Posting;
    @Input() isReply?: boolean;
    @Output() userReferenceClicked = new EventEmitter<string>();
    @Output() channelReferenceClicked = new EventEmitter<number>();

    showContent = false;
    currentlyLoadedPosts: Post[];
    postingContentParts = signal<PostingContentPart[]>([]);

    private postsSubscription: Subscription;

    // Directory for attachments. If the endpoint of the file service changes, this needs to be adapted
    private readonly ATTACHMENT_DIR = 'api/files/attachments/';

    // Icons
    faAngleUp = faAngleUp;
    faAngleDown = faAngleDown;

    constructor(private metisService: MetisService) {}

    /**
     * on initialization: calculate posting parts to be displayed
     */
    ngOnInit(): void {
        this.computeContentPartsOfPosts();
    }

    /**
     * on changes: update posting parts to be displayed
     */
    ngOnChanges(): void {
        this.computeContentPartsOfPosts();
    }

    /**
     * on initialization & on changes: subscribes to the currently loaded posts in the context, to be available for possible references,
     * computes the PostingContentParts for rendering
     */
    private computeContentPartsOfPosts() {
        this.postsSubscription = this.metisService.posts.subscribe((posts: Post[]) => {
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
     * computes an array of PostingContentPart objects by splitting up the posting content by post references (denoted by #{PostId}).
     */
    computePostingContentParts(patternMatches: PatternMatch[]): void {
        this.postingContentParts.set([]);
        // if there are references found in the posting content, we need to create a PostingContentPart per reference match
        if (patternMatches && patternMatches.length > 0) {
            patternMatches.forEach((patternMatch: PatternMatch, index: number) => {
                if (this.content === undefined) {
                    return;
                }

                const referencedId = this.content.substring(patternMatch.startIndex + 1, patternMatch.endIndex); // e.g. post id 6
                const referenceType = patternMatch.referenceType;
                let referenceStr; // e.g. '#6', 'Lecture-1.pdf', 'Modeling Exercise'
                let linkToReference;
                let attachmentToReference;
                let slideToReference;
                let queryParams;
                let imageToReference;
                if (ReferenceType.POST === referenceType) {
                    // if the referenced Id is within the currently loaded posts, we can create the context-specific link to that post
                    // by invoking the respective metis service methods for link and query params and passing the post object;
                    // if not, we do not want to fetch the post from the DB and rather always navigate to the course discussion page with the referenceStr as search text
                    const referencedPostInLoadedPosts = this.currentlyLoadedPosts.find((post: Post) => post.id! === +referencedId);
                    referenceStr = this.content.substring(patternMatch.startIndex, patternMatch.endIndex);
                    if (isCommunicationEnabled(this.metisService.getCourse())) {
                        linkToReference = this.metisService.getLinkForPost();
                        queryParams = referencedPostInLoadedPosts ? this.metisService.getQueryParamsForPost(referencedPostInLoadedPosts) : ({ searchText: referenceStr } as Params);
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
                    referenceStr = this.content.substring(this.content.indexOf(']', patternMatch.startIndex)! + 1, this.content.indexOf('(', patternMatch.startIndex)!);
                    linkToReference = [this.content.substring(this.content.indexOf('(', patternMatch.startIndex)! + 1, this.content.indexOf(')', patternMatch.startIndex))];
                } else if (ReferenceType.FAQ === referenceType) {
                    referenceStr = this.content.substring(this.content.indexOf(']', patternMatch.startIndex)! + 1, this.content.indexOf('(', patternMatch.startIndex)!);
                    linkToReference = [
                        this.content.substring(this.content.indexOf('(/courses', patternMatch.startIndex)! + 1, this.content.indexOf('?faqId', patternMatch.startIndex)),
                    ];
                    queryParams = { faqId: this.content.substring(this.content.indexOf('=') + 1, this.content.indexOf(')')) } as Params;
                } else if (ReferenceType.ATTACHMENT === referenceType || ReferenceType.ATTACHMENT_UNITS === referenceType) {
                    // referenceStr: string to be displayed for the reference
                    // attachmentToReference: location of attachment to be opened on reference click
                    // attachmentRefDir: directory of the attachment
                    referenceStr = this.content.substring(this.content.indexOf(']', patternMatch.startIndex)! + 1, this.content.indexOf('(', patternMatch.startIndex)!);
                    const attachmentRefDir = this.ATTACHMENT_DIR;
                    attachmentToReference =
                        attachmentRefDir + this.content.substring(this.content.indexOf('(', patternMatch.startIndex)! + 1, this.content.indexOf(')', patternMatch.startIndex));
                } else if (ReferenceType.SLIDE === referenceType) {
                    // referenceStr: string to be displayed for the reference
                    // slideToReference: location of attachment to be opened on reference click
                    referenceStr = this.content.substring(this.content.indexOf(']', patternMatch.startIndex)! + 1, this.content.indexOf('(', patternMatch.startIndex)!);
                    const attachmentUnitRefDir = this.ATTACHMENT_DIR;
                    slideToReference =
                        attachmentUnitRefDir + this.content.substring(this.content.indexOf('(', patternMatch.startIndex)! + 1, this.content.indexOf(')', patternMatch.startIndex));
                } else if (ReferenceType.USER === referenceType) {
                    // referenceStr: string to be displayed for the reference
                    referenceStr = this.content.substring(this.content.indexOf(']', patternMatch.startIndex)! + 1, this.content.indexOf('(', patternMatch.startIndex)!);
                    queryParams = {
                        referenceUserLogin: this.content.substring(this.content.indexOf('(', patternMatch.startIndex)! + 1, this.content.indexOf(')', patternMatch.startIndex)),
                    } as Params;
                } else if (ReferenceType.CHANNEL === referenceType) {
                    // referenceStr: string to be displayed for the reference
                    referenceStr = this.content.substring(this.content.indexOf(']', patternMatch.startIndex)! + 1, this.content.indexOf('(', patternMatch.startIndex)!);
                    const channelId = parseInt(this.content.substring(this.content.indexOf('(', patternMatch.startIndex)! + 1, this.content.indexOf(')', patternMatch.startIndex)));
                    queryParams = {
                        channelId: isNaN(channelId) ? undefined : channelId,
                    } as Params;
                } else if (ReferenceType.IMAGE === referenceType) {
                    // get filename of the image
                    referenceStr = this.content.substring(this.content.indexOf('![') + 2, this.content.indexOf('](', patternMatch.startIndex));
                    imageToReference = this.content.substring(this.content.indexOf('(', patternMatch.startIndex)! + 1, this.content.indexOf(')', patternMatch.startIndex));
                }

                // determining the endIndex of the content after the reference
                let endIndexOfContentAfterReference;
                // if current match is not the last match in the array, i.e. there is another match
                if (index < patternMatches.length - 1) {
                    // endIndex of the content after the reference equals the startIndex of the subsequent match
                    endIndexOfContentAfterReference = patternMatches[index + 1].startIndex;
                    // if current match is the only or last one in patternMatches
                } else {
                    // endIndex of the content after the reference equals the end of the post content
                    endIndexOfContentAfterReference = this.content.length;
                }

                // building the PostingContentPart object
                const contentPart: PostingContentPart = {
                    contentBeforeReference: index === 0 ? this.content.substring(0, patternMatch.startIndex) : undefined, // only defined for the first match
                    linkToReference,
                    attachmentToReference,
                    slideToReference,
                    queryParams,
                    referenceStr,
                    referenceType,
                    imageToReference,
                    contentAfterReference: this.content.substring(patternMatch.endIndex, endIndexOfContentAfterReference),
                };
                this.postingContentParts.set([...this.postingContentParts(), contentPart]);
            });
            // if there are no post references in the content, the whole content is represented by a single PostingContentPart,
            // with contentBeforeReferenced represents the post content
        } else {
            const contentLink: PostingContentPart = {
                contentBeforeReference: this.content,
                linkToReference: undefined,
                queryParams: undefined,
                referenceStr: undefined,
                referenceType: undefined,
                contentAfterReference: undefined,
            };
            this.postingContentParts.set([...this.postingContentParts(), contentLink]);
        }
    }

    /**
     * searches a regex pattern within a string and returns an array containing a PatternMatch Object per match
     */
    getPatternMatches(): PatternMatch[] {
        // Group 1: reference pattern for Posts: #{PostId} Ex: (#45)
        // Group 2: reference pattern for Programming Exercises
        // Group 3: reference pattern for Modeling Exercises
        // Group 4: reference pattern for Text Exercises
        // Group 5: reference pattern for File Upload Exercises
        // Group 6: reference pattern for Lectures
        // Group 7: reference pattern for Lecture Attachments
        // Group 8: reference pattern for Lecture Units
        // Group 9: reference pattern for Users
        // Group 10: reference pattern for FAQ
        // globally searched for, i.e. no return after first match
        const pattern =
            /(?<POST>#\d+)|(?<PROGRAMMING>\[programming].*?\[\/programming])|(?<MODELING>\[modeling].*?\[\/modeling])|(?<QUIZ>\[quiz].*?\[\/quiz])|(?<TEXT>\[text].*?\[\/text])|(?<FILE_UPLOAD>\[file-upload].*?\[\/file-upload])|(?<LECTURE>\[lecture].*?\[\/lecture])|(?<ATTACHMENT>\[attachment].*?\[\/attachment])|(?<ATTACHMENT_UNITS>\[lecture-unit].*?\[\/lecture-unit])|(?<SLIDE>\[slide].*?\[\/slide])|(?<USER>\[user].*?\[\/user])|(?<CHANNEL>\[channel].*?\[\/channel])|(?<FAQ>\[faq].*?\[\/faq])/g;

        // Group 10: pattern for embedded images
        // globally searched for, i.e. no return after first match
        const pattern =
            /(?<POST>#\d+)|(?<PROGRAMMING>\[programming].*?\[\/programming])|(?<MODELING>\[modeling].*?\[\/modeling])|(?<QUIZ>\[quiz].*?\[\/quiz])|(?<TEXT>\[text].*?\[\/text])|(?<FILE_UPLOAD>\[file-upload].*?\[\/file-upload])|(?<LECTURE>\[lecture].*?\[\/lecture])|(?<ATTACHMENT>\[attachment].*?\[\/attachment])|(?<ATTACHMENT_UNITS>\[lecture-unit].*?\[\/lecture-unit])|(?<SLIDE>\[slide].*?\[\/slide])|(?<USER>\[user].*?\[\/user])|(?<CHANNEL>\[channel].*?\[\/channel])|(?<IMAGE>!\[.*?]\(.*?\))|(?<FAQ>\[faq].*?\[\/faq])/g;


        // array with PatternMatch objects per reference found in the posting content
        const patternMatches: PatternMatch[] = [];

        // find start and end index of referenced posts in content, for each reference save [startIndexOfReference, endIndexOfReference] in the referenceIndicesArray
        let match = pattern.exec(this.content!);
        while (match) {
            let group: ReferenceType | undefined = undefined;

            for (const groupsKey in match.groups) {
                if (match.groups[groupsKey]) {
                    group = ReferenceType[groupsKey as keyof typeof ReferenceType];
                }
            }
            if (group) {
                patternMatches.push({
                    startIndex: match.index,
                    endIndex: pattern.lastIndex,
                    referenceType: group!,
                } as PatternMatch);
            }

            match = pattern.exec(this.content!);
        }
        return patternMatches;
    }
}
