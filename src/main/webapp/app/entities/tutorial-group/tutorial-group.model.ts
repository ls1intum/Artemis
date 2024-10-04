import { BaseEntity } from 'app/shared/model/base-entity';
import { Course } from 'app/entities/course.model';
import { User } from 'app/core/user/user.model';
import { TutorialGroupSchedule } from 'app/entities/tutorial-group/tutorial-group-schedule.model';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { TutorialGroupRegistration } from 'app/entities/tutorial-group/tutorial-group-registration.model';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';

export class TutorialGroup implements BaseEntity {
    public id?: number;
    public title?: string;
    public course?: Course;
    public capacity?: number;
    public campus?: string;
    public language?: string;
    public additionalInformation?: string;
    public isOnline?: boolean;
    public teachingAssistant?: User;
    public tutorialGroupSchedule?: TutorialGroupSchedule;
    public tutorialGroupSessions?: TutorialGroupSession[];
    public registrations?: TutorialGroupRegistration[];
    public channel?: ChannelDTO;

    // transientFields
    public isUserRegistered?: boolean;
    public isUserTutor?: boolean;
    public numberOfRegisteredUsers?: number;
    public teachingAssistantName?: string;
    public teachingAssistantId?: number;
    public teachingAssistantImageUrl?: string;
    public courseTitle?: string;
    public nextSession?: TutorialGroupSession;
    public averageAttendance?: number;
}
