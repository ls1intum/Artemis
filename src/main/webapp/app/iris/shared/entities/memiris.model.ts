import { SimulationLinkDatum, SimulationNodeDatum } from 'd3';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

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
    memories: MemirisMemory[]; // Array of memory objects associated with this connection
    description: string;
    weight: number;

    constructor(id: string, connection_type: MemirisConnectionType, memories: MemirisMemory[], description: string, weight: number) {
        this.id = id;
        this.connection_type = connection_type;
        this.memories = memories;
        this.description = description;
        this.weight = weight;
    }
}

export class MemirisGraphData {
    memories: MemirisMemory[] = [];
    learnings: MemirisLearning[] = [];
    connections: MemirisMemoryConnection[] = [];

    constructor(memories: MemirisMemory[], learnings: MemirisLearning[], connections: MemirisMemoryConnection[]) {
        this.memories = memories;
        this.learnings = learnings;
        this.connections = connections;
    }
}

export abstract class MemirisSimulationNode implements SimulationNodeDatum {
    x?: number | undefined;
    y?: number | undefined;
    index?: number | undefined;
    vx?: number | undefined;
    vy?: number | undefined;
    fx?: number | null | undefined;
    fy?: number | null | undefined;

    abstract getId(): string;
    abstract getLabel(): string;
}

export class MemirisMemoryNode extends MemirisSimulationNode {
    memory: MemirisMemory;

    constructor(memory: MemirisMemory) {
        super();
        this.memory = memory;
    }

    getId(): string {
        return this.memory.id;
    }

    getLabel(): string {
        return this.memory.title;
    }
}

export class MemirisLearningNode extends MemirisSimulationNode {
    learning: MemirisLearning;

    constructor(learning: MemirisLearning) {
        super();
        this.learning = learning;
    }

    getId(): string {
        return this.learning.id;
    }

    getLabel(): string {
        return this.learning.title;
    }
}

export abstract class MemirisSimulationLink implements SimulationLinkDatum<MemirisSimulationNode> {
    source: MemirisSimulationNode;
    target: MemirisSimulationNode;
    index?: number;

    abstract getId(): string;
    abstract getLabel(translatePipe: ArtemisTranslatePipe): string;
}

export class MemirisSimulationLinkMemoryMemory extends MemirisSimulationLink {
    connection: MemirisMemoryConnection;

    constructor(connection: MemirisMemoryConnection, source: MemirisMemoryNode, target: MemirisMemoryNode) {
        super();
        this.connection = connection;
        this.source = source;
        this.target = target;
    }

    getId(): string {
        return `${this.connection.id}-${this.source.getId()}-${this.target.getId()}`;
    }

    getLabel(translatePipe: ArtemisTranslatePipe): string {
        return translatePipe.transform(`artemisApp.memiris.connection.${this.connection.connection_type.toLowerCase()}.graphName`, {});
    }
}

export class MemirisSimulationLinkMemoryLearning extends MemirisSimulationLink {
    memory: MemirisMemory;
    learning: MemirisLearning;

    constructor(source: MemirisMemoryNode, target: MemirisLearningNode) {
        super();
        this.memory = source.memory;
        this.learning = target.learning;
        this.source = source;
        this.target = target;
    }

    getId(): string {
        return `${this.memory.id}-${this.learning.id}`;
    }
    getLabel(_translatePipe: ArtemisTranslatePipe): string {
        return '';
    }
}

export class MemirisGraphSettings {
    // Memory settings
    showMemories: boolean = true;
    showMemoryLabels: boolean = true;
    hideDeleted: boolean = true;
    // Learning settings
    showLearnings: boolean = true;
    showLearningLabels: boolean = true;
    // Other settings
    showConnections: boolean = true;
}
