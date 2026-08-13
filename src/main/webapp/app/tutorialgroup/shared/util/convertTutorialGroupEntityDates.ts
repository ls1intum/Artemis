import { convertDateFromServer } from 'app/foundation/util/date.utils';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { LegacyTutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { TutorialGroupSchedule } from 'app/tutorialgroup/shared/entities/tutorial-group-schedule.model';
import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';
import { TutorialGroupSummary } from 'app/openapi/model/tutorial-group-summary';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';

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

export function convertTutorialGroupSummaryArrayDatesFromServer(tutorialGroups: TutorialGroupSummary[]): TutorialGroup[] {
    return tutorialGroups.map(({ tutorialGroupSchedule, tutorialGroupSessions, nextSession, channel, ...properties }) => {
        const tutorialGroup = Object.assign(new TutorialGroup(), properties);
        tutorialGroup.tutorialGroupSchedule = tutorialGroupSchedule ? Object.assign(new TutorialGroupSchedule(), tutorialGroupSchedule) : undefined;
        tutorialGroup.tutorialGroupSessions = tutorialGroupSessions?.map((session) => Object.assign(new LegacyTutorialGroupSession(), session));
        tutorialGroup.nextSession = nextSession ? Object.assign(new LegacyTutorialGroupSession(), nextSession) : undefined;
        tutorialGroup.channel = channel ? Object.assign(new ChannelDTO(), channel) : undefined;
        return convertTutorialGroupDatesFromServer(tutorialGroup);
    });
}
