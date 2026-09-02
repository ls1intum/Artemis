import { ExerciseGenerationRetainedFile } from 'app/openapi/model/exercise-generation-retained-file';
import { ExerciseGenerationFileChange, HyperionFileChangeAction, HyperionFileChangeRepo } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';
import { REPO_ORDER, displayFileChangePath, newestFileChange } from 'app/hyperion/exercise-generation/hyperion-generation-activity.utils';

/**
 * One file of a generation run's workspace, as the client is currently able to know it.
 *
 * Two sources feed it and neither is complete on its own. The websocket `FILE_CHANGE` events say *that* a file was
 * written and when, but carry no content at all; the retained-artifacts snapshot carries content but only for a run
 * that ended without saving. So a file may legitimately exist here with no content, and `content === undefined` is
 * never rendered as an empty file - {@link artifactContentState} turns it into the sentence that explains why.
 */
export interface HyperionArtifactFile {
    readonly key: string;
    readonly repo: HyperionFileChangeRepo;
    /** Repository-relative path: the `solution/` style prefix is stripped, because the repository is the heading. */
    readonly path: string;
    /** Everything up to and including the last separator. This is the part the row may truncate. */
    readonly directory: string;
    /** The file name, which is never truncated. */
    readonly name: string;
    /** Absent for a file that only the retained snapshot knows about, because a snapshot records no action. */
    readonly action?: HyperionFileChangeAction;
    readonly turn?: number;
    readonly changedAt?: string;
    /** The most recently changed file of the whole run - what the agent is writing now, or what it wrote last. */
    readonly mostRecent: boolean;
    /** Present only where the server actually sends content. Never defaulted to `''`. */
    readonly content?: string;
}

export interface HyperionArtifactRepoGroup {
    readonly repo: HyperionFileChangeRepo;
    readonly labelKey: string;
    readonly files: readonly HyperionArtifactFile[];
    readonly count: number;
    /** Whether the run's most recent change is in this repository, so the tab can say so without opening it. */
    readonly containsMostRecent: boolean;
}

/**
 * Why a file's contents are, or are not, on screen.
 *
 * A discriminated union rather than a nullable string, for the same reason the spend figures use one: it makes
 * "render an unknown as empty" unrepresentable. Every branch below is a sentence the browser can show.
 */
export type HyperionArtifactContentState =
    | { readonly kind: 'text'; readonly content: string; readonly lineCount: number }
    /** The file has content and that content is empty. Distinct from having no content to show. */
    | { readonly kind: 'empty' }
    /** The agent deleted the file; there is nothing to read, and that is not a failure. */
    | { readonly kind: 'deleted' }
    | { readonly kind: 'loading' }
    | { readonly kind: 'failed' }
    /** The run is still going. The server has no endpoint that serves a file mid-run yet. */
    | { readonly kind: 'pendingRun' }
    /** The run saved its work into the exercise, so there is no draft copy - the repository is the truth. */
    | { readonly kind: 'savedToExercise' }
    /** The run ended and kept nothing, so only the file list survives. */
    | { readonly kind: 'notRetained' };

export interface HyperionArtifactContentContext {
    /** The retained snapshot is being fetched. */
    readonly loading: boolean;
    /** The retained snapshot could not be fetched, for a reason other than "there is none". */
    readonly failed: boolean;
    readonly running: boolean;
    readonly savedToExercise: boolean;
}

function repoRelativePath(repo: HyperionFileChangeRepo, path: string): string {
    const prefix = `${repo}/`;
    return path.startsWith(prefix) ? path.slice(prefix.length) : path;
}

function artifactKey(repo: HyperionFileChangeRepo, path: string): string {
    return `${repo}\0${path}`;
}

/**
 * The key {@link artifactFiles} will give this change, so a caller holding the raw change events can map a row the
 * user picked back to the event it came from without re-deriving the key format.
 */
export function artifactKeyForChange(change: ExerciseGenerationFileChange): string {
    return artifactKey(change.repo, displayFileChangePath(change));
}

/**
 * Builds one entry. Every field is written out rather than spread over a previous object, so a merge cannot
 * silently carry a field forward and the shape stays visible at the call site.
 */
function entry(repo: HyperionFileChangeRepo, path: string, rest: Pick<HyperionArtifactFile, 'action' | 'turn' | 'changedAt' | 'mostRecent' | 'content'>): HyperionArtifactFile {
    const separator = path.lastIndexOf('/');
    return {
        key: artifactKey(repo, path),
        repo,
        path,
        directory: separator < 0 ? '' : path.slice(0, separator + 1),
        name: separator < 0 ? path : path.slice(separator + 1),
        action: rest.action,
        turn: rest.turn,
        changedAt: rest.changedAt,
        mostRecent: rest.mostRecent,
        content: rest.content,
    };
}

/**
 * The run's workspace, merged from the change events and the retained snapshot.
 *
 * The snapshot is the union partner rather than a decoration: `fileChanges` rides the status DTO's replay window and
 * is evicted, so a run reopened later can have content for a file whose change event is long gone. Dropping such a
 * file would hide a file that actually exists.
 */
export function artifactFiles(changes: readonly ExerciseGenerationFileChange[], retained: readonly ExerciseGenerationRetainedFile[] = []): HyperionArtifactFile[] {
    const newestKey = (() => {
        const newest = newestFileChange(changes);
        return newest ? artifactKey(newest.repo, displayFileChangePath(newest)) : undefined;
    })();

    const byKey = new Map<string, HyperionArtifactFile>();
    for (const change of changes) {
        const path = displayFileChangePath(change);
        const key = artifactKey(change.repo, path);
        byKey.set(key, entry(change.repo, path, { action: change.action, turn: change.turn, changedAt: change.timestamp, mostRecent: key === newestKey, content: undefined }));
    }
    for (const file of retained) {
        const path = repoRelativePath(file.repo, file.path);
        const key = artifactKey(file.repo, path);
        const existing = byKey.get(key);
        byKey.set(
            key,
            entry(file.repo, path, {
                action: existing?.action,
                turn: existing?.turn,
                changedAt: existing?.changedAt,
                mostRecent: key === newestKey,
                content: file.content,
            }),
        );
    }

    return [...byKey.values()].sort((first, second) => REPO_ORDER.indexOf(first.repo) - REPO_ORDER.indexOf(second.repo) || first.path.localeCompare(second.path));
}

/** The same files grouped by repository, in the one order every Hyperion surface lists repositories in. */
export function artifactRepoGroups(files: readonly HyperionArtifactFile[]): HyperionArtifactRepoGroup[] {
    return REPO_ORDER.map((repo) => {
        const repoFiles = files.filter((file) => file.repo === repo);
        return {
            repo,
            labelKey: `artemisApp.hyperion.generationActivity.repo.${repo}`,
            files: repoFiles,
            count: repoFiles.length,
            containsMostRecent: repoFiles.some((file) => file.mostRecent),
        };
    }).filter((group) => group.count > 0);
}

/**
 * What the content pane shows for one file.
 *
 * Deliberately ordered so that a real answer always beats an explanation: a deleted file and a file whose content
 * arrived are both settled facts and outrank the run's own state.
 */
export function artifactContentState(file: HyperionArtifactFile, context: HyperionArtifactContentContext): HyperionArtifactContentState {
    if (file.action === 'delete') {
        return { kind: 'deleted' };
    }
    const content = file.content;
    if (content !== undefined) {
        return content.length === 0 ? { kind: 'empty' } : { kind: 'text', content, lineCount: content.split('\n').length };
    }
    if (context.loading) {
        return { kind: 'loading' };
    }
    if (context.failed) {
        return { kind: 'failed' };
    }
    if (context.running) {
        return { kind: 'pendingRun' };
    }
    return context.savedToExercise ? { kind: 'savedToExercise' } : { kind: 'notRetained' };
}
