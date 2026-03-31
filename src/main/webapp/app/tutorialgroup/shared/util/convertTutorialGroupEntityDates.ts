import { HttpResponse } from '@angular/common/http';
import { convertDateFromServer } from 'app/shared/util/date.utils';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';

export function convertTutorialGroupFreePeriodDatesFromServer(tutorialGroupFreePeriod: TutorialGroupFreePeriod): TutorialGroupFreePeriod {
    tutorialGroupFreePeriod.start = convertDateFromServer(tutorialGroupFreePeriod.start);
    tutorialGroupFreePeriod.end = convertDateFromServer(tutorialGroupFreePeriod.end);
    return tutorialGroupFreePeriod;
}

export function convertTutorialGroupSessionDatesFromServer(tutorialGroupSession: TutorialGroupSession): TutorialGroupSession {
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
        tutorialGroup.tutorialGroupSessions.forEach((tutorialGroupSession: TutorialGroupSession) => convertTutorialGroupSessionDatesFromServer(tutorialGroupSession));
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

export function convertTutorialGroupResponseArrayDatesFromServer(res: HttpResponse<TutorialGroup[]>): HttpResponse<TutorialGroup[]> {
    if (res.body) {
        res.body.forEach((tutorialGroup: TutorialGroup) => {
            convertTutorialGroupDatesFromServer(tutorialGroup);
        });
    }
    return res;
}
