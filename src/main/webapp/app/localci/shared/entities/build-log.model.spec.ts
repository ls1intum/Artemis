import { describe, expect, it } from 'vitest';
import { BuildLogEntry, BuildLogEntryArray, BuildLogType } from 'app/localci/shared/entities/build-log.model';
import { ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';

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

    describe('extractErrors', () => {
        const time = '2026-08-25T10:00:00.000Z';
        const extract = (log: string, language: ProgrammingLanguage, projectType: ProjectType) =>
            BuildLogEntryArray.fromBuildLogs([{ time, log }]).extractErrors(language, projectType);

        it('should parse a Maven error', () => {
            const errors = extract('[ERROR] /home/build/src/main/java/A.java:[12,5] cannot find symbol', ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN);

            expect(errors).toEqual([{ type: 'error', fileName: 'src/main/java/A.java', row: 11, column: 4, text: 'cannot find symbol', timestamp: Date.parse(time) }]);
        });

        it('should keep the text of a Maven error that contains colons and positions', () => {
            const errors = extract('[ERROR] /p/src/A.java:[12,5] incompatible types: int is not String:[3,4] here', ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN);

            expect(errors).toHaveLength(1);
            expect(errors[0]).toMatchObject({ fileName: 'src/A.java', row: 11, column: 4, text: 'incompatible types: int is not String:[3,4] here' });
        });

        it('should use the last source folder of a Maven path', () => {
            const errors = extract('[ERROR] /home/src/project/src/main/A.java:[1,2] oops', ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN);

            expect(errors[0]).toMatchObject({ fileName: 'src/main/A.java' });
        });

        it('should parse a Gradle error', () => {
            const errors = extract('/home/build/src/main/java/A.java:12: error: cannot find symbol: Foo', ProgrammingLanguage.JAVA, ProjectType.PLAIN_GRADLE);

            expect(errors).toHaveLength(1);
            expect(errors[0]).toMatchObject({ fileName: 'src/main/java/A.java', row: 11, text: 'cannot find symbol: Foo' });
        });

        it('should use the first source folder of a Gradle path', () => {
            const errors = extract('/home/src/project/src/main/A.java:3: error: oops', ProgrammingLanguage.JAVA, ProjectType.PLAIN_GRADLE);

            expect(errors[0]).toMatchObject({ fileName: 'src/project/src/main/A.java' });
        });

        it('should parse a Swift error', () => {
            const errors = extract('/build/Sources/App/main.swift:3:7: error: use of unresolved identifier', ProgrammingLanguage.SWIFT, ProjectType.PLAIN);

            expect(errors).toEqual([{ type: 'error', fileName: 'Sources/App/main.swift', row: 2, column: 6, text: 'use of unresolved identifier', timestamp: Date.parse(time) }]);
        });

        it('should ignore lines that are not errors', () => {
            expect(extract('src/main/java/A.java compiled', ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN)).toEqual([]);
        });

        it('should only parse lines up to the maximum length', () => {
            const prefix = '[ERROR] /p/src/A.java:[1,2] ';
            const maxLength = 1024;

            expect(extract(prefix + 'x'.repeat(maxLength - prefix.length), ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN)).toHaveLength(1);
            expect(extract(prefix + 'x'.repeat(maxLength - prefix.length + 1), ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN)).toEqual([]);
        });

        it.each([
            ['Maven', ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN, '[ERROR] ' + 'src/'.repeat(253) + ':['],
            ['Gradle', ProgrammingLanguage.JAVA, ProjectType.PLAIN_GRADLE, 'src/'.repeat(255) + ':1'],
            ['Swift', ProgrammingLanguage.SWIFT, ProjectType.PLAIN, 'Sources/'.repeat(127) + ':1:'],
        ])('should parse many unusual %s lines in reasonable time', (_name, language, projectType, line) => {
            const buildLogs = Array.from({ length: 2000 }, () => ({ time, log: line }));

            const start = performance.now();
            const errors = BuildLogEntryArray.fromBuildLogs(buildLogs).extractErrors(language, projectType);

            expect(errors).toEqual([]);
            expect(performance.now() - start).toBeLessThan(2000);
        });
    });
});
