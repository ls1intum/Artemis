import { TestBed } from '@angular/core/testing';
import { Observable, firstValueFrom, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { AgentChatService } from './agent-chat.service';
import { CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { CompetencyService } from 'app/atlas/manage/services/competency.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MockProvider } from 'ng-mocks';

describe('AgentChatService', () => {
    let service: AgentChatService;
    let courseManagementService: jest.Mocked<CourseManagementService>;
    let competencyService: jest.Mocked<CompetencyService>;

    beforeEach(() => {
        const mockCourseManagementService = {
            find: jest.fn().mockReturnValue(of(new HttpResponse({ body: { description: 'Test course description' } as Course }))),
            findWithExercises: jest.fn().mockReturnValue(
                of(
                    new HttpResponse({
                        body: {
                            exercises: [
                                { title: 'Programming Assignment 1', type: 'programming', dueDate: '2025-09-15' } as unknown as Exercise,
                                { title: 'Quiz 1', type: 'quiz', dueDate: '2025-09-10' } as unknown as Exercise,
                            ],
                        } as Course,
                    }),
                ),
            ),
        };

        const mockCompetencyService = {
            create: jest.fn().mockReturnValue(of(new HttpResponse({}))),
        };

        TestBed.configureTestingModule({
            providers: [AgentChatService, MockProvider(CourseManagementService, mockCourseManagementService), MockProvider(CompetencyService, mockCompetencyService)],
        });

        service = TestBed.inject(AgentChatService);
        courseManagementService = TestBed.inject(CourseManagementService) as jest.Mocked<CourseManagementService>;
        competencyService = TestBed.inject(CompetencyService) as jest.Mocked<CompetencyService>;
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    describe('sendMessage', () => {
        it('should handle course description requests', async () => {
            const response = await firstValueFrom(service.sendMessage('What is the course description?', 123));
            expect(response).toContain('Course Description');
            expect(response).toContain('Test course description');
            expect(courseManagementService.find).toHaveBeenCalledWith(123);
        });

        it('should handle course description requests when no description exists', async () => {
            courseManagementService.find.mockReturnValue(of(new HttpResponse({ body: { description: '' } as Course })));
            const response = await firstValueFrom(service.sendMessage('Show course description', 123));
            expect(response).toContain("doesn't have a description set up yet");
            expect(response).toContain('course settings');
        });

        it('should handle course exercises requests', async () => {
            const response = await firstValueFrom(service.sendMessage('Show me the exercises', 123));
            expect(response).toContain('Programming Assignment 1');
            expect(response).toContain('Quiz 1');
            expect(response).toContain('2 total');
            expect(courseManagementService.findWithExercises).toHaveBeenCalledWith(123);
        });

        it('should handle course exercises requests when no exercises exist', async () => {
            courseManagementService.findWithExercises.mockReturnValue(of(new HttpResponse({ body: { exercises: [] } as Course })));
            const response = await firstValueFrom(service.sendMessage('List exercises', 123));
            expect(response).toContain("doesn't have any exercises yet");
            expect(response).toContain('course management interface');
        });

        it('should handle exercise fetch errors', async () => {
            courseManagementService.findWithExercises.mockReturnValue(new Observable((subscriber) => subscriber.error(new Error('Network error'))));

            const response = await firstValueFrom(service.sendMessage('Show exercises', 123));
            expect(response).toContain("couldn't retrieve the course exercises");
        });

        it('should create competency with proper title extraction', async () => {
            const response = await firstValueFrom(service.sendMessage('Create competency for aerodynamics', 123));
            expect(response).toContain('Successfully created competency: "Aerodynamics"');
            expect(competencyService.create).toHaveBeenCalledWith(
                expect.objectContaining({
                    title: 'Aerodynamics',
                    description: 'Competency for Aerodynamics',
                    taxonomy: CompetencyTaxonomy.UNDERSTAND,
                    masteryThreshold: 85,
                    optional: false,
                }),
                123,
            );
        });

        it('should handle competency creation failure', async () => {
            competencyService.create.mockReturnValue(new Observable((subscriber) => subscriber.error(new Error('Creation failed'))));

            const response = await firstValueFrom(service.sendMessage('Create competency for physics', 123));
            expect(response).toContain('Error creating competency "Physics"');
        });

        it('should handle invalid competency creation requests', async () => {
            const response = await firstValueFrom(service.sendMessage('create competency', 123));
            expect(response).toContain("couldn't extract a competency title");
        });

        it('should handle general chat requests', async () => {
            const response = await firstValueFrom(service.sendMessage('How can you help me?', 123));
            expect(response).toContain('I can assist you with managing your course');
            expect(response).toContain('Show course description');
            expect(response).toContain('Create competency');
        });

        it('should handle empty messages gracefully', async () => {
            const response = await firstValueFrom(service.sendMessage('', 123));
            expect(response).toBeTruthy();
        });

        it('should handle very short messages', async () => {
            const response = await firstValueFrom(service.sendMessage('hi', 123));
            expect(response).toBeTruthy();
        });
    });

    describe('private methods', () => {
        it('should identify course description requests correctly', () => {
            const isCourseDescriptionRequestMethod = (service as any).isCourseDescriptionRequest;

            expect(isCourseDescriptionRequestMethod('show course description')).toBeTruthy();
            expect(isCourseDescriptionRequestMethod('what is the course description')).toBeTruthy();
            expect(isCourseDescriptionRequestMethod('tell me about the course')).toBeTruthy();
            expect(isCourseDescriptionRequestMethod('create competency')).toBeFalsy();
        });

        it('should identify course exercises requests correctly', () => {
            const isCourseExercisesRequestMethod = (service as any).isCourseExercisesRequest;

            expect(isCourseExercisesRequestMethod('show exercises')).toBeTruthy();
            expect(isCourseExercisesRequestMethod('list exercises')).toBeTruthy();
            expect(isCourseExercisesRequestMethod('course exercises')).toBeTruthy();
            expect(isCourseExercisesRequestMethod('create competency')).toBeFalsy();
        });

        it('should identify competency creation requests correctly', () => {
            const isCompetencyCreationRequestMethod = (service as any).isCompetencyCreationRequest;

            expect(isCompetencyCreationRequestMethod('create competency for math')).toBeTruthy();
            expect(isCompetencyCreationRequestMethod('add competency for physics')).toBeTruthy();
            expect(isCompetencyCreationRequestMethod('new competency for chemistry')).toBeTruthy();
            expect(isCompetencyCreationRequestMethod('show course description')).toBeFalsy();
        });

        it('should extract competency titles correctly', () => {
            const extractTitleMethod = (service as any).extractCompetencyTitle.bind(service);

            expect(extractTitleMethod('create competency for aerodynamics')).toBe('Aerodynamics');
            expect(extractTitleMethod('add competency for machine learning')).toBe('Machine Learning');
            expect(extractTitleMethod('new competency for data structures and algorithms')).toBe('Data Structures And Algorithms');
            expect(extractTitleMethod('create competency')).toBe('');
        });

        it('should capitalize titles correctly', () => {
            const capitalizeTitleMethod = (service as any).capitalizeTitle.bind(service);

            expect(capitalizeTitleMethod('aerodynamics')).toBe('Aerodynamics');
            expect(capitalizeTitleMethod('machine learning')).toBe('Machine Learning');
            expect(capitalizeTitleMethod('DATA structures')).toBe('Data Structures');
        });

        it('should handle course description fetch errors', async () => {
            courseManagementService.find.mockReturnValue(new Observable((subscriber) => subscriber.error(new Error('Network error'))));

            const response = await firstValueFrom(service.sendMessage('What is the course description?', 123));
            expect(response).toContain("This course doesn't have a description set up yet");
        });
    });
});
