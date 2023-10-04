export interface SearchResultDTO {
    project: ProjectDTO;
    file: MetadataFileDTO;
    metadata: UserProvidedMetadataDTO;
    ranking5: number;
    supportedActions: PluginActionInfo[];
    views: number;
    downloads: number;
}

export interface PluginActionInfo {
    plugin: string;
    action: string;
    commandName: string;
}

export interface UserProvidedMetadataDTO {
    contributor: Array<Person>;
    creator: Array<Person>;
    deprecated: boolean;
    description: string;
    difficulty: string;
    educationLevel: string;
    format: Array<string>;
    identifier: string;
    image: string;
    keyword: Array<string>;
    language: Array<string>;
    license: string;
    metadataVersion: string;
    programmingLanguage: Array<string>;
    collectionContent: Array<string>;
    publisher: Array<Person>;
    requires: Array<string>;
    source: Array<string>;
    status: string;
    structure: string;
    timeRequired: string;
    title: string;
    type: IExerciseType;
    version: string;
}

export interface Person {
    name: string;
    email: string;
    affiliation: string;
}

export enum IExerciseType {
    COLLECTION = 'collection',
    PROGRAMMING_EXERCISE = 'programming exercise',
    EXERCISE = 'exercise',
    OTHER = 'other',
}

export interface ProjectDTO {
    project_id: string;
    project_name: string;
    namespace: string;
    main_group: string;
    sub_group: string;
    url: string;
    last_activity_at: Date;
}

export interface MetadataFileDTO {
    filename: string;
    path: string;
    commit_id: string;
    indexing_date: Date;
}
