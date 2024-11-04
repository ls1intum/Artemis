import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { LearningPathNavComponent } from 'app/course/learning-paths/components/learning-path-student-nav/learning-path-student-nav.component';
import { LearningObjectType, LearningPathNavigationDTO } from 'app/entities/competency/learning-path.model';
import { LearningPathNavOverviewComponent } from 'app/course/learning-paths/components/learning-path-nav-overview/learning-path-nav-overview.component';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { LearningPathNavigationService } from 'app/course/learning-paths/services/learning-path-navigation.service';
import { MockComponent } from 'ng-mocks';

describe('LearningPathStudentNavComponent', () => {
    let component: LearningPathNavComponent;
    let fixture: ComponentFixture<LearningPathNavComponent>;
    let learningPathNavigationService: LearningPathNavigationService;
    let learningPathNavigationSpy: jest.SpyInstance;

    const navigationDto: LearningPathNavigationDTO = {
        predecessorLearningObject: {
            id: 1,
            name: 'Exercise 1',
            type: LearningObjectType.EXERCISE,
            completed: true,
            competencyId: 1,
            unreleased: false,
        },
        currentLearningObject: {
            id: 2,
            name: 'Lecture 2',
            type: LearningObjectType.LECTURE,
            completed: false,
            competencyId: 2,
            unreleased: false,
        },
        successorLearningObject: {
            id: 3,
            name: 'Exercise 3',
            type: LearningObjectType.EXERCISE,
            completed: false,
            competencyId: 2,
            unreleased: false,
        },
        progress: 50,
    };

    const learningPathId = 1;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LearningPathNavComponent, MockComponent(LearningPathNavOverviewComponent)],
            providers: [
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                {
                    provide: LearningPathNavigationService,
                    useValue: {
                        isLoading: jest.fn(),
                        learningPathNavigation: jest.fn(),
                        currentLearningObject: jest.fn(),
                        loadLearningPathNavigation: jest.fn(),
                        loadRelativeLearningPathNavigation: jest.fn(),
                        completeLearningPath: jest.fn(),
                    },
                },
            ],
        })

            .compileComponents();

        learningPathNavigationService = TestBed.inject(LearningPathNavigationService);
        learningPathNavigationSpy = jest.spyOn(learningPathNavigationService, 'learningPathNavigation');

        fixture = TestBed.createComponent(LearningPathNavComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('learningPathId', learningPathId);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should load initial learning path navigation', async () => {
        const loadLearningPathNavigationSpy = jest.spyOn(learningPathNavigationService, 'loadLearningPathNavigation');
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
        const loadRelativeLearningPathNavigationSpy = jest.spyOn(learningPathNavigationService, 'loadRelativeLearningPathNavigation');
        const isLoadingSuccessor = jest.spyOn(component.isLoadingSuccessor, 'set');
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
        const loadRelativeLearningPathNavigationSpy = jest.spyOn(learningPathNavigationService, 'loadRelativeLearningPathNavigation');
        const isLoadingSuccessor = jest.spyOn(component.isLoadingPredecessor, 'set');
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
        const completeLearningPathSpy = jest.spyOn(learningPathNavigationService, 'completeLearningPath');
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
        const setIsDropdownOpen = jest.spyOn(component.isDropdownOpen, 'set');

        component.setIsDropdownOpen(false);

        expect(setIsDropdownOpen).toHaveBeenNthCalledWith(1, false);
    });
});
