import { InlineComment, SerializedInlineComment, createInlineComment, deserializeComment, serializeComment } from './inline-comment.model';

describe('inline-comment.model', () => {
    describe('createInlineComment', () => {
        it('should create comment with default values and unique IDs', () => {
            const comment = createInlineComment(1, 5, 'Test instruction');

            expect(comment.id).toBeDefined();
            expect(comment.id.length).toBeGreaterThan(0);
            expect(comment.startLine).toBe(1);
            expect(comment.endLine).toBe(5);
            expect(comment.instruction).toBe('Test instruction');
            expect(comment.status).toBe('draft');
            expect(comment.createdAt).toBeInstanceOf(Date);

            // Unique IDs
            const comment2 = createInlineComment(1, 5, 'Test 2');
            expect(comment.id).not.toBe(comment2.id);

            // Single line
            const singleLine = createInlineComment(5, 5, 'Single line');
            expect(singleLine.startLine).toBe(singleLine.endLine);
        });
    });

    describe('serializeComment', () => {
        it('should serialize comment with ISO date string', () => {
            const now = new Date('2024-01-01T00:00:00Z');
            const comment: InlineComment = {
                id: 'test-id-123',
                startLine: 1,
                endLine: 5,
                instruction: 'Test instruction',
                status: 'pending',
                createdAt: now,
            };

            const serialized = serializeComment(comment);

            expect(serialized.id).toBe('test-id-123');
            expect(serialized.startLine).toBe(1);
            expect(serialized.endLine).toBe(5);
            expect(serialized.instruction).toBe('Test instruction');
            expect(serialized.status).toBe('pending');
            expect(serialized.createdAt).toBe(now.toISOString());
        });
    });

    describe('deserializeComment', () => {
        it('should deserialize comment and handle all status types', () => {
            const serialized: SerializedInlineComment = {
                id: 'test-id-123',
                startLine: 1,
                endLine: 5,
                instruction: 'Test instruction',
                status: 'applying',
                createdAt: '2024-01-01T00:00:00.000Z',
            };

            const deserialized = deserializeComment(serialized);

            expect(deserialized.id).toBe('test-id-123');
            expect(deserialized.startLine).toBe(1);
            expect(deserialized.endLine).toBe(5);
            expect(deserialized.instruction).toBe('Test instruction');
            expect(deserialized.status).toBe('applying');
            expect(deserialized.createdAt).toBeInstanceOf(Date);
            expect(deserialized.createdAt.toISOString()).toBe('2024-01-01T00:00:00.000Z');

            // All status types
            const statuses = ['draft', 'pending', 'applying', 'applied', 'error'] as const;
            for (const status of statuses) {
                const s: SerializedInlineComment = { ...serialized, status };
                expect(deserializeComment(s).status).toBe(status);
            }
        });
    });

    describe('roundtrip', () => {
        it('should preserve all data through serialization roundtrip', () => {
            const original = createInlineComment(10, 20, 'Complex instruction <>');
            original.status = 'error';

            const deserialized = deserializeComment(serializeComment(original));

            expect(deserialized.id).toBe(original.id);
            expect(deserialized.startLine).toBe(original.startLine);
            expect(deserialized.endLine).toBe(original.endLine);
            expect(deserialized.instruction).toBe(original.instruction);
            expect(deserialized.status).toBe(original.status);
            expect(deserialized.createdAt.getTime()).toBe(original.createdAt.getTime());
        });
    });
});
