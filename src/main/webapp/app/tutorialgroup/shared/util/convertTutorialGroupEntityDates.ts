import { convertDateFromServer, convertDateStringFromServer } from 'app/foundation/util/date.utils';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { LegacyTutorialGroupSession, TutorialGroupSessionStatus } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { TutorialGroupSchedule } from 'app/tutorialgroup/shared/entities/tutorial-group-schedule.model';
import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';
import { TutorialGroupSummary } from 'app/openapi/model/tutorial-group-summary';
import { Channel as TutorialGroupSummaryChannel } from 'app/openapi/model/channel';
import { TutorialGroupSummarySchedule } from 'app/openapi/model/tutorial-group-summary-schedule';
import { TutorialGroupSummarySession } from 'app/openapi/model/tutorial-group-summary-session';
import { TutorialGroupFreePeriod as TutorialGroupSummaryFreePeriod } from 'app/openapi/model/tutorial-group-free-period';
import { ChannelDTO, ChannelSubType } from 'app/communication/shared/entities/conversation/channel.model';
import { ConversationUserDTO } from 'app/communication/shared/entities/conversation/conversation-user-dto.model';
import { deepClone } from 'app/foundation/util/deep-clone.util';

export function convertTutorialGroupFreePeriodDatesFromServer(tutorialGroupFreePeriod: TutorialGroupFreePeriod): TutorialGroupFreePeriod {
    tutorialGroupFreePeriod.start = convertDateFromServer(tutorialGroupFreePeriod.start);
    tutorialGroupFreePeriod.end = convertDateFromServer(tutorialGroupFreePeriod.end);
    return tutorialGroupFreePeriod;
}

export function convertTutorialGroupSessionDatesFromServer(tutorialGroupSession: LegacyTutorialGroupSession): LegacyTutorialGroupSession {
    tutorialGroupSession.start = convertDateFromServer(tutorialGroupSession.start);
    tutorialGroupSession.end = convertDateFromServer(tutorialGroupSession.end);
    if (tutorialGroupSession.tutorialGroupFreePeriod) {
        tutorialGroupSession.tutorialGroupFreePeriod = convertTutorialGroupFreePeriodDatesFromServer(tutorialGroupSession.tutorialGroupFreePeriod);
    }
    return tutorialGroupSession;
}

export function convertTutorialGroupsConfigurationDatesFromServer(tutorialGroupsConfiguration: TutorialGroupsConfiguration): TutorialGroupsConfiguration {
    tutorialGroupsConfiguration.tutorialPeriodStartInclusive = convertDateFromServer(tutorialGroupsConfiguration.tutorialPeriodStartInclusive);
    tutorialGroupsConfiguration.tutorialPeriodEndInclusive = convertDateFromServer(tutorialGroupsConfiguration.tutorialPeriodEndInclusive);
    if (tutorialGroupsConfiguration.tutorialGroupFreePeriods) {
        tutorialGroupsConfiguration.tutorialGroupFreePeriods.forEach((tutorialGroupFreePeriod) => {
            tutorialGroupFreePeriod.start = convertDateFromServer(tutorialGroupFreePeriod.start);
            tutorialGroupFreePeriod.end = convertDateFromServer(tutorialGroupFreePeriod.end);
        });
    }
    return tutorialGroupsConfiguration;
}

export function convertTutorialGroupDatesFromServer(tutorialGroup: TutorialGroup): TutorialGroup {
    if (tutorialGroup.tutorialGroupSchedule) {
        tutorialGroup.tutorialGroupSchedule.validFromInclusive = convertDateFromServer(tutorialGroup.tutorialGroupSchedule.validFromInclusive);
        tutorialGroup.tutorialGroupSchedule.validToInclusive = convertDateFromServer(tutorialGroup.tutorialGroupSchedule.validToInclusive);
    }
    if (tutorialGroup.tutorialGroupSessions) {
        tutorialGroup.tutorialGroupSessions.map((tutorialGroupSession: LegacyTutorialGroupSession) => convertTutorialGroupSessionDatesFromServer(tutorialGroupSession));
    }
    if (tutorialGroup.nextSession) {
        tutorialGroup.nextSession = convertTutorialGroupSessionDatesFromServer(tutorialGroup.nextSession);
    }
    if (tutorialGroup.course?.tutorialGroupsConfiguration) {
        tutorialGroup.course.tutorialGroupsConfiguration = convertTutorialGroupsConfigurationDatesFromServer(tutorialGroup.course?.tutorialGroupsConfiguration);
    }
    return tutorialGroup;
}

export function convertTutorialGroupArrayDatesFromServer(tutorialGroups: TutorialGroup[]): TutorialGroup[] {
    if (tutorialGroups) {
        tutorialGroups.forEach((tutorialGroup: TutorialGroup) => {
            convertTutorialGroupDatesFromServer(tutorialGroup);
        });
    }
    return tutorialGroups;
}

function convertTutorialGroupSummaryFreePeriod(freePeriodSummary: TutorialGroupSummaryFreePeriod): TutorialGroupFreePeriod {
    const freePeriod = new TutorialGroupFreePeriod();
    freePeriod.id = freePeriodSummary.id;
    freePeriod.start = convertDateStringFromServer(freePeriodSummary.start);
    freePeriod.end = convertDateStringFromServer(freePeriodSummary.end);
    freePeriod.reason = freePeriodSummary.reason;
    return freePeriod;
}

function convertTutorialGroupSummarySession(sessionSummary: TutorialGroupSummarySession): LegacyTutorialGroupSession {
    const session = new LegacyTutorialGroupSession();
    session.id = sessionSummary.id;
    session.start = convertDateStringFromServer(sessionSummary.start);
    session.end = convertDateStringFromServer(sessionSummary.end);
    session.status =
        sessionSummary.status === 'ACTIVE' ? TutorialGroupSessionStatus.ACTIVE : sessionSummary.status === 'CANCELLED' ? TutorialGroupSessionStatus.CANCELLED : undefined;
    session.statusExplanation = sessionSummary.statusExplanation;
    session.location = sessionSummary.location;
    session.tutorialGroupFreePeriod = sessionSummary.tutorialGroupFreePeriod ? convertTutorialGroupSummaryFreePeriod(sessionSummary.tutorialGroupFreePeriod) : undefined;
    session.attendanceCount = sessionSummary.attendanceCount;
    return session;
}

function convertTutorialGroupSummarySchedule(scheduleSummary: TutorialGroupSummarySchedule): TutorialGroupSchedule {
    const schedule = new TutorialGroupSchedule();
    schedule.id = scheduleSummary.id;
    schedule.dayOfWeek = scheduleSummary.dayOfWeek;
    schedule.startTime = scheduleSummary.startTime;
    schedule.endTime = scheduleSummary.endTime;
    schedule.repetitionFrequency = scheduleSummary.repetitionFrequency;
    schedule.location = scheduleSummary.location;
    schedule.validFromInclusive = convertDateStringFromServer(scheduleSummary.validFromInclusive);
    schedule.validToInclusive = convertDateStringFromServer(scheduleSummary.validToInclusive);
    return schedule;
}

function convertTutorialGroupSummaryChannel(channelSummary: TutorialGroupSummaryChannel): ChannelDTO {
    const channel = new ChannelDTO();
    channel.id = channelSummary.id;
    channel.creationDate = convertDateStringFromServer(channelSummary.creationDate);
    channel.lastMessageDate = convertDateStringFromServer(channelSummary.lastMessageDate);
    channel.lastReadDate = convertDateStringFromServer(channelSummary.lastReadDate);
    channel.unreadMessagesCount = channelSummary.unreadMessagesCount;
    channel.isFavorite = channelSummary.isFavorite;
    channel.isHidden = channelSummary.isHidden;
    channel.isMuted = channelSummary.isMuted;
    channel.isCreator = channelSummary.isCreator;
    channel.isMember = channelSummary.isMember;
    channel.numberOfMembers = channelSummary.numberOfMembers;
    channel.name = channelSummary.name;
    channel.description = channelSummary.description;
    channel.topic = channelSummary.topic;
    channel.isPublic = channelSummary.isPublic ?? false;
    channel.isAnnouncementChannel = channelSummary.isAnnouncementChannel ?? false;
    channel.isArchived = channelSummary.isArchived ?? false;
    channel.isCourseWide = channelSummary.isCourseWide ?? false;
    channel.hasChannelModerationRights = channelSummary.hasChannelModerationRights ?? false;
    channel.isChannelModerator = channelSummary.isChannelModerator ?? false;
    channel.tutorialGroupId = channelSummary.tutorialGroupId;
    channel.tutorialGroupTitle = channelSummary.tutorialGroupTitle;
    channel.subTypeReferenceId = channelSummary.subTypeReferenceId;

    if (channelSummary.creator) {
        const creator = new ConversationUserDTO();
        creator.id = channelSummary.creator.id;
        creator.login = channelSummary.creator.login;
        creator.name = channelSummary.creator.name;
        creator.firstName = channelSummary.creator.firstName;
        creator.lastName = channelSummary.creator.lastName;
        creator.imageUrl = channelSummary.creator.imageUrl;
        creator.isInstructor = channelSummary.creator.isInstructor;
        creator.isEditor = channelSummary.creator.isEditor;
        creator.isTeachingAssistant = channelSummary.creator.isTeachingAssistant;
        creator.isStudent = channelSummary.creator.isStudent;
        creator.isChannelModerator = channelSummary.creator.isChannelModerator;
        creator.isRequestingUser = channelSummary.creator.isRequestingUser;
        channel.creator = creator;
    }

    switch (channelSummary.subType) {
        case 'general':
            channel.subType = ChannelSubType.GENERAL;
            break;
        case 'exercise':
            channel.subType = ChannelSubType.EXERCISE;
            break;
        case 'lecture':
            channel.subType = ChannelSubType.LECTURE;
            break;
        case 'exam':
            channel.subType = ChannelSubType.EXAM;
            break;
        case 'feedbackDiscussion':
            channel.subType = ChannelSubType.FEEDBACK_DISCUSSION;
            break;
    }
    return channel;
}

export function convertTutorialGroupSummaryArrayDatesFromServer(tutorialGroups: TutorialGroupSummary[]): TutorialGroup[] {
    return tutorialGroups.map((tutorialGroupSummary) => {
        const summary = deepClone(tutorialGroupSummary);
        const tutorialGroup = new TutorialGroup();
        tutorialGroup.id = summary.id;
        tutorialGroup.title = summary.title;
        tutorialGroup.capacity = summary.capacity;
        tutorialGroup.campus = summary.campus;
        tutorialGroup.language = summary.language;
        tutorialGroup.additionalInformation = summary.additionalInformation;
        tutorialGroup.isOnline = summary.isOnline;
        tutorialGroup.isUserRegistered = summary.isUserRegistered;
        tutorialGroup.isUserTutor = summary.isUserTutor;
        tutorialGroup.numberOfRegisteredUsers = summary.numberOfRegisteredUsers;
        tutorialGroup.teachingAssistantName = summary.teachingAssistantName;
        tutorialGroup.teachingAssistantId = summary.teachingAssistantId;
        tutorialGroup.teachingAssistantImageUrl = summary.teachingAssistantImageUrl;
        tutorialGroup.courseTitle = summary.courseTitle;
        tutorialGroup.averageAttendance = summary.averageAttendance;
        tutorialGroup.tutorialGroupSchedule = summary.tutorialGroupSchedule ? convertTutorialGroupSummarySchedule(summary.tutorialGroupSchedule) : undefined;
        tutorialGroup.tutorialGroupSessions = summary.tutorialGroupSessions?.map(convertTutorialGroupSummarySession);
        tutorialGroup.nextSession = summary.nextSession ? convertTutorialGroupSummarySession(summary.nextSession) : undefined;
        tutorialGroup.channel = summary.channel ? convertTutorialGroupSummaryChannel(summary.channel) : undefined;
        return tutorialGroup;
    });
}
