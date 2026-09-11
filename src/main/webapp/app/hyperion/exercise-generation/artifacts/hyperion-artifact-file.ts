import { ExerciseGenerationFileChange, HyperionFileChangeAction, HyperionFileChangeRepo } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';
import { REPO_ORDER, displayFileChangePath, newestFileChange } from 'app/hyperion/exercise-generation/hyperion-generation-activity.utils';

/** File-change metadata only; source contents belong in the code editor. */
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
}

export interface HyperionArtifactRepoGroup {
    readonly repo: HyperionFileChangeRepo;
    readonly labelKey: string;
    readonly files: readonly HyperionArtifactFile[];
    readonly count: number;
    /** Whether the run's most recent change is in this repository, so the tab can say so without opening it. */
    readonly containsMostRecent: boolean;
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
function entry(repo: HyperionFileChangeRepo, path: string, rest: Pick<HyperionArtifactFile, 'action' | 'turn' | 'changedAt' | 'mostRecent'>): HyperionArtifactFile {
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
    };
}

/** Latest file metadata, grouped consistently by repository and path. */
export function artifactFiles(changes: readonly ExerciseGenerationFileChange[]): HyperionArtifactFile[] {
    const newestKey = (() => {
        const newest = newestFileChange(changes);
        return newest ? artifactKey(newest.repo, displayFileChangePath(newest)) : undefined;
    })();

    const byKey = new Map<string, HyperionArtifactFile>();
    for (const change of changes) {
        const path = displayFileChangePath(change);
        const key = artifactKey(change.repo, path);
        byKey.set(key, entry(change.repo, path, { action: change.action, turn: change.turn, changedAt: change.timestamp, mostRecent: key === newestKey }));
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
