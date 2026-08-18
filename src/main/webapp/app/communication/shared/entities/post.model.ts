import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { Posting } from 'app/communication/shared/entities/posting.model';
import { DisplayPriority } from 'app/communication/metis.util';
import { PlagiarismCase } from 'app/plagiarism/shared/entities/PlagiarismCase';

export class Post extends Posting {
    public title?: string;
    public answers?: AnswerPost[];
    public plagiarismCase?: PlagiarismCase;
    public displayPriority?: DisplayPriority;
    public resolved?: boolean;
    public forwardedPosts?: (Post | undefined)[] = [];
    public forwardedAnswerPosts?: (AnswerPost | undefined)[] = [];
    public visibleForStudents?: boolean;

    constructor() {
        super();
        // set default values
        this.displayPriority = DisplayPriority.NONE;
    }

    /**
     * Returns a new Post holding the same values, with every nested value carried over as it is.
     *
     * This is deliberately not a {@link deepClone}: it exists so a consumer sees a changed reference after the posting
     * was updated in place, or so a derived copy can differ in one flag, while the author, reactions and the answers stay the
     * same instances. Deep-cloning them would detach the whole graph and re-create every rendered child.
     *
     * Keep this in sync with the fields above and on {@link Posting}.
     */
    static withSameValues(post: Post): Post {
        const rebuilt = new Post();
        rebuilt.id = post.id;
        rebuilt.referencePostId = post.referencePostId;
        rebuilt.author = post.author;
        rebuilt.authorRole = post.authorRole;
        rebuilt.creationDate = post.creationDate;
        rebuilt.updatedDate = post.updatedDate;
        rebuilt.content = post.content;
        rebuilt.isSaved = post.isSaved;
        rebuilt.savedPostStatus = post.savedPostStatus;
        rebuilt.postingType = post.postingType;
        rebuilt.reactions = post.reactions;
        rebuilt.hasForwardedMessages = post.hasForwardedMessages;
        rebuilt.isConsecutive = post.isConsecutive;
        rebuilt.conversation = post.conversation;
        rebuilt.title = post.title;
        rebuilt.answers = post.answers;
        rebuilt.plagiarismCase = post.plagiarismCase;
        rebuilt.displayPriority = post.displayPriority;
        rebuilt.resolved = post.resolved;
        rebuilt.forwardedPosts = post.forwardedPosts;
        rebuilt.forwardedAnswerPosts = post.forwardedAnswerPosts;
        rebuilt.visibleForStudents = post.visibleForStudents;
        return rebuilt;
    }
}
