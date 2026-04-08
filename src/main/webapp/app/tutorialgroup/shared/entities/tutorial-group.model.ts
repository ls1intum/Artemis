import { BaseEntity } from 'app/shared/model/base-entity';
import { Course } from 'app/core/course/shared/entities/course.model';
import { User } from 'app/core/user/user.model';
import { TutorialGroupSchedule } from 'app/tutorialgroup/shared/entities/tutorial-group-schedule.model';
import { LegacyTutorialGroupSession, TutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { TutorialGroupRegistration } from 'app/tutorialgroup/shared/entities/tutorial-group-registration.model';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { TutorialGroupDetailData as RawTutorialGroupDetailData } from 'app/openapi/model/tutorialGroupDetailData';

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
    public tutorialGroupSessions?: LegacyTutorialGroupSession[];
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
    public nextSession?: LegacyTutorialGroupSession;
    public averageAttendance?: number;
}

export class TutorialGroupDetailData {
    id: number;
    title: string;
    language: string;
    isOnline: boolean;
    sessions: TutorialGroupSession[];
    tutorName: string;
    tutorLogin: string;
    tutorId: number;
    tutorImageUrl?: string;
    capacity?: number;
    campus?: string;
    additionalInformation?: string;
    groupChannelId?: number;
    tutorChatId?: number;

    constructor(rawTutorialGroupDetailData: RawTutorialGroupDetailData) {
        this.id = rawTutorialGroupDetailData.id;
        this.title = rawTutorialGroupDetailData.title;
        this.language = rawTutorialGroupDetailData.language;
        this.isOnline = rawTutorialGroupDetailData.isOnline;
        this.sessions = (rawTutorialGroupDetailData.sessions ?? []).map((rawSessionDto) => new TutorialGroupSession(rawSessionDto));
        this.tutorName = rawTutorialGroupDetailData.tutorName;
        this.tutorLogin = rawTutorialGroupDetailData.tutorLogin;
        this.tutorId = rawTutorialGroupDetailData.tutorId;
        this.tutorImageUrl = rawTutorialGroupDetailData.tutorImageUrl;
        this.capacity = rawTutorialGroupDetailData.capacity;
        this.campus = rawTutorialGroupDetailData.campus;
        this.additionalInformation = rawTutorialGroupDetailData.additionalInformation;
        this.groupChannelId = rawTutorialGroupDetailData.groupChannelId;
        this.tutorChatId = rawTutorialGroupDetailData.tutorChatId;
    }
}

export type TutorialGroupRegisterStudentRequest =
    | { login: undefined; registrationNumber: string }
    | { login: string; registrationNumber: undefined }
    | { login: string; registrationNumber: string };

export interface TutorialGroupTutor {
    id: number;
    nameAndLogin: string;
}
