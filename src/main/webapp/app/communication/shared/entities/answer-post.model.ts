import { Post } from 'app/communication/shared/entities/post.model';
import { Posting } from 'app/communication/shared/entities/posting.model';
import { User } from 'app/account/user/user.model';
import dayjs from 'dayjs/esm';

export class AnswerPost extends Posting {
    public resolvesPost?: boolean;
    public post?: Post;
    public forwardedPosts?: (Post | undefined)[] = [];
    public forwardedAnswerPosts?: (AnswerPost | undefined)[] = [];
    public confidenceScore?: number;
    public verified?: boolean;
    public verifiedBy?: User;
    public verifiedAt?: dayjs.Dayjs;

    constructor() {
        super();
        this.resolvesPost = false; // default value
    }

    /**
     * Returns a new AnswerPost holding the same values, with every nested value carried over as it is.
     *
     * This is deliberately not a {@link deepClone}: it exists so a consumer sees a changed reference after the posting
     * was updated in place, or so a derived copy can differ in one flag, while the author, reactions and the parent post stay the
     * same instances. Deep-cloning them would detach the whole graph and re-create every rendered child.
     *
     * Keep this in sync with the fields above and on {@link Posting}.
     */
    static withSameValues(answerPost: AnswerPost): AnswerPost {
        const rebuilt = new AnswerPost();
        rebuilt.id = answerPost.id;
        rebuilt.referencePostId = answerPost.referencePostId;
        rebuilt.author = answerPost.author;
        rebuilt.authorRole = answerPost.authorRole;
        rebuilt.creationDate = answerPost.creationDate;
        rebuilt.updatedDate = answerPost.updatedDate;
        rebuilt.content = answerPost.content;
        rebuilt.isSaved = answerPost.isSaved;
        rebuilt.savedPostStatus = answerPost.savedPostStatus;
        rebuilt.postingType = answerPost.postingType;
        rebuilt.reactions = answerPost.reactions;
        rebuilt.hasForwardedMessages = answerPost.hasForwardedMessages;
        rebuilt.isConsecutive = answerPost.isConsecutive;
        rebuilt.conversation = answerPost.conversation;
        rebuilt.resolvesPost = answerPost.resolvesPost;
        rebuilt.post = answerPost.post;
        rebuilt.forwardedPosts = answerPost.forwardedPosts;
        rebuilt.forwardedAnswerPosts = answerPost.forwardedAnswerPosts;
        rebuilt.confidenceScore = answerPost.confidenceScore;
        rebuilt.verified = answerPost.verified;
        rebuilt.verifiedBy = answerPost.verifiedBy;
        rebuilt.verifiedAt = answerPost.verifiedAt;
        return rebuilt;
    }
}
