import { TestBed } from '@angular/core/testing';
import { AgentChatService } from './agent-chat.service';
import { CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';

describe('AgentChatService', () => {
    let service: AgentChatService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [AgentChatService],
        });
        service = TestBed.inject(AgentChatService);
    });

    afterEach(() => {
        // Clear pending competencies
        (service as any).pendingCompetencies = [];
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    describe('sendMessage', () => {
        it('should handle competency creation confirmation', async () => {
            // First, create some pending competencies
            await service.sendMessage('create competencies for programming', 123).toPromise();
            // Now confirm creation
            const response = await service.sendMessage('yes, create them', 123).toPromise();
            expect(response).toContain('Mock: Created');
            expect(response).toContain('competencies for your course');
        });

        it('should handle competency-related requests', async () => {
            const response = await service.sendMessage('Help me create competencies for programming', 123).toPromise();
            expect(response).toContain('Programming Fundamentals');
            expect(response).toContain('Should I create these competencies');
        });

        it('should handle algorithm-related requests', async () => {
            const response = await service.sendMessage('I need competencies for sorting algorithms', 123).toPromise();
            expect(response).toContain('Algorithm Design');
        });

        it('should handle database-related requests', async () => {
            const response = await service.sendMessage('Create competencies for database management', 123).toPromise();
            expect(response).toContain('Database Management');
        });

        it('should handle web development requests', async () => {
            const response = await service.sendMessage('I need web development competencies', 123).toPromise();
            expect(response).toContain('Web Development');
        });

        it('should handle multiple topic requests', async () => {
            const response = await service.sendMessage('Create competencies for programming and algorithms', 123).toPromise();
            expect(response).toContain('Programming Fundamentals');
            expect(response).toContain('Algorithm Design');
        });

        it('should generate generic competencies for unknown topics', async () => {
            const response = await service.sendMessage('Help me with quantum computing fundamentals', 123).toPromise();
            expect(response).toContain('Quantum Computing');
        });

        it('should provide competency prompt for unclear requests', async () => {
            const response = await service.sendMessage('help', 123).toPromise();
            expect(response).toContain('competencies');
            expect(response).toContain('help');
        });

        it('should handle general chat requests', async () => {
            const response = await service.sendMessage('How can you help me?', 123).toPromise();
            expect(response).toContain('competencies');
        });

        it('should handle sorting/algorithm specific requests', async () => {
            const response = await service.sendMessage('Tell me about sorting algorithms', 123).toPromise();
            expect(response).toContain('sorting algorithms');
            expect(response).toContain('Algorithm Analysis');
        });

        it('should handle Java programming requests', async () => {
            const response = await service.sendMessage('I need help with Java programming', 123).toPromise();
            expect(response).toContain('Java programming');
            expect(response).toContain('Object-Oriented Programming');
        });

        it('should handle database/SQL requests', async () => {
            const response = await service.sendMessage('Help with database and SQL', 123).toPromise();
            expect(response).toContain('database');
            expect(response).toContain('SQL Query Writing');
        });

        it('should handle web/frontend/React requests', async () => {
            const response = await service.sendMessage('I need help with React frontend', 123).toPromise();
            expect(response).toContain('web development');
            expect(response).toContain('React framework');
        });

        it('should not create competencies without confirmation', async () => {
            const response = await service.sendMessage('maybe later', 123).toPromise();
            expect(response).not.toContain('Mock: Created');
        });
    });

    describe('private methods', () => {
        it('should identify competency requests correctly', () => {
            const isCompetencyRequestMethod = (service as any).isCompetencyRequest;

            expect(isCompetencyRequestMethod('create competencies')).toBeTruthy();
            expect(isCompetencyRequestMethod('learning objectives')).toBeTruthy();
            expect(isCompetencyRequestMethod('help me with course')).toBeTruthy();
            expect(isCompetencyRequestMethod('random chat')).toBeFalsy();
        });

        it('should extract topics correctly', () => {
            const extractTopicsMethod = (service as any).extractTopics;

            const programmingTopics = extractTopicsMethod('programming and coding');
            expect(programmingTopics).toHaveLength(1);
            expect(programmingTopics[0].title).toBe('Programming Fundamentals');

            const multipleTopics = extractTopicsMethod('programming algorithms database web');
            expect(multipleTopics.length).toBeGreaterThan(1);
        });

        it('should generate titles from messages', () => {
            const generateTitleMethod = (service as any).generateTitleFromMessage;

            const title = generateTitleMethod('machine learning fundamentals');
            expect(title).toBe('Machine Learning Fundamentals');

            const shortTitle = generateTitleMethod('ai');
            expect(shortTitle).toBe('Course Competency');
        });

        it('should get correct taxonomy labels', () => {
            const getTaxonomyLabelMethod = (service as any).getTaxonomyLabel;

            expect(getTaxonomyLabelMethod(CompetencyTaxonomy.REMEMBER)).toBe('Remember');
            expect(getTaxonomyLabelMethod(CompetencyTaxonomy.UNDERSTAND)).toBe('Understand');
            expect(getTaxonomyLabelMethod(CompetencyTaxonomy.APPLY)).toBe('Apply');
            expect(getTaxonomyLabelMethod(CompetencyTaxonomy.ANALYZE)).toBe('Analyze');
            expect(getTaxonomyLabelMethod(CompetencyTaxonomy.EVALUATE)).toBe('Evaluate');
            expect(getTaxonomyLabelMethod(CompetencyTaxonomy.CREATE)).toBe('Create');
        });

        it('should handle empty pending competencies', async () => {
            const createPendingMethod = (service as any).createPendingCompetencies.bind(service);

            const response = await createPendingMethod(123).toPromise();
            expect(response).toBe('No competencies to create.');
        });

        it('should get competency prompt', () => {
            const getCompetencyPromptMethod = (service as any).getCompetencyPrompt;

            const prompt = getCompetencyPromptMethod();
            expect(prompt).toContain("I'd love to help you create competencies");
            expect(prompt).toContain('What topic or subject');
        });
    });

    describe('error handling', () => {
        it('should handle empty messages gracefully', async () => {
            const response = await service.sendMessage('', 123).toPromise();
            expect(response).toBeTruthy();
        });

        it('should handle very short messages', async () => {
            const response = await service.sendMessage('hi', 123).toPromise();
            expect(response).toBeTruthy();
        });
    });

    describe('competency workflow', () => {
        it('should complete full competency creation workflow', async () => {
            // Step 1: Request competencies
            const response = await service.sendMessage('Create competencies for Java programming', 123).toPromise();
            expect(response).toContain('Programming Fundamentals');

            // Step 2: Confirm creation
            const confirmResponse = await service.sendMessage('yes create them', 123).toPromise();
            expect(confirmResponse).toContain('Mock: Created');

            // Step 3: Try to confirm again (should have no pending)
            const emptyResponse = await service.sendMessage('yes', 123).toPromise();
            expect(emptyResponse).toBeTruthy();
        }, 10000);
    });
});
