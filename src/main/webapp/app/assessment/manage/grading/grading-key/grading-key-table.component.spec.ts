import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { GradingKeyTableComponent } from 'app/assessment/manage/grading/grading-key/grading-key-table.component';
import { ActivatedRoute, Params } from '@angular/router';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { of } from 'rxjs';
import { GradingService } from 'app/assessment/manage/grading/grading-service';
import { BonusService } from 'app/assessment/manage/grading/bonus/bonus.service';
import { ScoresStorageService } from 'app/core/course/manage/course-scores/scores-storage.service';
import { GradeType, GradingScale } from 'app/assessment/shared/entities/grading-scale.model';
import { GradeStep, GradeStepsDTO } from 'app/assessment/shared/entities/grade-step.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { GradeStepBoundsPipe } from 'app/shared/pipes/grade-step-bounds.pipe';
import { SafeHtmlPipe } from 'app/shared/pipes/safe-html.pipe';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { MockComponent } from 'ng-mocks';
import { HttpResponse } from '@angular/common/http';
import { Bonus, BonusStrategy } from 'app/assessment/shared/entities/bonus.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { CourseScores } from 'app/core/course/manage/course-scores/course-scores';

describe('GradingKeyTableComponent', () => {
    setupTestBed({ zoneless: true });
    let fixture: ComponentFixture<GradingKeyTableComponent>;
    let component: GradingKeyTableComponent;
    let gradingSystemService: GradingService;
    let bonusService: BonusService;
    let scoresStorageService: ScoresStorageService;

    const courseId = 123;
    const examId = 456;
    const studentGrade = '2.0';

    const gradeStep1: GradeStep = {
        gradeName: 'Fail',
        lowerBoundPercentage: 0,
        upperBoundPercentage: 50,
        lowerBoundInclusive: true,
        upperBoundInclusive: false,
        isPassingGrade: false,
    };
    const gradeStep2: GradeStep = {
        gradeName: 'Pass',
        lowerBoundPercentage: 50,
        upperBoundPercentage: 100,
        lowerBoundInclusive: true,
        upperBoundInclusive: true,
        isPassingGrade: true,
    };

    const gradeStepsDTO: GradeStepsDTO = {
        title: 'Test Exam',
        gradeType: GradeType.GRADE,
        gradeSteps: [gradeStep1, gradeStep2],
        maxPoints: 100,
        plagiarismGrade: '5.0',
        noParticipationGrade: '5.0',
    };

    describe('with exam context', () => {
        const route = {
            snapshot: {
                params: {} as Params,
                queryParams: { grade: studentGrade } as Params,
                data: {},
            },
            parent: {
                snapshot: { params: {} },
                parent: {
                    snapshot: {
                        params: { courseId, examId } as Params,
                    },
                },
            },
        } as ActivatedRoute;

        beforeEach(() => {
            return TestBed.configureTestingModule({
                imports: [GradingKeyTableComponent],
                providers: [
                    { provide: ActivatedRoute, useValue: route },
                    { provide: TranslateService, useClass: MockTranslateService },
                    MockProvider(GradingService),
                    MockProvider(BonusService),
                    MockProvider(ScoresStorageService),
                ],
            })
                .overrideComponent(GradingKeyTableComponent, {
                    remove: {
                        imports: [TranslateDirective, ArtemisTranslatePipe, GradeStepBoundsPipe, SafeHtmlPipe, HelpIconComponent],
                    },
                    add: {
                        imports: [
                            MockDirective(TranslateDirective),
                            MockPipe(ArtemisTranslatePipe),
                            MockPipe(GradeStepBoundsPipe),
                            MockPipe(SafeHtmlPipe),
                            MockComponent(HelpIconComponent),
                        ],
                    },
                })
                .compileComponents()
                .then(() => {
                    gradingSystemService = TestBed.inject(GradingService);
                    bonusService = TestBed.inject(BonusService);
                    scoresStorageService = TestBed.inject(ScoresStorageService);

                    vi.spyOn(gradingSystemService, 'findGradeSteps').mockReturnValue(of(gradeStepsDTO));
                    vi.spyOn(gradingSystemService, 'sortGradeSteps').mockImplementation((steps) => [...steps].sort((a, b) => a.lowerBoundPercentage - b.lowerBoundPercentage));
                    vi.spyOn(gradingSystemService, 'setGradePoints').mockImplementation((steps, maxPoints) => {
                        steps.forEach((step) => {
                            step.lowerBoundPoints = (maxPoints * step.lowerBoundPercentage) / 100;
                            step.upperBoundPoints = (maxPoints * step.upperBoundPercentage) / 100;
                        });
                    });
                    vi.spyOn(gradingSystemService, 'hasPointsSet').mockReturnValue(false);

                    fixture = TestBed.createComponent(GradingKeyTableComponent);
                    component = fixture.componentInstance;
                });
        });

        afterEach(() => {
            vi.restoreAllMocks();
        });

        it('should initialize with exam context', () => {
            fixture.detectChanges();

            expect(component).toBeTruthy();
            expect(component.courseId).toBe(courseId);
            expect(component.examId).toBe(examId);
            expect(component.isExam).toBe(true);
        });

        it('should load grade steps from service', () => {
            fixture.detectChanges();

            expect(gradingSystemService.findGradeSteps).toHaveBeenCalledWith(courseId, examId);
            expect(component.title).toBe('Test Exam');
            expect(component.gradeSteps).toHaveLength(2);
            expect(component.isBonus).toBe(false);
            expect(component.plagiarismGrade).toBe('5.0');
            expect(component.noParticipationGrade).toBe('5.0');
        });

        it('should set grade points for exam', () => {
            fixture.detectChanges();

            expect(gradingSystemService.setGradePoints).toHaveBeenCalledWith(expect.any(Array), 100);
        });

        it('should set studentGradeOrBonusPointsOrGradeBonus from query params', () => {
            fixture.detectChanges();

            expect(component.studentGradeOrBonusPointsOrGradeBonus()).toBe(studentGrade);
        });

        it('should expose GradeEditMode enum', () => {
            expect(component.GradeEditMode).toBeDefined();
        });

        it('should expose faChevronLeft icon', () => {
            expect(component.faChevronLeft).toBeDefined();
        });
    });

    describe('with course context', () => {
        const route = {
            snapshot: {
                params: {} as Params,
                queryParams: {} as Params,
                data: {},
            },
            parent: {
                snapshot: {
                    params: { courseId } as Params,
                },
            },
        } as ActivatedRoute;

        beforeEach(() => {
            return TestBed.configureTestingModule({
                imports: [GradingKeyTableComponent],
                providers: [
                    { provide: ActivatedRoute, useValue: route },
                    { provide: TranslateService, useClass: MockTranslateService },
                    MockProvider(GradingService),
                    MockProvider(BonusService),
                    MockProvider(ScoresStorageService),
                ],
            })
                .overrideComponent(GradingKeyTableComponent, {
                    remove: {
                        imports: [TranslateDirective, ArtemisTranslatePipe, GradeStepBoundsPipe, SafeHtmlPipe, HelpIconComponent],
                    },
                    add: {
                        imports: [
                            MockDirective(TranslateDirective),
                            MockPipe(ArtemisTranslatePipe),
                            MockPipe(GradeStepBoundsPipe),
                            MockPipe(SafeHtmlPipe),
                            MockComponent(HelpIconComponent),
                        ],
                    },
                })
                .compileComponents()
                .then(() => {
                    gradingSystemService = TestBed.inject(GradingService);
                    scoresStorageService = TestBed.inject(ScoresStorageService);

                    vi.spyOn(gradingSystemService, 'findGradeSteps').mockReturnValue(of(gradeStepsDTO));
                    vi.spyOn(gradingSystemService, 'sortGradeSteps').mockImplementation((steps) => [...steps].sort((a, b) => a.lowerBoundPercentage - b.lowerBoundPercentage));
                    vi.spyOn(gradingSystemService, 'setGradePoints').mockImplementation(() => {});
                    vi.spyOn(gradingSystemService, 'hasPointsSet').mockReturnValue(false);
                    vi.spyOn(scoresStorageService, 'getStoredTotalScores').mockReturnValue(
                        new CourseScores(200, 200, 0, { absoluteScore: 150, relativeScore: 75, currentRelativeScore: 80, presentationScore: 0 }),
                    );

                    fixture = TestBed.createComponent(GradingKeyTableComponent);
                    component = fixture.componentInstance;
                });
        });

        afterEach(() => {
            vi.restoreAllMocks();
        });

        it('should initialize with course context', () => {
            fixture.detectChanges();

            expect(component).toBeTruthy();
            expect(component.courseId).toBe(courseId);
            expect(component.examId).toBeUndefined();
            expect(component.isExam).toBe(false);
        });

        it('should get max points from scores storage for course', () => {
            fixture.detectChanges();

            expect(scoresStorageService.getStoredTotalScores).toHaveBeenCalledWith(courseId);
            expect(gradingSystemService.setGradePoints).toHaveBeenCalledWith(expect.any(Array), 200);
        });
    });

    describe('with forBonus context', () => {
        const route = {
            snapshot: {
                params: {} as Params,
                queryParams: {} as Params,
                data: { forBonus: true },
            },
            parent: {
                snapshot: { params: {} },
                parent: {
                    snapshot: {
                        params: { courseId, examId } as Params,
                    },
                },
            },
        } as unknown as ActivatedRoute;

        const sourceGradingScale: GradingScale = {
            id: 1,
            gradeType: GradeType.BONUS,
            gradeSteps: [gradeStep1, gradeStep2],
            plagiarismGrade: '0',
            noParticipationGrade: '0',
            exam: { title: 'Source Exam', examMaxPoints: 50 } as Exam,
        };

        const bonus: Bonus = {
            id: 1,
            bonusStrategy: BonusStrategy.GRADES_CONTINUOUS,
            weight: 1,
            sourceGradingScale,
        };

        beforeEach(() => {
            return TestBed.configureTestingModule({
                imports: [GradingKeyTableComponent],
                providers: [
                    { provide: ActivatedRoute, useValue: route },
                    { provide: TranslateService, useClass: MockTranslateService },
                    MockProvider(GradingService),
                    MockProvider(BonusService),
                    MockProvider(ScoresStorageService),
                ],
            })
                .overrideComponent(GradingKeyTableComponent, {
                    remove: {
                        imports: [TranslateDirective, ArtemisTranslatePipe, GradeStepBoundsPipe, SafeHtmlPipe, HelpIconComponent],
                    },
                    add: {
                        imports: [
                            MockDirective(TranslateDirective),
                            MockPipe(ArtemisTranslatePipe),
                            MockPipe(GradeStepBoundsPipe),
                            MockPipe(SafeHtmlPipe),
                            MockComponent(HelpIconComponent),
                        ],
                    },
                })
                .compileComponents()
                .then(() => {
                    gradingSystemService = TestBed.inject(GradingService);
                    bonusService = TestBed.inject(BonusService);

                    vi.spyOn(bonusService, 'findBonusForExam').mockReturnValue(of(new HttpResponse({ body: bonus })));
                    vi.spyOn(gradingSystemService, 'getGradingScaleTitle').mockReturnValue('Source Exam');
                    vi.spyOn(gradingSystemService, 'getGradingScaleMaxPoints').mockReturnValue(50);
                    vi.spyOn(gradingSystemService, 'sortGradeSteps').mockImplementation((steps) => [...steps].sort((a, b) => a.lowerBoundPercentage - b.lowerBoundPercentage));
                    vi.spyOn(gradingSystemService, 'setGradePoints').mockImplementation(() => {});
                    vi.spyOn(gradingSystemService, 'hasPointsSet').mockReturnValue(false);

                    fixture = TestBed.createComponent(GradingKeyTableComponent);
                    component = fixture.componentInstance;
                });
        });

        afterEach(() => {
            vi.restoreAllMocks();
        });

        it('should load grade steps from bonus service when forBonus is true', () => {
            fixture.detectChanges();

            expect(bonusService.findBonusForExam).toHaveBeenCalledWith(courseId, examId, true);
            expect(component.forBonus()).toBe(true);
            expect(component.isBonus).toBe(true);
        });
    });

    describe('with undefined gradeSteps', () => {
        const route = {
            snapshot: {
                params: {} as Params,
                queryParams: {} as Params,
                data: {},
            },
            parent: {
                snapshot: { params: {} },
                parent: {
                    snapshot: {
                        params: { courseId, examId } as Params,
                    },
                },
            },
        } as ActivatedRoute;

        beforeEach(() => {
            return TestBed.configureTestingModule({
                imports: [GradingKeyTableComponent],
                providers: [
                    { provide: ActivatedRoute, useValue: route },
                    { provide: TranslateService, useClass: MockTranslateService },
                    MockProvider(GradingService),
                    MockProvider(BonusService),
                    MockProvider(ScoresStorageService),
                ],
            })
                .overrideComponent(GradingKeyTableComponent, {
                    remove: {
                        imports: [TranslateDirective, ArtemisTranslatePipe, GradeStepBoundsPipe, SafeHtmlPipe, HelpIconComponent],
                    },
                    add: {
                        imports: [
                            MockDirective(TranslateDirective),
                            MockPipe(ArtemisTranslatePipe),
                            MockPipe(GradeStepBoundsPipe),
                            MockPipe(SafeHtmlPipe),
                            MockComponent(HelpIconComponent),
                        ],
                    },
                })
                .compileComponents()
                .then(() => {
                    gradingSystemService = TestBed.inject(GradingService);

                    vi.spyOn(gradingSystemService, 'findGradeSteps').mockReturnValue(of(undefined));
                    vi.spyOn(gradingSystemService, 'hasPointsSet').mockReturnValue(false);

                    fixture = TestBed.createComponent(GradingKeyTableComponent);
                    component = fixture.componentInstance;
                });
        });

        afterEach(() => {
            vi.restoreAllMocks();
        });

        it('should handle undefined grade steps gracefully', () => {
            fixture.detectChanges();

            expect(component.gradeSteps).toHaveLength(0);
            expect(component.title).toBeUndefined();
        });
    });
});
