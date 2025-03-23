import { BaseEntity } from 'app/shared/model/base-entity';
import { User } from 'app/core/user/user.model';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';

export enum TutorialGroupRegistrationType {
    SELF_REGISTRATION = 'SELF_REGISTRATION',
    INSTRUCTOR_REGISTRATION = 'INSTRUCTOR_REGISTRATION',
}

export class TutorialGroupRegistration implements BaseEntity {
    public id?: number;
    public student?: User;
    public tutorialGroup?: TutorialGroup;
    public type?: TutorialGroupRegistrationType;
}
