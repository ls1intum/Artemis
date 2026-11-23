// Posts the query report's new findings as review comments on the line of code that causes them.
//
// GitHub accepts a review comment only on a line of the pull request's diff, so a finding whose line
// the PR did not touch stays in the summary comment. Each comment carries a hidden marker with the
// finding's key: a re-run leaves the comments that are still current alone, deletes the ones whose
// finding is gone and posts only the new ones, so a PR is not notified again about the same finding.

const fs = require('fs');

const MARKER = /<!-- query-inline:([0-9a-f]+) -->/;

/**
 * The lines of the new version of a file that a unified diff shows (added and unchanged lines).
 * @param {string|undefined} patch the `patch` of a file as the pull request files API returns it
 * @returns {Set<number>}
 */
function visibleLines(patch) {
    const lines = new Set();
    if (!patch) {
        return lines;
    }
    let next = 0;
    for (const row of patch.split('\n')) {
        const hunk = /^@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@/.exec(row);
        if (hunk) {
            next = Number(hunk[1]);
        } else if (next > 0 && !row.startsWith('-') && !row.startsWith('\\')) {
            lines.add(next);
            next++;
        }
    }
    return lines;
}

/**
 * The comments that can be shown on a line of the diff: on their own line, or else on their
 * fallback line.
 * @param {{path: string, line: number, key: string, body: string, fallback?: {path: string, line: number}}[]} comments
 * @param {{filename: string, patch?: string}[]} files
 */
function anchorable(comments, files) {
    const visible = new Map(files.map((f) => [f.filename, visibleLines(f.patch)]));
    const shown = ({ path, line }) => visible.get(path)?.has(line);
    const anchored = [];
    for (const c of comments) {
        if (shown(c)) {
            anchored.push(c);
        } else if (c.fallback && shown(c.fallback)) {
            anchored.push({ ...c, path: c.fallback.path, line: c.fallback.line });
        }
    }
    return anchored;
}

/**
 * Splits the comments into what to post, which of the earlier comments to rewrite because their
 * text changed (an edit does not notify anyone) and which to delete.
 * @param {{key: string, body: string}[]} desired the comments that should exist
 * @param {{id: number, body: string}[]} existing the review comments already on the pull request
 */
function plan(desired, existing) {
    const ours = existing
        .map((c) => ({ id: c.id, body: c.body, key: MARKER.exec(c.body || '')?.[1] }))
        .filter((c) => c.key);
    const wanted = new Map(desired.map((c) => [c.key, c]));
    const present = new Map(ours.map((c) => [c.key, c]));
    return {
        post: desired.filter((c) => !present.has(c.key)),
        update: ours.filter((c) => wanted.has(c.key) && wanted.get(c.key).body !== c.body).map((c) => ({ id: c.id, body: wanted.get(c.key).body })),
        remove: ours.filter((c) => !wanted.has(c.key)).map((c) => c.id),
    };
}

/**
 * @returns {Promise<{anchored: number, posted: number, updated: number, removed: number}>}
 */
async function postInlineQueryComments({ github, context, prNumber, commentsPath, headSha, removeStale = true }) {
    let comments = [];
    try {
        comments = JSON.parse(fs.readFileSync(commentsPath, 'utf8'));
    } catch {
        // no report, or no baseline to compare with: nothing to post, but earlier comments may be stale
    }
    const { owner, repo } = context.repo;
    const files = await github.paginate(github.rest.pulls.listFiles, { owner, repo, pull_number: prNumber, per_page: 100 });
    const existing = await github.paginate(github.rest.pulls.listReviewComments, { owner, repo, pull_number: prNumber, per_page: 100 });
    const desired = anchorable(comments, files);
    const { post, update, remove: stale } = plan(desired, existing);
    // a job that reports only some of the tests must not delete what another job reported
    const remove = removeStale ? stale : [];

    for (const id of remove) {
        await github.rest.pulls.deleteReviewComment({ owner, repo, comment_id: id });
    }
    for (const { id, body } of update) {
        await github.rest.pulls.updateReviewComment({ owner, repo, comment_id: id, body });
    }
    let posted = 0;
    if (post.length > 0) {
        const review = post.map((c) => ({ path: c.path, line: c.line, side: 'RIGHT', body: c.body }));
        try {
            await github.rest.pulls.createReview({ owner, repo, pull_number: prNumber, commit_id: headSha, event: 'COMMENT', comments: review });
            posted = post.length;
        } catch (error) {
            // one rejected line fails the whole review; fall back to one comment at a time
            for (const c of review) {
                try {
                    await github.rest.pulls.createReviewComment({ owner, repo, pull_number: prNumber, commit_id: headSha, ...c });
                    posted++;
                } catch (single) {
                    console.log(`Could not comment on ${c.path}:${c.line}: ${single.message}`);
                }
            }
        }
    }
    return { anchored: desired.length, posted, updated: update.length, removed: remove.length };
}

module.exports = { visibleLines, anchorable, plan, postInlineQueryComments };
