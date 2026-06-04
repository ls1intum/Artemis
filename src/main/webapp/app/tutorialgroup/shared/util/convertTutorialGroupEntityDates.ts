import { HttpResponse } from '@angular/common/http';
import { convertDateFromServer } from 'app/foundation/util/date.utils';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { LegacyTutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';
import { TutorialGroupSummary } from 'app/openapi/model/tutorialGroupSummary';
import { TutorialGroupSummarySession } from 'app/openapi/model/tutorialGroupSummarySession';
import { TutorialGroupFreePeriod as TutorialGroupFreePeriodOpenApi } from 'app/openapi/model/tutorialGroupFreePeriod';

export function convertTutorialGroupFreePeriodDatesFromServer<T extends TutorialGroupFreePeriod | TutorialGroupFreePeriodOpenApi>(tutorialGroupFreePeriod: T): T {
    tutorialGroupFreePeriod.start = convertDateFromServer(tutorialGroupFreePeriod.start) as any;
    tutorialGroupFreePeriod.end = convertDateFromServer(tutorialGroupFreePeriod.end) as any;
    return tutorialGroupFreePeriod;
}

export function convertTutorialGroupSessionDatesFromServer<T extends LegacyTutorialGroupSession | TutorialGroupSummarySession>(tutorialGroupSession: T): T {
    tutorialGroupSession.start = convertDateFromServer(tutorialGroupSession.start) as any;
    tutorialGroupSession.end = convertDateFromServer(tutorialGroupSession.end) as any;
    if (tutorialGroupSession.tutorialGroupFreePeriod) {
        tutorialGroupSession.tutorialGroupFreePeriod = convertTutorialGroupFreePeriodDatesFromServer(tutorialGroupSession.tutorialGroupFreePeriod);
    }
    return tutorialGroupSession;
}

export function convertTutorialGroupsConfigurationDatesFromServer(tutorialGroupsConfiguration: TutorialGroupsConfiguration): TutorialGroupsConfiguration {
    tutorialGroupsConfiguration.tutorialPeriodStartInclusive = convertDateFromServer(tutorialGroupsConfiguration.tutorialPeriodStartInclusive) as any;
    tutorialGroupsConfiguration.tutorialPeriodEndInclusive = convertDateFromServer(tutorialGroupsConfiguration.tutorialPeriodEndInclusive) as any;
    if (tutorialGroupsConfiguration.tutorialGroupFreePeriods) {
        tutorialGroupsConfiguration.tutorialGroupFreePeriods.forEach((tutorialGroupFreePeriod) => {
            tutorialGroupFreePeriod.start = convertDateFromServer(tutorialGroupFreePeriod.start) as any;
            tutorialGroupFreePeriod.end = convertDateFromServer(tutorialGroupFreePeriod.end) as any;
        });
    }
    return tutorialGroupsConfiguration;
}

export function convertTutorialGroupDatesFromServer<T extends TutorialGroup | TutorialGroupSummary>(tutorialGroup: T): T {
    if (tutorialGroup.tutorialGroupSchedule) {
        tutorialGroup.tutorialGroupSchedule.validFromInclusive = convertDateFromServer(tutorialGroup.tutorialGroupSchedule.validFromInclusive) as any;
        tutorialGroup.tutorialGroupSchedule.validToInclusive = convertDateFromServer(tutorialGroup.tutorialGroupSchedule.validToInclusive) as any;
    }
    if (tutorialGroup.tutorialGroupSessions) {
        tutorialGroup.tutorialGroupSessions.forEach((tutorialGroupSession: LegacyTutorialGroupSession | TutorialGroupSummarySession) =>
            convertTutorialGroupSessionDatesFromServer(tutorialGroupSession),
        );
    }
    if ('nextSession' in tutorialGroup && tutorialGroup.nextSession) {
        tutorialGroup.nextSession = convertTutorialGroupSessionDatesFromServer(tutorialGroup.nextSession);
    }
    if ('course' in tutorialGroup && tutorialGroup.course?.tutorialGroupsConfiguration) {
        tutorialGroup.course.tutorialGroupsConfiguration = convertTutorialGroupsConfigurationDatesFromServer(tutorialGroup.course?.tutorialGroupsConfiguration);
    }
    return tutorialGroup;
}

export function convertTutorialGroupArrayDatesFromServer<T extends TutorialGroup | TutorialGroupSummary>(tutorialGroups: T[]): T[] {
    if (tutorialGroups) {
        tutorialGroups.forEach((tutorialGroup: T) => {
            convertTutorialGroupDatesFromServer(tutorialGroup);
        });
    }
    return tutorialGroups;
}

export function convertTutorialGroupResponseArrayDatesFromServer<T extends TutorialGroup | TutorialGroupSummary>(res: HttpResponse<T[]>): HttpResponse<T[]> {
    if (res.body) {
        res.body.forEach((tutorialGroup: T) => {
            convertTutorialGroupDatesFromServer(tutorialGroup);
        });
    }
    return res;
}
