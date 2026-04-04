import { BaseEntity } from 'app/shared/model/base-entity';
import { Course } from 'app/core/course/shared/entities/course.model';
import { User } from 'app/core/user/user.model';
import { TutorialGroupSchedule } from 'app/tutorialgroup/shared/entities/tutorial-group-schedule.model';
import { RawTutorialGroupDetailSessionDTO, TutorialGroupDetailSessionDTO, TutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { TutorialGroupRegistration } from 'app/tutorialgroup/shared/entities/tutorial-group-registration.model';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';

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

export class TutorialGroupDetailDTO {
    id: number;
    title: string;
    language: string;
    isOnline: boolean;
    sessions: TutorialGroupDetailSessionDTO[];
    tutorName: string;
    tutorLogin: string;
    tutorId: number;
    tutorImageUrl?: string;
    capacity?: number;
    campus?: string;
    additionalInformation?: string;
    groupChannelId?: number;
    tutorChatId?: number;

    constructor(rawDto: RawTutorialGroupDetailGroupDTO) {
        this.id = rawDto.id;
        this.title = rawDto.title;
        this.language = rawDto.language;
        this.isOnline = rawDto.isOnline;
        this.sessions = (rawDto.sessions ?? []).map((rawSessionDto) => new TutorialGroupDetailSessionDTO(rawSessionDto));
        this.tutorName = rawDto.tutorName;
        this.tutorLogin = rawDto.tutorLogin;
        this.tutorId = 1; // temporary compatibility solution -> does not break anything, will fix on main PR
        this.tutorImageUrl = rawDto.tutorImageUrl;
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
    tutorName: string;
    tutorLogin: string;
    sessions: RawTutorialGroupDetailSessionDTO[] | undefined;
    tutorImageUrl?: string;
    capacity?: number;
    campus?: string;
    groupChannelId?: number;
    tutorChatId?: number;
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
