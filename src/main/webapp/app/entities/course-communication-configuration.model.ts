import { BaseEntity } from 'app/shared/model/base-entity';

export class CourseCommunicationConfiguration implements BaseEntity {
    public id?: number;
    public questionsAndAnswersEnabled?: boolean;
    public channelMessagingEnabled?: boolean;
    public groupMessagingEnabled?: boolean;
    public oneToOneMessagingEnabled?: boolean;
}
