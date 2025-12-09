import { PlagiarismComparison } from './PlagiarismComparison';
import { PlagiarismStatus } from './PlagiarismStatus';
import { PlagiarismSubmission } from './PlagiarismSubmission';
import { PlagiarismResult } from './PlagiarismResult';
import { PlagiarismMatch } from './PlagiarismMatch';

describe('PlagiarismComparison', () => {
    let plagiarismComparison: PlagiarismComparison;
    let mockSubmissionA: PlagiarismSubmission;
    let mockSubmissionB: PlagiarismSubmission;
    let mockPlagiarismResult: PlagiarismResult;
    let mockMatches: PlagiarismMatch[];

    beforeEach(() => {
        mockSubmissionA = {
            id: 1,
            studentLogin: 'student1',
            submissionId: 101,
            size: 150,
            score: 85,
        } as PlagiarismSubmission;

        mockSubmissionB = {
            id: 2,
            studentLogin: 'student2',
            submissionId: 102,
            size: 200,
            score: 90,
        } as PlagiarismSubmission;

        mockPlagiarismResult = {
            id: 100,
            comparisons: [],
            duration: 1000,
            exercise: {} as any,
            similarityDistribution: new Array(10).fill(0) as [number, 10],
            createdDate: {} as any,
        } as PlagiarismResult;

        mockMatches = [
            {
                startA: 0,
                startB: 5,
                length: 10,
            } as PlagiarismMatch,
            {
                startA: 20,
                startB: 25,
                length: 15,
            } as PlagiarismMatch,
        ];

        plagiarismComparison = new PlagiarismComparison();
    });

    describe('Complete comparison scenarios', () => {
        it('should represent a high similarity confirmed plagiarism case', () => {
            plagiarismComparison.id = 1;
            plagiarismComparison.submissionA = mockSubmissionA;
            plagiarismComparison.submissionB = mockSubmissionB;
            plagiarismComparison.similarity = 95.7;
            plagiarismComparison.status = PlagiarismStatus.CONFIRMED;
            plagiarismComparison.matches = mockMatches;
            plagiarismComparison.plagiarismResult = mockPlagiarismResult;

            expect(plagiarismComparison.similarity).toBeGreaterThan(90);
            expect(plagiarismComparison.status).toBe(PlagiarismStatus.CONFIRMED);
            expect(plagiarismComparison.matches).toHaveLength(2);
        });

        it('should represent a low similarity denied case', () => {
            plagiarismComparison.id = 2;
            plagiarismComparison.submissionA = mockSubmissionA;
            plagiarismComparison.submissionB = mockSubmissionB;
            plagiarismComparison.similarity = 25.3;
            plagiarismComparison.status = PlagiarismStatus.DENIED;
            plagiarismComparison.matches = [];

            expect(plagiarismComparison.similarity).toBeLessThan(50);
            expect(plagiarismComparison.status).toBe(PlagiarismStatus.DENIED);
            expect(plagiarismComparison.matches).toHaveLength(0);
        });

        it('should represent an unreviewed case with moderate similarity', () => {
            plagiarismComparison.id = 3;
            plagiarismComparison.submissionA = mockSubmissionA;
            plagiarismComparison.submissionB = mockSubmissionB;
            plagiarismComparison.similarity = 67.2;
            plagiarismComparison.status = PlagiarismStatus.NONE;
            plagiarismComparison.matches = [mockMatches[0]];

            expect(plagiarismComparison.similarity).toBeGreaterThan(50);
            expect(plagiarismComparison.similarity).toBeLessThan(80);
            expect(plagiarismComparison.status).toBe(PlagiarismStatus.NONE);
            expect(plagiarismComparison.matches).toHaveLength(1);
        });
    });

    describe('Edge cases and validation', () => {
        it('should handle comparisons with identical submissions', () => {
            const identicalSubmission = Object.assign({}, mockSubmissionA);
            plagiarismComparison.submissionA = mockSubmissionA;
            plagiarismComparison.submissionB = identicalSubmission;
            plagiarismComparison.similarity = 100;

            expect(plagiarismComparison.submissionA.studentLogin).toBe(plagiarismComparison.submissionB.studentLogin);
            expect(plagiarismComparison.similarity).toBe(100);
        });

        it('should handle very small similarity values', () => {
            plagiarismComparison.similarity = 0.001;
            expect(plagiarismComparison.similarity).toBe(0.001);
        });

        it('should handle comparisons without matches but with similarity', () => {
            plagiarismComparison.submissionA = mockSubmissionA;
            plagiarismComparison.submissionB = mockSubmissionB;
            plagiarismComparison.similarity = 45;
            plagiarismComparison.matches = [];
            plagiarismComparison.status = PlagiarismStatus.NONE;

            expect(plagiarismComparison.similarity).toBeGreaterThan(0);
            expect(plagiarismComparison.matches).toHaveLength(0);
        });
    });
});
