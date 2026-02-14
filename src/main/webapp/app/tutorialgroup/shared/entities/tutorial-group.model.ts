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

export class TutorialGroupDTO {
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

    constructor(rawDto: RawTutorialGroupDTO) {
        this.id = rawDto.id;
        this.title = rawDto.title;
        this.language = rawDto.language;
        this.isOnline = rawDto.isOnline;
        this.sessions = (rawDto.sessions ?? []).map(
            (rawSessionDto: RawTutorialGroupDetailSessionDTO) =>
                new TutorialGroupDetailSessionDTO(
                    rawSessionDto.id,
                    dayjs(rawSessionDto.start),
                    dayjs(rawSessionDto.end),
                    rawSessionDto.location,
                    rawSessionDto.isCancelled,
                    rawSessionDto.locationChanged,
                    rawSessionDto.timeChanged,
                    rawSessionDto.dateChanged,
                    rawSessionDto.attendanceCount,
                ),
        );
        this.tutorName = rawDto.tutorName;
        this.tutorLogin = rawDto.tutorLogin;
        this.tutorId = rawDto.tutorId;
        this.tutorImageUrl = rawDto.tutorImageUrl;
        this.capacity = rawDto.capacity;
        this.campus = rawDto.campus;
        this.additionalInformation = rawDto.additionalInformation;
        this.groupChannelId = rawDto.groupChannelId;
        this.tutorChatId = rawDto.tutorChatId;
    }
}

export class RawTutorialGroupDTO {
    id: number;
    title: string;
    language: string;
    isOnline: boolean;
    tutorName: string;
    tutorLogin: string;
    tutorId: number;
    sessions: RawTutorialGroupDetailSessionDTO[] | undefined;
    tutorImageUrl?: string;
    capacity?: number;
    campus?: string;
    additionalInformation?: string;
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

export interface TutorialGroupRegisterStudentDTO {
    login?: string;
    registrationNumber?: string;
}

export interface TutorialGroupTutorDTO {
    id: number;
    nameAndLogin: string;
}

export interface UpdateTutorialGroupDTO {
    title: string;
    updateChannelName: boolean;
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
