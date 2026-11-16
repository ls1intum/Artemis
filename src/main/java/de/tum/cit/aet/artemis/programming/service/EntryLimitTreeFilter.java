package de.tum.cit.aet.artemis.programming.service;

import java.io.IOException;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

/**
 * Counts every entry that a tree walk looks at, including the ones that the wrapped filter excludes, and stops the walk when there are more than the limit.
 * <p>
 * A walk that only counts the entries it returns does not see the entries that a path filter rejects, or the subtrees that it enters on its own, so the work for a tree that
 * references the same subtrees many times would not be bounded.
 */
final class EntryLimitTreeFilter extends TreeFilter {

    private final TreeFilter delegate;

    private final int maxEntries;

    private int visitedEntries;

    EntryLimitTreeFilter(TreeFilter delegate, int maxEntries) {
        this.delegate = delegate;
        this.maxEntries = maxEntries;
    }

    @Override
    public boolean include(TreeWalk walker) throws MissingObjectException, IncorrectObjectTypeException, IOException {
        if (++visitedEntries > maxEntries) {
            throw new IOException("The tree of the commit has more than " + maxEntries + " entries.");
        }
        return delegate.include(walker);
    }

    @Override
    public boolean shouldBeRecursive() {
        return delegate.shouldBeRecursive();
    }

    @Override
    public TreeFilter clone() {
        return new EntryLimitTreeFilter(delegate.clone(), maxEntries);
    }
}
