import { safeUnescape } from 'app/shared/util/security-util';
import { Annotation } from 'app/exercises/programming/shared/code-editor/ace/code-editor-ace.component';

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
    private errorLogRegex = /\[?(ERROR)?\]?.*\/?(src\/.+):\[(\d+),(\d+)\]\s(.*$)/;

    /**
     * Factory method for creating an instance of the class. Prefer this method over the default constructor.
     *
     * @param buildLogs BuildLogEntry[]
     */
    static fromBuildLogs(buildLogs: BuildLogEntry[]) {
        const mappedLogs = buildLogs.map(({ log, ...rest }) => {
            let logType = BuildLogType.OTHER;
            if (log) {
                if (log.trimLeft().startsWith('[ERROR]')) {
                    logType = BuildLogType.ERROR;
                } else if (log.trimLeft().startsWith('WARNING')) {
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
    extractErrors(): Array<Annotation> {
        return (
            this
                // Parse build logs
                .map(({ log, time }) => ({ log: log.match(this.errorLogRegex), time }))
                // Remove entries that could not be parsed, are too short or not errors
                .filter(({ log }: { log: ParsedLogEntry | null; time: string }) => log && log.length === 6 && (log[0]?.includes(':[') || log[1] === 'ERROR'))
                // Map buildLogEntries into annotation format
                .map(({ log: [, , fileName, row, column, text], time }: { log: ParsedLogEntry; time: string }) => ({
                    type: 'error',
                    fileName,
                    row: Math.max(parseInt(row, 10) - 1, 0),
                    column: Math.max(parseInt(column, 10) - 1, 0),
                    text: safeUnescape(text) || '',
                    timestamp: Date.parse(time),
                }))
        );
    }
}
