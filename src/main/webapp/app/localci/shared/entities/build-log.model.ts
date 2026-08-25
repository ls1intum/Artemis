import { safeUnescape } from 'app/foundation/util/security.util';
import { ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { Annotation } from 'app/programming/shared/code-editor/monaco/code-editor-monaco.component';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

export enum BuildLogType {
    ERROR = 'ERROR',
    WARNING = 'WARNING',
    OTHER = 'OTHER',
}

export type BuildLogEntry = {
    time: string;
    log: string;
    type?: BuildLogType;
};

// flag(error, warning),filePath,fileName,line,row,error
type ParsedLogEntry = [string, string, string, string, string, string];

/**
 * Orders build log entries by their timestamp, leaving every entry whose timestamp cannot be parsed at the position it
 * came in at.
 *
 * The entries with a usable timestamp are sorted among themselves and written back into the slots they occupied, so an
 * unparseable entry never moves and never separates two entries that do compare. Comparing straight through the
 * unparseable ones instead would make the comparison intransitive, which leaves the result of a sort undefined.
 *
 * @param entries the entries to order, which are not modified
 * @returns a new array holding the same entries in display order
 */
function sortByTime(entries: BuildLogEntry[]): BuildLogEntry[] {
    const sortable: { entry: BuildLogEntry; index: number; time: number }[] = [];
    entries.forEach((entry, index) => {
        const time = Date.parse(entry.time);
        if (!Number.isNaN(time)) {
            sortable.push({ entry, index, time });
        }
    });

    const ordered = [...entries];
    sortable
        .map(({ entry, time }) => ({ entry, time }))
        .sort((first, second) => first.time - second.time)
        .forEach(({ entry }, position) => (ordered[sortable[position].index] = entry));
    return ordered;
}

/**
 * Wrapper class for build log output.
 */
export class BuildLogEntryArray extends Array<BuildLogEntry> {
    private mavenErrorLogRegex = /\[?(ERROR)?\]?.*\/?(src\/.+):\[(\d+),(\d+)\]\s(.*$)/;
    private gradleErrorLogRegex = /(src\/.+):(\d+)():\s(error:)\s(.*$)/;
    private swiftErrorLogRegex = /.*\/?(Sources\/.+):(\d+):(\d+):\s(error:)(.*$)/;

    /**
     * Factory method for creating an instance of the class. Prefer this method over the default constructor.
     *
     * The entries are sorted by their timestamp. The server already returns them in order, but that order relies on a
     * mapping detail that is easy to lose somewhere on the way here, and build output is unreadable once the lines are
     * shuffled, so the client does not depend on it.
     *
     * @param buildLogs BuildLogEntry[]
     */
    static fromBuildLogs(buildLogs: BuildLogEntry[]) {
        const mappedLogs = buildLogs.map(({ log, ...rest }) => {
            let logType = BuildLogType.OTHER;
            if (log) {
                if (log.trimStart().startsWith('[ERROR]')) {
                    logType = BuildLogType.ERROR;
                } else if (log.trimStart().startsWith('WARNING')) {
                    logType = BuildLogType.WARNING;
                }
            }
            return cloneWith({ log, type: logType }, rest);
        });
        return new BuildLogEntryArray(...sortByTime(mappedLogs));
    }

    /**
     * Filters compilation errors from build log.
     * Safely unescapes messages within the build log to avoid vulnerability to injection.
     *
     */
    extractErrors(programmingLanguage?: ProgrammingLanguage, projectType?: ProjectType): Array<Annotation> {
        let errorLogRegex: RegExp;
        // TODO: implement build error regex for other programming languages
        if (programmingLanguage === ProgrammingLanguage.SWIFT) {
            errorLogRegex = this.swiftErrorLogRegex;
        } else if (projectType === ProjectType.PLAIN_GRADLE || projectType === ProjectType.GRADLE_GRADLE) {
            errorLogRegex = this.gradleErrorLogRegex;
        } else {
            errorLogRegex = this.mavenErrorLogRegex;
        }
        return Array.from(
            this
                // Parse build logs
                .map(({ log, time }) => ({ log: log.split('\n', 1)[0].trim().match(errorLogRegex), time }))
                // Remove entries that could not be parsed, are too short or not errors
                .filter((entry: { log: RegExpMatchArray | null; time: string }): entry is { log: ParsedLogEntry; time: string } => {
                    const { log } = entry;
                    // Java logs do not always contain "ERROR"
                    return !!log && log.length === 6 && (log[0]?.includes(':[') || log[1] === 'ERROR' || log[4] === 'error:');
                })
                // Sort entries to fit a standard format
                .map(({ log, time }) => {
                    const sortedLog = [...log];
                    if (programmingLanguage === ProgrammingLanguage.SWIFT || projectType === ProjectType.PLAIN_GRADLE || projectType === ProjectType.GRADLE_GRADLE) {
                        const errorIndicator = sortedLog.splice(sortedLog.indexOf('error:'), 1)[0];
                        sortedLog.unshift(errorIndicator);
                    }
                    return { log: sortedLog, time };
                })
                // Map buildLogEntries into annotation format
                .map(({ log: [, , fileName, row, column, text], time }: { log: string[]; time: string }) => ({
                    type: 'error',
                    fileName,
                    row: Math.max(parseInt(row, 10) - 1, 0),
                    column: Math.max(parseInt(column, 10) - 1, 0),
                    text: safeUnescape(text) || '',
                    timestamp: Date.parse(time),
                })),
        );
    }
}
