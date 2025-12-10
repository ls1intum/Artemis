import { InlineComment, SerializedInlineComment, createInlineComment, deserializeComment, serializeComment } from './inline-comment.model';

describe('inline-comment.model', () => {
    describe('createInlineComment', () => {
        it('should create a new inline comment with default values', () => {
            const comment = createInlineComment(1, 5, 'Test instruction');

            expect(comment.id).toBeDefined();
            expect(comment.id.length).toBeGreaterThan(0);
            expect(comment.startLine).toBe(1);
            expect(comment.endLine).toBe(5);
            expect(comment.instruction).toBe('Test instruction');
            expect(comment.status).toBe('draft');
            expect(comment.createdAt).toBeInstanceOf(Date);
        });

        it('should create unique IDs for each comment', () => {
            const comment1 = createInlineComment(1, 5, 'Test 1');
            const comment2 = createInlineComment(1, 5, 'Test 2');

            expect(comment1.id).not.toBe(comment2.id);
        });

        it('should handle single line comments', () => {
            const comment = createInlineComment(5, 5, 'Single line comment');

            expect(comment.startLine).toBe(5);
            expect(comment.endLine).toBe(5);
        });
    });

    describe('serializeComment', () => {
        it('should serialize a comment to a storage-friendly format', () => {
            const comment: InlineComment = {
                id: 'test-id-123',
                startLine: 1,
                endLine: 5,
                instruction: 'Test instruction',
                status: 'pending',
                createdAt: new Date('2024-01-01T00:00:00Z'),
            };

            const serialized = serializeComment(comment);

            expect(serialized.id).toBe('test-id-123');
            expect(serialized.startLine).toBe(1);
            expect(serialized.endLine).toBe(5);
            expect(serialized.instruction).toBe('Test instruction');
            expect(serialized.status).toBe('pending');
            expect(serialized.createdAt).toBe('2024-01-01T00:00:00.000Z');
        });

        it('should convert createdAt Date to ISO string', () => {
            const now = new Date();
            const comment = createInlineComment(1, 5, 'Test');
            comment.createdAt = now;

            const serialized = serializeComment(comment);

            expect(serialized.createdAt).toBe(now.toISOString());
        });
    });

    describe('deserializeComment', () => {
        it('should deserialize a comment from storage format', () => {
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
        });

        it('should handle all status types', () => {
            const statuses = ['draft', 'pending', 'applying', 'applied', 'error'] as const;

            for (const status of statuses) {
                const serialized: SerializedInlineComment = {
                    id: 'test-id',
                    startLine: 1,
                    endLine: 5,
                    instruction: 'Test',
                    status,
                    createdAt: '2024-01-01T00:00:00.000Z',
                };

                const deserialized = deserializeComment(serialized);
                expect(deserialized.status).toBe(status);
            }
        });
    });

    describe('serialize and deserialize roundtrip', () => {
        it('should preserve all data through serialization roundtrip', () => {
            const original = createInlineComment(10, 20, 'Complex instruction with special chars: <>&"\'');
            original.status = 'error';

            const serialized = serializeComment(original);
            const deserialized = deserializeComment(serialized);

            expect(deserialized.id).toBe(original.id);
            expect(deserialized.startLine).toBe(original.startLine);
            expect(deserialized.endLine).toBe(original.endLine);
            expect(deserialized.instruction).toBe(original.instruction);
            expect(deserialized.status).toBe(original.status);
            expect(deserialized.createdAt.getTime()).toBe(original.createdAt.getTime());
        });
    });
});
