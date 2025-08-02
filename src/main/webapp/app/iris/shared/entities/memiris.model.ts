export class MemirisLearning {
    id: string;
    title: string;
    content: string;
    reference: string;
    memories: string[]; // Array of memory IDs associated with this learning

    constructor(id: string, title: string, content: string, reference: string, memories: string[]) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.reference = reference;
        this.memories = memories;
    }
}

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

export enum MemirisConnectionType {
    RELATED = 'related',
    CONTRADICTS = 'contradicts',
    SAME_TOPIC = 'same_topic',
    DUPLICATE = 'duplicate',
    CREATED_FROM = 'created_from',
}

export class MemirisMemoryConnection {
    id: string;
    connection_type: MemirisConnectionType;
    memories: string[]; // Array of memory objects associated with this connection
    description: string;
    weight: number;

    constructor(id: string, connection_type: MemirisConnectionType, memories: string[], description: string, weight: number) {
        this.id = id;
        this.connection_type = connection_type;
        this.memories = memories;
        this.description = description;
        this.weight = weight;
    }
}
