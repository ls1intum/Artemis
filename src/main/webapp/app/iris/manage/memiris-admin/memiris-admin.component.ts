import { Component, OnInit, signal } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MemirisGraphViewComponent } from 'app/iris/shared/memiris-graph-view/memiris-graph-view.component';
import { MemirisConnectionType, MemirisGraphData, MemirisGraphSettings, MemirisLearning, MemirisMemory, MemirisMemoryConnection } from 'app/iris/shared/entities/memiris.model';
import { v4 as uuid } from 'uuid';
import { MemirisGraphSettingsComponent } from 'app/iris/shared/memiris-graph-settings/memiris-graph-settings.component';

@Component({
    selector: 'jhi-memiris-admin',
    imports: [TranslateDirective, MemirisGraphViewComponent, MemirisGraphSettingsComponent],
    templateUrl: './memiris-admin.component.html',
    styleUrl: './memiris-admin.component.scss',
})
export class MemirisAdminComponent implements OnInit {
    graphData?: MemirisGraphData;
    readonly settings = signal(new MemirisGraphSettings());

    ngOnInit(): void {
        setTimeout(() => {
            this.graphData = new MemirisGraphData([], [], []);
        }, 1000);

        // Initialize the graph data with mock data after 5 seconds
        setTimeout(() => {
            const memories = [];

            const memoryCount = Math.floor(Math.random() * 20) + 1;
            for (let i = 0; i < memoryCount; i++) {
                const memory = this.mockMemory(`Memory ${i + 1}`);
                memories.push(memory);
            }

            const learnings: MemirisLearning[] = [];

            memories.forEach((memory) => {
                const amount = Math.floor(Math.random() * 10) + 1;
                for (let i = 0; i < amount; i++) {
                    const learning = this.mockLearning(`Learning ${memory.title} ${i + 1}`);
                    learnings.push(learning);
                    memory.learnings.push(learning.id);
                }
            });

            const deletedMemories = memories.filter((memory) => memory.deleted);
            const nonDeletedMemories = memories.filter((memory) => !memory.deleted);

            const connections: MemirisMemoryConnection[] = [];

            if (nonDeletedMemories.length > 0 && deletedMemories.length > 0) {
                const createdFromConnection = new MemirisMemoryConnection(
                    uuid(),
                    MemirisConnectionType.CREATED_FROM,
                    [nonDeletedMemories[0], ...deletedMemories],
                    'This memory was created from the following memories.',
                    1,
                );
                connections.push(createdFromConnection);
                for (const memory of deletedMemories) {
                    memory.connections.push(createdFromConnection.id);
                }
            }

            Object.values(MemirisConnectionType).forEach((connectionType) => {
                if (connectionType === MemirisConnectionType.CREATED_FROM) {
                    return; // Skip CREATED_FROM connections as they are handled separately
                }

                const amount = Math.floor(Math.random() * 3) + 1;
                for (let i = 0; i < amount; i++) {
                    const memory1 = nonDeletedMemories[Math.floor(Math.random() * nonDeletedMemories.length)];
                    const memory2 = nonDeletedMemories[Math.floor(Math.random() * nonDeletedMemories.length)];

                    // Ensure the two memories don't already share a connection
                    if (memory1.id === memory2.id || connections.some((connection) => connection.memories.includes(memory1) && connection.memories.includes(memory2))) {
                        continue;
                    }

                    const connection = new MemirisMemoryConnection(
                        uuid(),
                        connectionType,
                        [memory1, memory2],
                        `This is a ${connectionType} connection between ${memory1.title} and ${memory2.title}.`,
                        Math.random(),
                    );
                    connections.push(connection);
                    memory1.connections.push(connection.id);
                    memory2.connections.push(connection.id);
                }
            });

            this.graphData = new MemirisGraphData(memories, learnings, connections);
        }, 2000);
    }

    private mockLearning(title: string): MemirisLearning {
        return new MemirisLearning(uuid(), title, 'This is a mock learning description.', 'Random reference', []);
    }

    private mockMemory(title: string): MemirisMemory {
        return new MemirisMemory(uuid(), title, 'This is a mock memory description.', [], [], Math.random() > 0.6, Math.random() > 0.8);
    }
}
