export class MemirisMemory {
    id: string;
    title: string;
    content: string;
    learnings: string[]; // Array of learning IDs associated with this memory
    connections: string[]; // Array of memory connection IDs of this memory
    slept_on: boolean;
    deleted: boolean;

    constructor(id: string, title: string, content: string, learnings: string[], connections: string[], slept_on: boolean, deleted: boolean) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.learnings = learnings;
        this.connections = connections;
        this.slept_on = slept_on;
        this.deleted = deleted;
    }
}

export interface MemirisLearningDTO {
    id: string;
    title: string;
    content: string;
    reference?: string;
    memories: string[];
}

export interface MemirisMemoryConnectionDTO {
    id: string;
    connectionType: string;
    memories: string[]; // Related memory IDs
    description?: string;
    weight?: number;
}

export interface MemirisMemoryWithRelationsDTO {
    id: string;
    title: string;
    content: string;
    sleptOn: boolean;
    deleted: boolean;
    learnings: MemirisLearningDTO[];
    connections: MemirisMemoryConnectionDTO[];
}
