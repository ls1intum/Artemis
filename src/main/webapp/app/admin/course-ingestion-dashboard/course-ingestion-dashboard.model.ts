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
