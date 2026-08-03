/**
 * Object count for a single Weaviate collection.
 * {@link objectCount} is null when the collection could not be read (see {@link available}).
 */
export interface IndexCollectionCount {
    collection: string;
    objectCount: number | null;
    available: boolean;
}

/**
 * Read-only snapshot of the Weaviate index: reachability plus per-collection object counts.
 */
export interface IndexOverview {
    weaviateUp: boolean;
    weaviateAddress?: string;
    collections: IndexCollectionCount[];
}

/**
 * Index drift for a single entity type within a course: how many rows are indexed in Weaviate
 * ({@link present}) versus how many should be ({@link expected}). {@link expected} is null when it is
 * not computed for that type yet.
 */
export interface TypeDrift {
    type: string;
    present: number;
    expected: number | null;
}

/**
 * Per-type indexed-vs-expected drift for one course, computed live.
 */
export interface CourseIndexDrift {
    courseId: number;
    types: TypeDrift[];
}
