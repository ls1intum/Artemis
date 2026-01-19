import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { LearningPathNavComponent } from 'app/atlas/overview/learning-path-student-nav/learning-path-student-nav.component';
import { LearningObjectType, LearningPathNavigationDTO } from 'app/atlas/shared/entities/learning-path.model';
import { LearningPathNavOverviewComponent } from 'app/atlas/overview/learning-path-nav-overview/learning-path-nav-overview.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { LearningPathNavigationService } from 'app/atlas/overview/learning-path-navigation.service';
import { MockComponent, MockProvider } from 'ng-mocks';
import { ScienceService } from 'app/shared/science/science.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('LearningPathStudentNavComponent', () => {
    setupTestBed({ zoneless: true });
    let component: LearningPathNavComponent;
    let fixture: ComponentFixture<LearningPathNavComponent>;
    let learningPathNavigationService: LearningPathNavigationService;
    let learningPathNavigationSpy: ReturnType<typeof vi.spyOn>;

    const navigationDto: LearningPathNavigationDTO = {
        predecessorLearningObject: {
            id: 1,
            name: 'Exercise 1',
            type: LearningObjectType.EXERCISE,
            completed: true,
            competencyId: 1,
            repeatedTest: false,
            unreleased: false,
        },
        currentLearningObject: {
            id: 2,
            name: 'Lecture 2',
            type: LearningObjectType.LECTURE,
            completed: false,
            competencyId: 2,
            repeatedTest: false,
            unreleased: false,
        },
        successorLearningObject: {
            id: 3,
            name: 'Exercise 3',
            type: LearningObjectType.EXERCISE,
            completed: false,
            competencyId: 2,
            repeatedTest: false,
            unreleased: false,
        },
        progress: 50,
    };

    const learningPathId = 1;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LearningPathNavComponent, MockComponent(LearningPathNavOverviewComponent), FaIconComponent],
            providers: [
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                {
                    provide: LearningPathNavigationService,
                    useValue: {
                        isLoading: vi.fn(),
                        learningPathNavigation: vi.fn(),
                        currentLearningObject: vi.fn(),
                        loadLearningPathNavigation: vi.fn(),
                        loadRelativeLearningPathNavigation: vi.fn(),
                        completeLearningPath: vi.fn(),
                    },
                },
                MockProvider(ScienceService),
            ],
        })

            .compileComponents();

        learningPathNavigationService = TestBed.inject(LearningPathNavigationService);
        learningPathNavigationSpy = vi.spyOn(learningPathNavigationService, 'learningPathNavigation');

        fixture = TestBed.createComponent(LearningPathNavComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('learningPathId', learningPathId);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should load initial learning path navigation', async () => {
        const loadLearningPathNavigationSpy = vi.spyOn(learningPathNavigationService, 'loadLearningPathNavigation');
        learningPathNavigationSpy.mockReturnValue(navigationDto);

        fixture.detectChanges();

        expect(loadLearningPathNavigationSpy).toHaveBeenCalledExactlyOnceWith(learningPathId);
    });

    it('should show progress bar percentage', async () => {
        learningPathNavigationSpy.mockReturnValue(navigationDto);

        fixture.detectChanges();

        const progressBar = fixture.nativeElement.querySelector('.progress-bar');

        expect(progressBar.style.width).toBe('50%');
    });

    it('should set learningPathProgress correctly', () => {
        learningPathNavigationSpy.mockReturnValue(navigationDto);

        fixture.detectChanges();

        expect(component.learningPathProgress()).toBe(50);
    });

    it('should set learning objects correctly', async () => {
        learningPathNavigationSpy.mockReturnValue(navigationDto);

        fixture.detectChanges();

        expect(component.predecessorLearningObject()).toEqual(navigationDto.predecessorLearningObject);
        expect(component.currentLearningObject()).toEqual(navigationDto.currentLearningObject);
        expect(component.successorLearningObject()).toEqual(navigationDto.successorLearningObject);
    });

    it('should navigate with next button', async () => {
        learningPathNavigationSpy.mockReturnValue(navigationDto);
        const loadRelativeLearningPathNavigationSpy = vi.spyOn(learningPathNavigationService, 'loadRelativeLearningPathNavigation');
        const isLoadingSuccessor = vi.spyOn(component.isLoadingSuccessor, 'set');
        fixture.detectChanges();

        const nextButton = fixture.nativeElement.querySelector('#next-button');
        nextButton.click();

        await fixture.whenStable();
        fixture.detectChanges();

        expect(loadRelativeLearningPathNavigationSpy).toHaveBeenCalledExactlyOnceWith(learningPathId, navigationDto.successorLearningObject);
        expect(isLoadingSuccessor).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSuccessor).toHaveBeenNthCalledWith(2, false);
    });

    it('should navigate with previous button', async () => {
        learningPathNavigationSpy.mockReturnValue(navigationDto);
        const loadRelativeLearningPathNavigationSpy = vi.spyOn(learningPathNavigationService, 'loadRelativeLearningPathNavigation');
        const isLoadingSuccessor = vi.spyOn(component.isLoadingPredecessor, 'set');
        fixture.detectChanges();

        const nextButton = fixture.nativeElement.querySelector('#previous-button');
        nextButton.click();

        await fixture.whenStable();
        fixture.detectChanges();

        expect(loadRelativeLearningPathNavigationSpy).toHaveBeenCalledExactlyOnceWith(learningPathId, navigationDto.predecessorLearningObject);
        expect(isLoadingSuccessor).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSuccessor).toHaveBeenNthCalledWith(2, false);
    });

    it('should set current to previous unit on complete button', async () => {
        const completeLearningPathSpy = vi.spyOn(learningPathNavigationService, 'completeLearningPath');
        learningPathNavigationSpy.mockReturnValue({
            predecessorLearningObject: { ...navigationDto.predecessorLearningObject },
            currentLearningObject: { ...navigationDto.currentLearningObject },
            progress: 95,
        });

        fixture.detectChanges();

        const completeButton = fixture.nativeElement.querySelector('#complete-button');
        completeButton.click();

        expect(completeLearningPathSpy).toHaveBeenCalledOnce();
    });

    it('should show navigation overview on click', async () => {
        const setIsDropdownOpen = vi.spyOn(component.isDropdownOpen, 'set');

        component.setIsDropdownOpen(false);

        expect(setIsDropdownOpen).toHaveBeenNthCalledWith(1, false);
    });
});
