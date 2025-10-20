import { BaseEntity } from 'app/shared/model/base-entity';
import { Course } from 'app/core/course/shared/entities/course.model';
import { User } from 'app/core/user/user.model';
import { TutorialGroupSchedule } from 'app/tutorialgroup/shared/entities/tutorial-group-schedule.model';
import { RawTutorialGroupDetailSessionDTO, TutorialGroupDetailSessionDTO, TutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { TutorialGroupRegistration } from 'app/tutorialgroup/shared/entities/tutorial-group-registration.model';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import dayjs from 'dayjs/esm';

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

export class TutorialGroupDetailGroupDTO {
    id: number;
    title: string;
    language: string;
    isOnline: boolean;
    sessions: TutorialGroupDetailSessionDTO[];
    teachingAssistantName: string;
    teachingAssistantLogin: string;
    teachingAssistantImageUrl?: string;
    capacity?: number;
    campus?: string;
    groupChannelId?: number;
    tutorChatId?: number;

    constructor(rawDto: RawTutorialGroupDetailGroupDTO) {
        this.id = rawDto.id;
        this.title = rawDto.title;
        this.language = rawDto.language;
        this.isOnline = rawDto.isOnline;
        this.sessions = (rawDto.sessions ?? []).map(
            (session: RawTutorialGroupDetailSessionDTO) =>
                new TutorialGroupDetailSessionDTO(
                    dayjs(session.start),
                    dayjs(session.end),
                    session.location,
                    session.isCancelled,
                    session.locationChanged,
                    session.timeChanged,
                    session.dateChanged,
                    session.attendanceCount,
                ),
        );
        this.teachingAssistantName = rawDto.teachingAssistantName;
        this.teachingAssistantLogin = rawDto.teachingAssistantLogin;
        this.teachingAssistantImageUrl = rawDto.teachingAssistantImageUrl;
        this.capacity = rawDto.capacity;
        this.campus = rawDto.campus;
        this.groupChannelId = rawDto.groupChannelId;
        this.tutorChatId = rawDto.tutorChatId;
    }
}

export class RawTutorialGroupDetailGroupDTO {
    id: number;
    title: string;
    language: string;
    isOnline: boolean;
    teachingAssistantName: string;
    teachingAssistantLogin: string;
    sessions: RawTutorialGroupDetailSessionDTO[] | undefined;
    teachingAssistantImageUrl?: string;
    capacity?: number;
    campus?: string;
    groupChannelId?: number;
    tutorChatId?: number;
}
