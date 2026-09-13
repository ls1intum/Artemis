import { convertDateFromServer } from 'app/foundation/util/date.utils';
import { hydrate } from 'app/foundation/util/deep-clone.util';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { TutorialGroupSummary } from 'app/openapi/model/tutorial-group-summary';
import { TutorialGroupSummarySession } from 'app/openapi/model/tutorial-group-summary-session';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { LegacyTutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { TutorialGroupSchedule } from 'app/tutorialgroup/shared/entities/tutorial-group-schedule.model';
import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';
import dayjs from 'dayjs/esm';

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

function convertGeneratedDateFromServer(date?: string): dayjs.Dayjs | undefined {
    return date ? dayjs(date) : undefined;
}

function convertTutorialGroupSummarySessionDatesFromServer(tutorialGroupSession: TutorialGroupSummarySession): LegacyTutorialGroupSession {
    const convertedSession = new LegacyTutorialGroupSession();
    hydrate(convertedSession, tutorialGroupSession);
    convertedSession.start = convertGeneratedDateFromServer(tutorialGroupSession.start);
    convertedSession.end = convertGeneratedDateFromServer(tutorialGroupSession.end);
    if (tutorialGroupSession.tutorialGroupFreePeriod) {
        const convertedFreePeriod = new TutorialGroupFreePeriod();
        hydrate(convertedFreePeriod, tutorialGroupSession.tutorialGroupFreePeriod);
        convertedFreePeriod.start = convertGeneratedDateFromServer(tutorialGroupSession.tutorialGroupFreePeriod.start);
        convertedFreePeriod.end = convertGeneratedDateFromServer(tutorialGroupSession.tutorialGroupFreePeriod.end);
        convertedSession.tutorialGroupFreePeriod = convertedFreePeriod;
    }
    return convertedSession;
}

export function convertTutorialGroupSummaryArrayDatesFromServer(tutorialGroups: TutorialGroupSummary[]): TutorialGroup[] {
    return tutorialGroups.map((tutorialGroup) => {
        const { tutorialGroupSchedule, tutorialGroupSessions, nextSession, channel, ...properties } = tutorialGroup;
        const convertedTutorialGroup = new TutorialGroup();
        hydrate(convertedTutorialGroup, properties);

        if (tutorialGroupSchedule) {
            const convertedSchedule = new TutorialGroupSchedule();
            hydrate(convertedSchedule, tutorialGroupSchedule);
            convertedSchedule.validFromInclusive = convertGeneratedDateFromServer(tutorialGroupSchedule.validFromInclusive);
            convertedSchedule.validToInclusive = convertGeneratedDateFromServer(tutorialGroupSchedule.validToInclusive);
            convertedTutorialGroup.tutorialGroupSchedule = convertedSchedule;
        }
        convertedTutorialGroup.tutorialGroupSessions = tutorialGroupSessions?.map(convertTutorialGroupSummarySessionDatesFromServer);
        convertedTutorialGroup.nextSession = nextSession ? convertTutorialGroupSummarySessionDatesFromServer(nextSession) : undefined;

        if (channel) {
            const convertedChannel = new ChannelDTO();
            hydrate(convertedChannel, channel);
            convertedChannel.creationDate = convertGeneratedDateFromServer(channel.creationDate);
            convertedChannel.lastMessageDate = convertGeneratedDateFromServer(channel.lastMessageDate);
            convertedChannel.lastReadDate = convertGeneratedDateFromServer(channel.lastReadDate);
            convertedChannel.subTypeReferenceStartDate = convertGeneratedDateFromServer(channel.subTypeReferenceStartDate);
            convertedChannel.subTypeReferenceEndDate = convertGeneratedDateFromServer(channel.subTypeReferenceEndDate);
            convertedTutorialGroup.channel = convertedChannel;
        }

        return convertedTutorialGroup;
    });
}
