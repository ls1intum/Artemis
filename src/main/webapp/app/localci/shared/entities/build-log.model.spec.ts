import { describe, expect, it } from 'vitest';
import { BuildLogEntry, BuildLogEntryArray, BuildLogType } from 'app/localci/shared/entities/build-log.model';

describe('BuildLogEntryArray', () => {
    describe('fromBuildLogs', () => {
        it('should order the entries by their timestamp', () => {
            const buildLogs: BuildLogEntry[] = [
                { time: '2026-08-25T10:00:02.000Z', log: 'third' },
                { time: '2026-08-25T10:00:00.000Z', log: 'first' },
                { time: '2026-08-25T10:00:01.000Z', log: 'second' },
            ];

            const buildLogEntries = BuildLogEntryArray.fromBuildLogs(buildLogs);

            expect(Array.from(buildLogEntries, (entry) => entry.log)).toEqual(['first', 'second', 'third']);
        });

        it('should not change the order of entries logged at the same time', () => {
            const buildLogs: BuildLogEntry[] = [
                { time: '2026-08-25T10:00:00.000Z', log: 'first' },
                { time: '2026-08-25T10:00:00.000Z', log: 'second' },
                { time: '2026-08-25T10:00:00.000Z', log: 'third' },
            ];

            const buildLogEntries = BuildLogEntryArray.fromBuildLogs(buildLogs);

            expect(Array.from(buildLogEntries, (entry) => entry.log)).toEqual(['first', 'second', 'third']);
        });

        it('should order the parseable entries around one that cannot be parsed', () => {
            const buildLogs: BuildLogEntry[] = [
                { time: '2026-08-25T10:00:02.000Z', log: 'late' },
                { time: 'not a date', log: 'invalid' },
                { time: '2026-08-25T10:00:00.000Z', log: 'early' },
            ];

            const buildLogEntries = BuildLogEntryArray.fromBuildLogs(buildLogs);

            // The unparseable entry keeps its slot, the two around it are ordered.
            expect(Array.from(buildLogEntries, (entry) => entry.log)).toEqual(['early', 'invalid', 'late']);
        });

        it('should keep the given order when a timestamp cannot be parsed', () => {
            const buildLogs: BuildLogEntry[] = [
                { time: 'not a date', log: 'first' },
                { time: '2026-08-25T10:00:00.000Z', log: 'second' },
            ];

            const buildLogEntries = BuildLogEntryArray.fromBuildLogs(buildLogs);

            expect(Array.from(buildLogEntries, (entry) => entry.log)).toEqual(['first', 'second']);
        });

        it('should not modify the given array', () => {
            const buildLogs: BuildLogEntry[] = [
                { time: '2026-08-25T10:00:01.000Z', log: 'second' },
                { time: '2026-08-25T10:00:00.000Z', log: 'first' },
            ];

            BuildLogEntryArray.fromBuildLogs(buildLogs);

            expect(buildLogs.map((entry) => entry.log)).toEqual(['second', 'first']);
        });

        it('should derive the log type from the message', () => {
            const buildLogs: BuildLogEntry[] = [
                { time: '2026-08-25T10:00:00.000Z', log: '[ERROR] something broke' },
                { time: '2026-08-25T10:00:01.000Z', log: '  WARNING careful' },
                { time: '2026-08-25T10:00:02.000Z', log: 'just a line' },
            ];

            const buildLogEntries = BuildLogEntryArray.fromBuildLogs(buildLogs);

            expect(Array.from(buildLogEntries, (entry) => entry.type)).toEqual([BuildLogType.ERROR, BuildLogType.WARNING, BuildLogType.OTHER]);
        });
    });
});
