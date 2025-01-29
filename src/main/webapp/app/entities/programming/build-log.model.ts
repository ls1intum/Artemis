import { safeUnescape } from 'app/shared/util/security.util';
import { Annotation } from 'app/exercises/programming/shared/code-editor/monaco/code-editor-monaco.component';
import { ProgrammingLanguage, ProjectType } from 'app/entities/programming/programming-exercise.model';

export enum BuildLogType {
    ERROR = 'ERROR',
    WARNING = 'WARNING',
    OTHER = 'OTHER',
}

export type BuildLogEntry = {
    time: any;
    log: string;
    type?: BuildLogType;
};

// flag(error, warning),filePath,fileName,line,row,error
type ParsedLogEntry = [string, string, string, string, string, string];

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
            return {
                log,
                type: logType,
                ...rest,
            };
        });
        return new BuildLogEntryArray(...mappedLogs);
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
                .filter(({ log }: { log: ParsedLogEntry | null; time: string }) => {
                    // Java logs do not always contain "ERROR"
                    return log && log.length === 6 && (log[0]?.includes(':[') || log[1] === 'ERROR' || log[4] === 'error:');
                })
                // Sort entries to fit a standard format
                .map(({ log, time }) => {
                    const sortedLog = [...log!];
                    if (programmingLanguage === ProgrammingLanguage.SWIFT || projectType === ProjectType.PLAIN_GRADLE || projectType === ProjectType.GRADLE_GRADLE) {
                        const errorIndicator = sortedLog!.splice(sortedLog!.indexOf('error:'), 1)[0];
                        sortedLog.unshift(errorIndicator);
                    }
                    return { log: sortedLog, time };
                })
                // Map buildLogEntries into annotation format
                .map(({ log: [, , fileName, row, column, text], time }: { log: ParsedLogEntry; time: string }) => ({
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
