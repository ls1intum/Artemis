import { HttpResponse } from '@angular/common/http';
import dayjs from 'dayjs/esm';
import { describe, expect, it } from 'vitest';

import {
    convertTutorialGroupArrayDatesFromServer,
    convertTutorialGroupDatesFromServer,
    convertTutorialGroupFreePeriodDatesFromServer,
    convertTutorialGroupResponseArrayDatesFromServer,
    convertTutorialGroupSessionDatesFromServer,
    convertTutorialGroupsConfigurationDatesFromServer,
} from 'app/tutorialgroup/shared/util/convertTutorialGroupEntityDates';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { TutorialGroupSchedule } from 'app/tutorialgroup/shared/entities/tutorial-group-schedule.model';
import { TutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';
import { Dayjs } from 'dayjs/esm';

const START = '2026-03-26T10:00:00.000Z';
const END = '2026-03-26T12:00:00.000Z';
const NEXT_START = '2026-03-27T10:00:00.000Z';
const NEXT_END = '2026-03-27T12:00:00.000Z';
const VALID_FROM = '2026-03-01T00:00:00.000Z';
const VALID_TO = '2026-03-31T00:00:00.000Z';

function rawServerDate(value: string): Dayjs {
    return value as unknown as Dayjs;
}

describe('convertTutorialGroupEntityDates', () => {
    it('should convert tutorial group free period dates from the server', () => {
        const freePeriod = {
            start: rawServerDate(START),
            end: rawServerDate(END),
        } as TutorialGroupFreePeriod;

        const result = convertTutorialGroupFreePeriodDatesFromServer(freePeriod);

        expect(dayjs.isDayjs(result.start)).toBe(true);
        expect(dayjs.isDayjs(result.end)).toBe(true);
        expect(result.start?.toISOString()).toBe(START);
        expect(result.end?.toISOString()).toBe(END);
    });

    it('should convert tutorial group session dates including a nested free period', () => {
        const session = {
            start: rawServerDate(START),
            end: rawServerDate(END),
            tutorialGroupFreePeriod: {
                start: rawServerDate(NEXT_START),
                end: rawServerDate(NEXT_END),
            } as TutorialGroupFreePeriod,
        } as TutorialGroupSession;

        const result = convertTutorialGroupSessionDatesFromServer(session);

        expect(dayjs.isDayjs(result.start)).toBe(true);
        expect(dayjs.isDayjs(result.end)).toBe(true);
        expect(result.start?.toISOString()).toBe(START);
        expect(result.end?.toISOString()).toBe(END);
        expect(dayjs.isDayjs(result.tutorialGroupFreePeriod?.start)).toBe(true);
        expect(dayjs.isDayjs(result.tutorialGroupFreePeriod?.end)).toBe(true);
        expect(result.tutorialGroupFreePeriod?.start?.toISOString()).toBe(NEXT_START);
        expect(result.tutorialGroupFreePeriod?.end?.toISOString()).toBe(NEXT_END);
    });

    it('should convert tutorial groups configuration dates including nested free periods', () => {
        const configuration = {
            tutorialPeriodStartInclusive: rawServerDate(VALID_FROM),
            tutorialPeriodEndInclusive: rawServerDate(VALID_TO),
            tutorialGroupFreePeriods: [
                {
                    start: rawServerDate(START),
                    end: rawServerDate(END),
                } as TutorialGroupFreePeriod,
            ],
        } as TutorialGroupsConfiguration;

        const result = convertTutorialGroupsConfigurationDatesFromServer(configuration);

        expect(dayjs.isDayjs(result.tutorialPeriodStartInclusive)).toBe(true);
        expect(dayjs.isDayjs(result.tutorialPeriodEndInclusive)).toBe(true);
        expect(result.tutorialPeriodStartInclusive?.toISOString()).toBe(VALID_FROM);
        expect(result.tutorialPeriodEndInclusive?.toISOString()).toBe(VALID_TO);
        expect(dayjs.isDayjs(result.tutorialGroupFreePeriods?.[0].start)).toBe(true);
        expect(dayjs.isDayjs(result.tutorialGroupFreePeriods?.[0].end)).toBe(true);
    });

    it('should convert tutorial group dates including schedule, sessions, next session, and configuration', () => {
        const tutorialGroup = {
            tutorialGroupSchedule: {
                validFromInclusive: rawServerDate(VALID_FROM),
                validToInclusive: rawServerDate(VALID_TO),
            } as TutorialGroupSchedule,
            tutorialGroupSessions: [
                {
                    start: rawServerDate(START),
                    end: rawServerDate(END),
                } as TutorialGroupSession,
            ],
            nextSession: {
                start: rawServerDate(NEXT_START),
                end: rawServerDate(NEXT_END),
            } as TutorialGroupSession,
            course: {
                tutorialGroupsConfiguration: {
                    tutorialPeriodStartInclusive: rawServerDate(VALID_FROM),
                    tutorialPeriodEndInclusive: rawServerDate(VALID_TO),
                } as TutorialGroupsConfiguration,
            } as TutorialGroup['course'],
        } as TutorialGroup;

        const result = convertTutorialGroupDatesFromServer(tutorialGroup);

        expect(dayjs.isDayjs(result.tutorialGroupSchedule?.validFromInclusive)).toBe(true);
        expect(dayjs.isDayjs(result.tutorialGroupSchedule?.validToInclusive)).toBe(true);
        expect(result.tutorialGroupSchedule?.validFromInclusive?.toISOString()).toBe(VALID_FROM);
        expect(result.tutorialGroupSchedule?.validToInclusive?.toISOString()).toBe(VALID_TO);
        expect(dayjs.isDayjs(result.tutorialGroupSessions?.[0].start)).toBe(true);
        expect(dayjs.isDayjs(result.tutorialGroupSessions?.[0].end)).toBe(true);
        expect(dayjs.isDayjs(result.nextSession?.start)).toBe(true);
        expect(dayjs.isDayjs(result.nextSession?.end)).toBe(true);
        expect(dayjs.isDayjs(result.course?.tutorialGroupsConfiguration?.tutorialPeriodStartInclusive)).toBe(true);
        expect(dayjs.isDayjs(result.course?.tutorialGroupsConfiguration?.tutorialPeriodEndInclusive)).toBe(true);
    });

    it('should convert tutorial group arrays from the server', () => {
        const tutorialGroups = [
            {
                nextSession: {
                    start: rawServerDate(START),
                    end: rawServerDate(END),
                } as TutorialGroupSession,
            } as TutorialGroup,
        ];

        const result = convertTutorialGroupArrayDatesFromServer(tutorialGroups);

        expect(dayjs.isDayjs(result[0].nextSession?.start)).toBe(true);
        expect(dayjs.isDayjs(result[0].nextSession?.end)).toBe(true);
    });

    it('should convert tutorial group response arrays from the server', () => {
        const response = new HttpResponse<TutorialGroup[]>({
            body: [
                {
                    nextSession: {
                        start: rawServerDate(START),
                        end: rawServerDate(END),
                    } as TutorialGroupSession,
                } as TutorialGroup,
            ],
        });

        const result = convertTutorialGroupResponseArrayDatesFromServer(response);

        expect(dayjs.isDayjs(result.body?.[0].nextSession?.start)).toBe(true);
        expect(dayjs.isDayjs(result.body?.[0].nextSession?.end)).toBe(true);
    });
});
