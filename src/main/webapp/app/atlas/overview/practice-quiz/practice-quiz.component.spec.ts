import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { AlertService } from 'app/foundation/service/alert.service';
import { PracticeQuizComponent } from 'app/atlas/overview/practice-quiz/practice-quiz.component';
import { PracticeQuizService } from 'app/atlas/overview/practice-quiz/practice-quiz.service';
import { PracticeSessionDTO, PracticeSessionReviewDTO, PracticeSessionSummaryDTO } from 'app/atlas/overview/practice-quiz/practice-quiz.model';

describe('PracticeQuizComponent', () => {
    let fixture: ComponentFixture<PracticeQuizComponent>;
    let component: PracticeQuizComponent;
    let practiceQuizService: PracticeQuizService;

    const mockSession: PracticeSessionDTO = {
        id: 42,
        courseId: 1,
        startTime: '2026-11-17T12:00:00Z',
        questions: [
            {
                id: 101,
                title: 'Question 1',
                text: 'What is polymorphism?',
                points: 2,
                type: 'multiple-choice',
                singleChoice: false,
                answerOptions: [
                    { id: 201, text: 'Option A' },
                    { id: 202, text: 'Option B' },
                ],
            },
        ],
    };

    const mockReview: PracticeSessionReviewDTO = {
        id: 42,
        courseId: 1,
        startTime: '2026-11-17T12:00:00Z',
        completedAt: '2026-11-17T12:05:00Z',
        score: 2,
        maxPoints: 2,
        questions: [
            {
                question: {
                    id: 101,
                    title: 'Question 1',
                    text: 'What is polymorphism?',
                    points: 2,
                    type: 'multiple-choice',
                    answerOptions: [
                        { id: 201, text: 'Option A', isCorrect: true, explanation: 'Correct because A.' },
                        { id: 202, text: 'Option B', isCorrect: false },
                    ],
                },
                isAdaptive: true,
                score: 2,
            },
        ],
    };

    const mockSummary: PracticeSessionSummaryDTO = {
        id: 42,
        courseId: 1,
        startTime: '2026-11-17T12:00:00Z',
        completedAt: '2026-11-17T12:05:00Z',
        score: 2,
        maxPoints: 2,
        completed: true,
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PracticeQuizComponent, MockPipe(ArtemisDatePipe)],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(AlertService),
                MockProvider(PracticeQuizService),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            paramMap: of(convertToParamMap({ courseId: '1' })),
                        },
                    },
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(PracticeQuizComponent);
        component = fixture.componentInstance;
        practiceQuizService = TestBed.inject(PracticeQuizService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should stay in OVERVIEW when no active session exists', () => {
        vi.spyOn(practiceQuizService, 'getCurrentSession').mockReturnValue(of(null));
        vi.spyOn(practiceQuizService, 'getPastSessions').mockReturnValue(of([mockSummary]));

        component.ngOnInit();

        expect(component.state()).toBe('OVERVIEW');
        expect(component.pastSessions()).toEqual([mockSummary]);
        expect(component.currentSession()).toBeNull();
    });

    it('should resume into QUIZ state when an active session is returned', () => {
        vi.spyOn(practiceQuizService, 'getCurrentSession').mockReturnValue(of(mockSession));

        component.ngOnInit();

        expect(component.state()).toBe('QUIZ');
        expect(component.currentSession()).toEqual(mockSession);
    });

    it('should start a new quiz session on startNewQuiz', () => {
        vi.spyOn(practiceQuizService, 'getCurrentSession').mockReturnValue(of(null));
        vi.spyOn(practiceQuizService, 'getPastSessions').mockReturnValue(of([]));
        vi.spyOn(practiceQuizService, 'startSession').mockReturnValue(of(mockSession));

        component.ngOnInit();
        component.startNewQuiz();

        expect(component.state()).toBe('QUIZ');
        expect(component.currentSession()).toEqual(mockSession);
    });

    it('should toggle options correctly for multiple choice questions', () => {
        component.toggleOption(101, 201, false);
        expect(component.isOptionSelected(101, 201)).toBe(true);

        component.toggleOption(101, 202, false);
        expect(component.isOptionSelected(101, 201)).toBe(true);
        expect(component.isOptionSelected(101, 202)).toBe(true);

        component.toggleOption(101, 201, false);
        expect(component.isOptionSelected(101, 201)).toBe(false);
        expect(component.isOptionSelected(101, 202)).toBe(true);
    });

    it('should submit answers and transition to REVIEW state', () => {
        vi.spyOn(practiceQuizService, 'getCurrentSession').mockReturnValue(of(null));
        vi.spyOn(practiceQuizService, 'getPastSessions').mockReturnValue(of([]));
        component.ngOnInit();

        component['currentSession'].set(mockSession);
        component.toggleOption(101, 201, false);

        vi.spyOn(practiceQuizService, 'submitSession').mockReturnValue(of(mockReview));

        component.submitQuiz();

        expect(component.state()).toBe('REVIEW');
        expect(component.reviewSession()).toEqual(mockReview);
        expect(component.currentSession()).toBeNull();
    });

    it('should open review of past session and return back to overview', () => {
        vi.spyOn(practiceQuizService, 'getCurrentSession').mockReturnValue(of(null));
        vi.spyOn(practiceQuizService, 'getPastSessions').mockReturnValue(of([mockSummary]));
        vi.spyOn(practiceQuizService, 'getSessionReview').mockReturnValue(of(mockReview));

        component.ngOnInit();
        component.openReview(42);

        expect(component.state()).toBe('REVIEW');
        expect(component.reviewSession()).toEqual(mockReview);

        component.backToOverview();
        expect(component.state()).toBe('OVERVIEW');
        expect(component.reviewSession()).toBeNull();
    });
});
