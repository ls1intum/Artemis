import { Moment } from 'moment';
import { BaseEntity } from 'app/shared/model/base-entity';
import { User } from 'app/core/user/user.model';
import { Post } from 'app/entities/metis/post.model';

export class AnswerPost implements BaseEntity {
    public id?: number;
    public author?: User;
    public creationDate?: Moment;
    public content?: string;
    public tokenizedContent?: string;
    public tutorApproved?: boolean;
    public post?: Post;

    constructor() {
        this.tutorApproved = false; // default value
    }
}
