import { BaseEntity } from 'app/shared/model/base-entity';
import { Course } from 'app/core/course/shared/entities/course.model';
import { User } from 'app/core/user/user.model';
import { TutorialGroupSchedule } from 'app/tutorialgroup/shared/entities/tutorial-group-schedule.model';
import { LegacyTutorialGroupSession, TutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { TutorialGroupRegistration } from 'app/tutorialgroup/shared/entities/tutorial-group-registration.model';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { TutorialGroupDetail } from 'app/openapi/model/tutorialGroupDetail';

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

export class TutorialGroupDetailDTO {
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

    constructor(tutorialGroupDetail: TutorialGroupDetail) {
        this.id = tutorialGroupDetail.id;
        this.title = tutorialGroupDetail.title;
        this.language = tutorialGroupDetail.language;
        this.isOnline = tutorialGroupDetail.isOnline;
        this.sessions = (tutorialGroupDetail.sessions ?? []).map((rawSessionDto) => new TutorialGroupSession(rawSessionDto));
        this.tutorName = tutorialGroupDetail.tutorName;
        this.tutorLogin = tutorialGroupDetail.tutorLogin;
        this.tutorId = tutorialGroupDetail.tutorId;
        this.tutorImageUrl = tutorialGroupDetail.tutorImageUrl;
        this.capacity = tutorialGroupDetail.capacity;
        this.campus = tutorialGroupDetail.campus;
        this.additionalInformation = tutorialGroupDetail.additionalInformation;
        this.groupChannelId = tutorialGroupDetail.groupChannelId;
        this.tutorChatId = tutorialGroupDetail.tutorChatId;
    }
}

export interface TutorialGroupRegisteredStudentDTO {
    id: number;
    name?: string;
    profilePictureUrl?: string;
    login: string;
    email?: string;
    registrationNumber?: string;
}

export type TutorialGroupRegisterStudentDTO =
    | { login: undefined; registrationNumber: string }
    | { login: string; registrationNumber: undefined }
    | { login: string; registrationNumber: string };

export interface TutorialGroupTutorDTO {
    id: number;
    nameAndLogin: string;
}

export interface CreateOrUpdateTutorialGroupDTO {
    title: string;
    tutorId: number;
    language: string;
    isOnline: boolean;
    campus?: string;
    capacity?: number;
    additionalInformation?: string;
    tutorialGroupScheduleDTO?: TutorialGroupScheduleDTO;
}

export interface TutorialGroupScheduleDTO {
    firstSessionStart: string;
    firstSessionEnd: string;
    repetitionFrequency: number;
    tutorialPeriodEnd: string;
    location: string;
}
