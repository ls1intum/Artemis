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
