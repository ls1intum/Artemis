import { TestBed } from '@angular/core/testing';
import { firstValueFrom, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { AgentChatService } from './agent-chat.service';
import { CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';
import { CourseCompetencyService } from 'app/atlas/shared/services/course-competency.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { CompetencyService } from 'app/atlas/manage/services/competency.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { AlertService } from 'app/shared/service/alert.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { MockProvider } from 'ng-mocks';

describe('AgentChatService', () => {
    let service: AgentChatService;
    let courseManagementService: jest.Mocked<CourseManagementService>;
    let competencyService: jest.Mocked<CompetencyService>;

    beforeEach(() => {
        const mockCourseCompetencyService = {
            generateCompetenciesFromCourseDescription: jest.fn().mockReturnValue(of({})),
            getAllForCourse: jest.fn().mockReturnValue(of({ body: [] })),
        };

        const mockCourseManagementService = {
            find: jest.fn().mockReturnValue(of(new HttpResponse({ body: { description: 'Test course description' } as Course }))),
        };

        const mockCompetencyService = {
            createBulk: jest.fn().mockReturnValue(of({})),
        };

        const mockWebsocketService = {
            subscribe: jest.fn(),
            receive: jest.fn().mockReturnValue(of({ result: [] })),
            unsubscribe: jest.fn(),
        };

        TestBed.configureTestingModule({
            providers: [
                AgentChatService,
                MockProvider(CourseCompetencyService, mockCourseCompetencyService),
                MockProvider(CourseManagementService, mockCourseManagementService),
                MockProvider(CompetencyService, mockCompetencyService),
                MockProvider(WebsocketService, mockWebsocketService),
                MockProvider(AlertService),
            ],
        });

        service = TestBed.inject(AgentChatService);
        courseManagementService = TestBed.inject(CourseManagementService) as jest.Mocked<CourseManagementService>;
        competencyService = TestBed.inject(CompetencyService) as jest.Mocked<CompetencyService>;
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
            await firstValueFrom(service.sendMessage('create competencies for programming', 123));
            // Now confirm creation
            const response = await firstValueFrom(service.sendMessage('yes, create them', 123));
            expect(response).toContain('Successfully created');
            expect(response).toContain('competencies for your course');
            expect(competencyService.createBulk).toHaveBeenCalled();
        });

        it('should handle competency-related requests', async () => {
            const response = await firstValueFrom(service.sendMessage('Help me create competencies for programming', 123));
            expect(response).toContain('Programming Fundamentals');
            expect(response).toContain('Should I create these competencies');
        });

        it('should handle algorithm-related requests', async () => {
            const response = await firstValueFrom(service.sendMessage('I need competencies for sorting algorithms', 123));
            expect(response).toContain('Algorithm Design');
        });

        it('should handle database-related requests', async () => {
            const response = await firstValueFrom(service.sendMessage('Create competencies for database management', 123));
            expect(response).toContain('Database Management');
        });

        it('should handle web development requests', async () => {
            const response = await firstValueFrom(service.sendMessage('I need web development competencies', 123));
            expect(response).toContain('Web Development');
        });

        it('should handle multiple topic requests', async () => {
            const response = await firstValueFrom(service.sendMessage('Create competencies for programming and algorithms', 123));
            expect(response).toContain('Programming Fundamentals');
            expect(response).toContain('Algorithm Design');
        });

        it('should generate generic competencies for unknown topics', async () => {
            const response = await firstValueFrom(service.sendMessage('Help me with quantum computing fundamentals', 123));
            expect(response).toContain('Quantum Computing');
        });

        it('should provide competency prompt for unclear requests', async () => {
            const response = await firstValueFrom(service.sendMessage('help', 123));
            expect(response).toContain('competencies');
            expect(response).toContain('help');
        });

        it('should handle general chat requests', async () => {
            const response = await firstValueFrom(service.sendMessage('How can you help me?', 123));
            expect(response).toContain('competencies');
        });

        it('should handle sorting/algorithm specific requests', async () => {
            const response = await firstValueFrom(service.sendMessage('Tell me about sorting algorithms', 123));
            expect(response).toContain('sorting algorithms');
            expect(response).toContain('Algorithm Analysis');
        });

        it('should handle Java programming requests', async () => {
            const response = await firstValueFrom(service.sendMessage('I need help with Java programming', 123));
            expect(response).toContain('Java programming');
            expect(response).toContain('Object-Oriented Programming');
        });

        it('should handle database/SQL requests', async () => {
            const response = await firstValueFrom(service.sendMessage('Help with database and SQL', 123));
            expect(response).toContain('database');
            expect(response).toContain('SQL Query Writing');
        });

        it('should handle web/frontend/React requests', async () => {
            const response = await firstValueFrom(service.sendMessage('I need help with React frontend', 123));
            expect(response).toContain('web development');
            expect(response).toContain('React framework');
        });

        it('should not create competencies without confirmation', async () => {
            const response = await firstValueFrom(service.sendMessage('maybe later', 123));
            expect(response).not.toContain('Successfully created');
        });

        it('should handle course description generation requests', async () => {
            const response = await firstValueFrom(service.sendMessage('Generate competencies from course description', 123));
            expect(response).toContain('Analyzing course description');
            expect(courseManagementService.find).toHaveBeenCalledWith(123);
        });

        it('should handle course description generation when no description exists', async () => {
            courseManagementService.find.mockReturnValue(of(new HttpResponse({ body: { description: '' } as Course })));
            const response = await firstValueFrom(service.sendMessage('Generate competencies from course description', 123));
            expect(response).toContain("couldn't find a course description");
        });

        it('should identify course description requests correctly', () => {
            const isGenerateFromDescriptionMethod = (service as any).isGenerateFromDescriptionRequest;

            expect(isGenerateFromDescriptionMethod('Generate competencies from course description')).toBeTruthy();
            expect(isGenerateFromDescriptionMethod('based on course content')).toBeTruthy();
            expect(isGenerateFromDescriptionMethod('create competencies for programming')).toBeFalsy();
        });

        it('should handle course description display requests', async () => {
            const response = await firstValueFrom(service.sendMessage('What is the course description?', 123));
            expect(response).toContain('Course Description');
            expect(response).toContain('Test course description');
            expect(response).toContain('Would you like me to generate competencies');
            expect(courseManagementService.find).toHaveBeenCalledWith(123);
        });

        it('should handle course description requests when no description exists', async () => {
            courseManagementService.find.mockReturnValue(of(new HttpResponse({ body: { description: '' } as Course })));
            const response = await firstValueFrom(service.sendMessage('Show course description', 123));
            expect(response).toContain("doesn't have a description set up yet");
            expect(response).toContain('course settings');
        });

        it('should identify course description display requests correctly', () => {
            const isCourseDescriptionRequestMethod = (service as any).isCourseDescriptionRequest;

            expect(isCourseDescriptionRequestMethod('show course description')).toBeTruthy();
            expect(isCourseDescriptionRequestMethod('what is the course description')).toBeTruthy();
            expect(isCourseDescriptionRequestMethod('tell me about the course')).toBeTruthy();
            expect(isCourseDescriptionRequestMethod('create competencies')).toBeFalsy();
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

            const response = await firstValueFrom(createPendingMethod(123));
            expect(response).toBe('No competencies to create.');
        });

        it('should get competency prompt', () => {
            const getCompetencyPromptMethod = (service as any).getCompetencyPrompt;

            const prompt = getCompetencyPromptMethod();
            expect(prompt).toContain("I'd love to help you create competencies");
            expect(prompt).toContain('Auto-generate from course description');
            expect(prompt).toContain('Generate competencies from course description');
        });
    });

    describe('error handling', () => {
        it('should handle empty messages gracefully', async () => {
            const response = await firstValueFrom(service.sendMessage('', 123));
            expect(response).toBeTruthy();
        });

        it('should handle very short messages', async () => {
            const response = await firstValueFrom(service.sendMessage('hi', 123));
            expect(response).toBeTruthy();
        });
    });

    describe('competency workflow', () => {
        it('should complete full competency creation workflow', async () => {
            // Step 1: Request competencies
            const response = await firstValueFrom(service.sendMessage('Create competencies for Java programming', 123));
            expect(response).toContain('Programming Fundamentals');

            // Step 2: Confirm creation
            const confirmResponse = await firstValueFrom(service.sendMessage('yes create them', 123));
            expect(confirmResponse).toContain('Successfully created');
            expect(competencyService.createBulk).toHaveBeenCalled();

            // Step 3: Try to confirm again (should have no pending)
            const emptyResponse = await firstValueFrom(service.sendMessage('yes', 123));
            expect(emptyResponse).toBeTruthy();
        }, 10000);
    });
});
