import dayjs from 'dayjs/esm';
import { describe, expect, it } from 'vitest';

import { convertTutorialGroupFreePeriodDatesFromServer, convertTutorialGroupSummaryArrayDatesFromServer } from 'app/tutorialgroup/shared/util/convertTutorialGroupEntityDates';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { Dayjs } from 'dayjs/esm';
import { TutorialGroupSummary } from 'app/openapi/model/tutorial-group-summary';

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

    it('should convert generated tutorial group summary dates without mutating the response', () => {
        const tutorialGroupSummaries: TutorialGroupSummary[] = [
            {
                tutorialGroupSchedule: {
                    validFromInclusive: VALID_FROM,
                    validToInclusive: VALID_TO,
                },
                nextSession: {
                    start: NEXT_START,
                    end: NEXT_END,
                    tutorialGroupFreePeriod: {
                        start: START,
                        end: END,
                        reason: 'Original reason',
                    },
                },
                channel: {
                    creator: {
                        name: 'Original creator',
                    },
                },
            },
        ];

        const result = convertTutorialGroupSummaryArrayDatesFromServer(tutorialGroupSummaries);

        expect(dayjs.isDayjs(result[0].tutorialGroupSchedule?.validFromInclusive)).toBe(true);
        expect(dayjs.isDayjs(result[0].tutorialGroupSchedule?.validToInclusive)).toBe(true);
        expect(dayjs.isDayjs(result[0].nextSession?.start)).toBe(true);
        expect(dayjs.isDayjs(result[0].nextSession?.end)).toBe(true);
        expect(dayjs.isDayjs(result[0].nextSession?.tutorialGroupFreePeriod?.start)).toBe(true);
        result[0].nextSession!.tutorialGroupFreePeriod!.reason = 'Changed reason';
        result[0].channel!.creator!.name = 'Changed creator';
        expect(tutorialGroupSummaries[0].tutorialGroupSchedule?.validFromInclusive).toBe(VALID_FROM);
        expect(tutorialGroupSummaries[0].nextSession?.start).toBe(NEXT_START);
        expect(tutorialGroupSummaries[0].nextSession?.tutorialGroupFreePeriod?.reason).toBe('Original reason');
        expect(tutorialGroupSummaries[0].channel?.creator?.name).toBe('Original creator');
    });
});
