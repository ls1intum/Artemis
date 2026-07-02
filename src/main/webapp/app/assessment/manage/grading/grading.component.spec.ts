import { MockInstance, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { GradeEditMode, GradingComponent, GradingViewMode } from 'app/assessment/manage/grading/grading.component';
import { ActivatedRoute, Params } from '@angular/router';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { of } from 'rxjs';
import { GradeType, GradingScale } from 'app/assessment/shared/entities/grading-scale.model';
import { DocumentationButtonComponent } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { GradingInfoModalComponent } from 'app/assessment/manage/grading/grading-info-modal/grading-info-modal.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { GradingPresentationsComponent, PresentationType } from 'app/assessment/manage/grading/grading-presentations/grading-presentations.component';
import { ModePickerComponent } from 'app/exercise/mode-picker/mode-picker.component';
import { GradeStepBoundsPipe } from 'app/foundation/pipes/grade-step-bounds.pipe';
import { SafeHtmlPipe } from 'app/foundation/pipes/safe-html.pipe';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { GradingService } from 'app/assessment/manage/grading/grading-service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { Course } from 'app/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { GradeStep } from 'app/assessment/shared/entities/grade-step.model';
import { cloneDeep } from 'lodash-es';
import { MockCourseManagementService } from 'test/helpers/mocks/service/mock-course-management.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { downloadCsv } from 'app/foundation/util/csv-download.util';
import { DialogService } from 'primeng/dynamicdialog';
import { GradingScaleDTO, toGradingScaleDTO } from 'app/assessment/shared/entities/grading-scale-dto.model';

vi.mock('app/foundation/util/csv-download.util', () => {
    return {
        downloadCsv: vi.fn(),
    };
});

describe('GradingComponent', () => {
    setupTestBed({ zoneless: true });
    let fixture: ComponentFixture<GradingComponent>;
    let comp: GradingComponent;
    let gradingService: GradingService;
    let translateService: TranslateService;
    let translateStub: MockInstance;
    let examService: ExamManagementService;

    const mockGradingScale = new GradingScale();
    mockGradingScale.gradeSteps = [];
    mockGradingScale.gradeType = GradeType.GRADE;

    const gradeStep1: GradeStep = {
        id: 1,
        gradeName: 'Fail',
        lowerBoundPercentage: 0,
        upperBoundPercentage: 40,
        lowerBoundInclusive: true,
        upperBoundInclusive: false,
        isPassingGrade: false,
    };
    const gradeStep2: GradeStep = {
        id: 2,
        gradeName: 'Pass',
        lowerBoundPercentage: 40,
        upperBoundPercentage: 80,
        lowerBoundInclusive: true,
        upperBoundInclusive: false,
        isPassingGrade: true,
    };
    const gradeStep3: GradeStep = {
        id: 3,
        gradeName: 'Excellent',
        lowerBoundPercentage: 80,
        upperBoundPercentage: 100,
        lowerBoundInclusive: true,
        upperBoundInclusive: true,
        isPassingGrade: true,
    };
    const gradeSteps = [gradeStep1, gradeStep2, gradeStep3];

    const gradeStep4: GradeStep = {
        gradeName: 'Sticky',
        lowerBoundPercentage: 100,
        upperBoundPercentage: 200,
        lowerBoundInclusive: false,
        upperBoundInclusive: true,
        isPassingGrade: true,
    };
    const intervalGradeSteps = [gradeStep1, { ...gradeStep2, upperBoundPercentage: 65 }, { ...gradeStep3, lowerBoundPercentage: 65 }, gradeStep4];

    const exam = new Exam();
    exam.examMaxPoints = 100;
    const course = new Course();
    course.maxPoints = 100;

    const courseId = 123;
    const examId = 456;

    /**
     * Patches a single grade step in the signal-form model and commits the change. Grade steps are no longer mutated
     * in place on `comp.gradingScale` (that getter rebuilds a fresh object each read), so tests go through the model.
     */
    function patchStep(index: number, patch: Partial<GradeStep>) {
        comp.gradeStepsModel.update((model) => {
            const gradeSteps = model.gradeSteps.map((gradeStep) => ({ ...gradeStep }));
            Object.assign(gradeSteps[index], patch);
            return { ...model, gradeSteps };
        });
    }

    function setupComponent(isExam = true, gradingScaleBody: GradingScale | null = null) {
        const route = {
            params: of(isExam ? { courseId, examId } : ({ courseId } as Params)),
        } as ActivatedRoute;

        const gradingScaleToUse = gradingScaleBody ?? new GradingScale();
        gradingScaleToUse.gradeSteps = cloneDeep(gradeSteps);

        return TestBed.configureTestingModule({
            providers: [
                { provide: ActivatedRoute, useValue: route },
                MockProvider(ExamManagementService),
                MockProvider(DialogService),
                { provide: CourseManagementService, useClass: MockCourseManagementService },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                gradingService = TestBed.inject(GradingService);
                examService = TestBed.inject(ExamManagementService);
                translateService = TestBed.inject(TranslateService);

                vi.spyOn(examService, 'find').mockReturnValue(of(new HttpResponse<Exam>({ body: exam })));

                if (isExam) {
                    vi.spyOn(gradingService, 'findGradingScaleForExam').mockReturnValue(of(new HttpResponse<GradingScaleDTO>({ body: toGradingScaleDTO(gradingScaleToUse) })));
                } else {
                    vi.spyOn(gradingService, 'findGradingScaleForCourse').mockReturnValue(of(new HttpResponse<GradingScaleDTO>({ body: toGradingScaleDTO(gradingScaleToUse) })));
                }

                fixture = TestBed.createComponent(GradingComponent);
                comp = fixture.componentInstance;

                comp.gradingScale = new GradingScale();
                comp.gradeStepsModel.update((model) => ({ ...model, gradeSteps: cloneDeep(gradeSteps) }));
                comp.courseId = courseId;
                comp.examId = examId;
                comp.firstPassingGrade.set('Pass');
                translateStub = vi.spyOn(translateService, 'instant');
            });
    }

    describe('with course context', () => {
        const route = {
            params: of({ courseId } as Params),
        } as ActivatedRoute;

        beforeEach(() => {
            return TestBed.configureTestingModule({
                imports: [GradingComponent],
                providers: [
                    { provide: ActivatedRoute, useValue: route },
                    { provide: TranslateService, useClass: MockTranslateService },
                    {
                        provide: GradingService,
                        useValue: {
                            findGradingScaleForCourse: vi.fn().mockReturnValue(of(new HttpResponse({ body: mockGradingScale }))),
                            sortGradeSteps: vi.fn().mockReturnValue([]),
                        },
                    },
                    {
                        provide: CourseManagementService,
                        useValue: {
                            find: vi.fn().mockReturnValue(of(new HttpResponse({ body: { id: courseId } as Course }))),
                        },
                    },
                    {
                        provide: ExamManagementService,
                        useValue: {
                            find: vi.fn(),
                        },
                    },
                ],
            })
                .overrideComponent(GradingComponent, {
                    remove: {
                        imports: [
                            DocumentationButtonComponent,
                            GradingInfoModalComponent,
                            FaIconComponent,
                            TranslateDirective,
                            ArtemisTranslatePipe,
                            HelpIconComponent,
                            GradingPresentationsComponent,
                            ModePickerComponent,
                            GradeStepBoundsPipe,
                            SafeHtmlPipe,
                            DeleteButtonDirective,
                        ],
                    },
                    add: {
                        imports: [
                            MockComponent(DocumentationButtonComponent),
                            MockComponent(GradingInfoModalComponent),
                            MockComponent(FaIconComponent),
                            MockDirective(TranslateDirective),
                            MockPipe(ArtemisTranslatePipe),
                            MockComponent(HelpIconComponent),
                            MockComponent(GradingPresentationsComponent),
                            MockComponent(ModePickerComponent),
                            MockPipe(GradeStepBoundsPipe),
                            MockPipe(SafeHtmlPipe),
                            MockDirective(DeleteButtonDirective),
                        ],
                    },
                })
                .compileComponents()
                .then(() => {
                    fixture = TestBed.createComponent(GradingComponent);
                    comp = fixture.componentInstance;
                });
        });

        it('should initialize with course context', () => {
            fixture.detectChanges();

            expect(comp).toBeTruthy();
            expect(comp.courseId).toBe(courseId);
            expect(comp.examId).toBeUndefined();
            expect(comp.isExam()).toBe(false);
        });

        it('should expose GradeType enum', () => {
            expect(comp.GradeType).toBe(GradeType);
        });

        it('should expose GradeEditMode enum', () => {
            expect(comp.GradeEditMode).toBe(GradeEditMode);
        });

        it('should expose documentation type', () => {
            expect(comp.documentationType).toBe('Grading');
        });

        it('should expose faExclamationTriangle icon', () => {
            expect(comp.faExclamationTriangle).toBeDefined();
        });

        it('should have default view mode as interval', () => {
            expect(comp.viewMode()).toBe(GradingViewMode.INTERVAL);
        });

        it('should switch view mode', () => {
            expect(comp.viewMode()).toBe(GradingViewMode.INTERVAL);
            comp.setViewMode(GradingViewMode.DETAILED);
            expect(comp.viewMode()).toBe(GradingViewMode.DETAILED);
            comp.setViewMode(GradingViewMode.INTERVAL);
            expect(comp.viewMode()).toBe(GradingViewMode.INTERVAL);
        });

        it('should have interval mode picker options', () => {
            expect(comp.intervalModePickerOptions).toHaveLength(2);
            expect(comp.intervalModePickerOptions[0].value).toBe(GradeEditMode.PERCENTAGE);
            expect(comp.intervalModePickerOptions[1].value).toBe(GradeEditMode.POINTS);
        });
    });

    describe('with exam context', () => {
        const route = {
            params: of({ courseId: 456, examId: 789 } as Params),
        } as ActivatedRoute;

        beforeEach(() => {
            return TestBed.configureTestingModule({
                imports: [GradingComponent],
                providers: [
                    { provide: ActivatedRoute, useValue: route },
                    { provide: TranslateService, useClass: MockTranslateService },
                    {
                        provide: GradingService,
                        useValue: {
                            findGradingScaleForExam: vi.fn().mockReturnValue(of(new HttpResponse({ body: mockGradingScale }))),
                            sortGradeSteps: vi.fn().mockReturnValue([]),
                        },
                    },
                    {
                        provide: CourseManagementService,
                        useValue: {
                            find: vi.fn(),
                        },
                    },
                    {
                        provide: ExamManagementService,
                        useValue: {
                            find: vi.fn().mockReturnValue(of(new HttpResponse({ body: { id: 789 } as Exam }))),
                        },
                    },
                ],
            })
                .overrideComponent(GradingComponent, {
                    remove: {
                        imports: [
                            DocumentationButtonComponent,
                            GradingInfoModalComponent,
                            FaIconComponent,
                            TranslateDirective,
                            ArtemisTranslatePipe,
                            HelpIconComponent,
                            GradingPresentationsComponent,
                            ModePickerComponent,
                            GradeStepBoundsPipe,
                            SafeHtmlPipe,
                            DeleteButtonDirective,
                        ],
                    },
                    add: {
                        imports: [
                            MockComponent(DocumentationButtonComponent),
                            MockComponent(GradingInfoModalComponent),
                            MockComponent(FaIconComponent),
                            MockDirective(TranslateDirective),
                            MockPipe(ArtemisTranslatePipe),
                            MockComponent(HelpIconComponent),
                            MockComponent(GradingPresentationsComponent),
                            MockComponent(ModePickerComponent),
                            MockPipe(GradeStepBoundsPipe),
                            MockPipe(SafeHtmlPipe),
                            MockDirective(DeleteButtonDirective),
                        ],
                    },
                })
                .compileComponents()
                .then(() => {
                    fixture = TestBed.createComponent(GradingComponent);
                    comp = fixture.componentInstance;
                });
        });

        it('should initialize with exam context', () => {
            fixture.detectChanges();

            expect(comp).toBeTruthy();
            expect(comp.courseId).toBe(456);
            expect(comp.examId).toBe(789);
            expect(comp.isExam()).toBe(true);
        });
    });

    describe('detailed view mode functionality', () => {
        beforeEach(() => setupComponent(true));

        afterEach(() => {
            vi.restoreAllMocks();
        });

        it('should handle find response for exam', () => {
            const findGradingScaleForExamStub = vi
                .spyOn(gradingService, 'findGradingScaleForExam')
                .mockReturnValue(of(new HttpResponse<GradingScaleDTO>({ body: toGradingScaleDTO(comp.gradingScale) })));
            const findExamStub = vi.spyOn(examService, 'find').mockReturnValue(of(new HttpResponse<Exam>({ body: exam })));

            fixture.changeDetectorRef.detectChanges();

            expect(comp.isExam()).toBe(true);
            expect(findGradingScaleForExamStub).toHaveBeenNthCalledWith(1, courseId, examId);
            expect(findGradingScaleForExamStub).toHaveBeenCalledTimes(1);
            expect(findExamStub).toHaveBeenCalledTimes(1);
            expect(comp.exam()).toStrictEqual(exam);
            expect(comp.maxPoints()).toBe(exam.examMaxPoints);
        });

        it('should handle find response for exam and not find a grading scale', () => {
            const findGradingScaleForExamAndReturnNotFoundStub = vi
                .spyOn(gradingService, 'findGradingScaleForExam')
                .mockReturnValue(of(new HttpResponse<GradingScaleDTO>({ status: 404 })));

            fixture.changeDetectorRef.detectChanges();

            expect(findGradingScaleForExamAndReturnNotFoundStub).toHaveBeenNthCalledWith(1, courseId, examId);
            expect(findGradingScaleForExamAndReturnNotFoundStub).toHaveBeenCalledTimes(1);
        });

        it('should generate default grading scale', () => {
            comp.generateDefaultGradingScale();

            expect(comp.gradingScale.gradeType).toStrictEqual(GradeType.GRADE);
            expect(comp.firstPassingGrade()).toBe('4.0');
            expect(comp.lowerBoundInclusivity).toBe(true);
            expect(comp.gradingScale.gradeSteps).toHaveLength(13);
            comp.gradingScale.gradeSteps.forEach((gradeStep) => {
                expect(gradeStep.id).toBeUndefined();
                expect(gradeStep.gradeName).toBeDefined();
                expect(gradeStep.lowerBoundInclusive).toBe(true);
                expect(gradeStep.lowerBoundPercentage).toBeGreaterThanOrEqual(0);
                expect(gradeStep.lowerBoundPercentage).toBeLessThan(101);
                expect(gradeStep.upperBoundPercentage).toBeGreaterThanOrEqual(0);
                expect(gradeStep.upperBoundPercentage).toBeLessThan(101);
                expect(gradeStep.lowerBoundPercentage).toBeLessThanOrEqual(gradeStep.upperBoundPercentage);
                if (gradeStep.upperBoundPercentage === 100) {
                    expect(gradeStep.upperBoundInclusive).toBe(true);
                } else {
                    expect(gradeStep.upperBoundInclusive).toBe(false);
                }
                if (gradeStep.lowerBoundPercentage >= 50) {
                    expect(gradeStep.isPassingGrade).toBe(true);
                }
            });
        });

        it('should delete grade step in detailed mode', () => {
            comp.setViewMode(GradingViewMode.DETAILED);
            comp.deleteGradeStep(1);

            expect(comp.gradingScale.gradeSteps).toHaveLength(2);
            expect(comp.gradingScale.gradeSteps).not.toContainEqual(expect.objectContaining({ gradeName: 'Pass' }));
        });

        it('should create grade step in detailed mode', () => {
            comp.setViewMode(GradingViewMode.DETAILED);
            comp.lowerBoundInclusivity = true;

            comp.createGradeStep();

            expect(comp.gradingScale.gradeSteps).toHaveLength(4);
            expect(comp.gradingScale.gradeSteps[3].id).toBeUndefined();
            expect(comp.gradingScale.gradeSteps[3].gradeName).toBe('');
            expect(comp.gradingScale.gradeSteps[3].lowerBoundPercentage).toBe(100);
            expect(comp.gradingScale.gradeSteps[3].upperBoundPercentage).toBe(100);
            expect(comp.gradingScale.gradeSteps[3].isPassingGrade).toBe(true);
            expect(comp.gradingScale.gradeSteps[3].lowerBoundInclusive).toBe(true);
            expect(comp.gradingScale.gradeSteps[3].upperBoundInclusive).toBe(false);
        });

        it('should delete grade names correctly', () => {
            comp.deleteGradeNames();

            comp.gradingScale.gradeSteps.forEach((gradeStep) => {
                expect(gradeStep.gradeName).toBe('');
            });
        });

        it('should filter grade steps with empty names correctly', () => {
            patchStep(0, { gradeName: '' });
            patchStep(2, { gradeName: '' });

            const filteredGradeSteps = comp.gradeStepsWithNonemptyNames();

            expect(filteredGradeSteps).toHaveLength(1);
            expect(filteredGradeSteps[0].gradeName).toBe('Pass');
        });

        it('should set passing Grades correctly', () => {
            comp.firstPassingGrade.set('Fail');

            comp.setPassingGrades(comp.gradingScale.gradeSteps);

            comp.gradingScale.gradeSteps.forEach((gradeStep) => {
                expect(gradeStep.isPassingGrade).toBe(true);
            });

            comp.firstPassingGrade.set('');

            comp.setPassingGrades(comp.gradingScale.gradeSteps);

            comp.gradingScale.gradeSteps.forEach((gradeStep) => {
                expect(gradeStep.isPassingGrade).toBe(false);
            });
        });

        it('should determine first passing grade correctly', () => {
            comp.determineFirstPassingGrade();

            expect(comp.firstPassingGrade()).toBe('Pass');
        });

        it('should set inclusivity correctly in detailed mode', () => {
            comp.setViewMode(GradingViewMode.DETAILED);
            comp.lowerBoundInclusivity = false;

            comp.setInclusivity();

            comp.gradingScale.gradeSteps.forEach((gradeStep) => {
                expect(gradeStep.upperBoundInclusive).toBe(true);
                if (gradeStep.lowerBoundPercentage === 0) {
                    expect(gradeStep.lowerBoundInclusive).toBe(true);
                } else {
                    expect(gradeStep.lowerBoundInclusive).toBe(false);
                }
            });
        });

        it('should determine lower bound inclusivity correctly', () => {
            comp.setBoundInclusivity();

            expect(comp.lowerBoundInclusivity).toBe(true);
        });

        it('should not delete non-existing grading scale', () => {
            comp.existingGradingScale.set(false);
            const gradingSystemDeleteForCourseSpy = vi.spyOn(gradingService, 'deleteGradingScaleForCourse');
            const gradingSystemDeleteForExamSpy = vi.spyOn(gradingService, 'deleteGradingScaleForExam');

            comp.delete();

            expect(gradingSystemDeleteForCourseSpy).not.toHaveBeenCalled();
            expect(gradingSystemDeleteForExamSpy).not.toHaveBeenCalled();
        });

        it('should delete grading scale for course', () => {
            comp.existingGradingScale.set(true);
            comp.isExam.set(false);
            comp.courseId = courseId;
            const gradingSystemDeleteForCourseStub = vi.spyOn(gradingService, 'deleteGradingScaleForCourse').mockReturnValue(of(new HttpResponse<void>({ body: undefined })));

            comp.delete();

            expect(gradingSystemDeleteForCourseStub).toHaveBeenNthCalledWith(1, comp.courseId);
            expect(gradingSystemDeleteForCourseStub).toHaveBeenCalledTimes(1);
            expect(comp.existingGradingScale()).toBe(false);
        });

        it('should delete grading scale for exam', () => {
            comp.existingGradingScale.set(true);
            comp.isExam.set(true);
            const gradingSystemDeleteForExamStub = vi.spyOn(gradingService, 'deleteGradingScaleForExam').mockReturnValue(of(new HttpResponse<void>({ body: undefined })));

            comp.delete();

            expect(gradingSystemDeleteForExamStub).toHaveBeenNthCalledWith(1, comp.courseId, comp.examId);
            expect(gradingSystemDeleteForExamStub).toHaveBeenCalledTimes(1);
            expect(comp.existingGradingScale()).toBe(false);
        });

        it('should create grading scale correctly for course', () => {
            comp.existingGradingScale.set(false);
            comp.isExam.set(false);
            comp.course.set(course);
            comp.gradeStepsModel.update((model) => ({ ...model, gradeType: GradeType.BONUS }));
            const gradingSystemCreateForCourseMock = vi
                .spyOn(gradingService, 'createGradingScaleForCourse')
                .mockReturnValue(of(new HttpResponse<GradingScaleDTO>({ body: toGradingScaleDTO(comp.gradingScale) })));

            comp.save();

            expect(gradingSystemCreateForCourseMock).toHaveBeenNthCalledWith(1, comp.courseId, comp.gradingScale);
            expect(gradingSystemCreateForCourseMock).toHaveBeenCalledTimes(1);
            expect(comp.existingGradingScale()).toBe(true);
        });

        it('should create grading scale correctly for exam', () => {
            comp.existingGradingScale.set(false);
            comp.isExam.set(true);
            comp.exam.set(exam);
            comp.gradeStepsModel.update((model) => ({ ...model, gradeType: GradeType.BONUS }));
            const gradingSystemCreateForExamMock = vi
                .spyOn(gradingService, 'createGradingScaleForExam')
                .mockReturnValue(of(new HttpResponse<GradingScaleDTO>({ body: toGradingScaleDTO(comp.gradingScale) })));

            comp.save();

            expect(gradingSystemCreateForExamMock).toHaveBeenNthCalledWith(1, comp.courseId, comp.examId, comp.gradingScale);
            expect(gradingSystemCreateForExamMock).toHaveBeenCalledTimes(1);
            expect(comp.existingGradingScale()).toBe(true);
        });

        it('should update grading scale correctly for course', () => {
            comp.existingGradingScale.set(true);
            comp.isExam.set(false);
            comp.course.set(course);
            comp.gradeStepsModel.update((model) => ({ ...model, gradeType: GradeType.BONUS }));
            const gradingSystemUpdateForCourseMock = vi
                .spyOn(gradingService, 'updateGradingScaleForCourse')
                .mockReturnValue(of(new HttpResponse<GradingScaleDTO>({ body: toGradingScaleDTO(comp.gradingScale) })));

            comp.save();

            expect(gradingSystemUpdateForCourseMock).toHaveBeenNthCalledWith(1, comp.courseId, comp.gradingScale);
            expect(gradingSystemUpdateForCourseMock).toHaveBeenCalledTimes(1);
            expect(comp.existingGradingScale()).toBe(true);
        });

        it('should update grading scale correctly for exam', () => {
            comp.existingGradingScale.set(true);
            comp.isExam.set(true);
            comp.exam.set(exam);
            comp.gradeStepsModel.update((model) => ({ ...model, gradeType: GradeType.BONUS }));
            const gradingSystemUpdateForExamMock = vi
                .spyOn(gradingService, 'updateGradingScaleForExam')
                .mockReturnValue(of(new HttpResponse<GradingScaleDTO>({ body: toGradingScaleDTO(comp.gradingScale) })));

            comp.save();

            expect(gradingSystemUpdateForExamMock).toHaveBeenNthCalledWith(1, comp.courseId, comp.examId, comp.gradingScale);
            expect(gradingSystemUpdateForExamMock).toHaveBeenCalledTimes(1);
            expect(comp.existingGradingScale()).toBe(true);
        });

        it('should handle find response correctly', () => {
            comp.handleFindResponse(toGradingScaleDTO(comp.gradingScale));

            expect(comp.firstPassingGrade()).toBe('Pass');
            expect(comp.lowerBoundInclusivity).toBe(true);
            expect(comp.existingGradingScale()).toBe(true);
        });

        it('should validate valid grading scale correctly', () => {
            expect(comp.gradingForm().valid()).toBe(true);
            expect(comp.validPresentationsConfig()).toBe(true);
            expect(comp.invalidGradeStepsMessage()).toBeUndefined();
        });

        it('should validate invalid grading scale with empty grade steps correctly', () => {
            comp.gradeStepsModel.update((model) => ({ ...model, gradeSteps: [] }));
            translateStub.mockReturnValue('empty set');

            expect(comp.gradingForm().valid()).toBe(false);
            expect(comp.invalidGradeStepsMessage()).toBe('empty set');
        });

        it('should validate invalid grading scale with negative max points', () => {
            comp.course.set(course);
            comp.maxPoints.set(-10);
            translateStub.mockReturnValue('negative max points');

            expect(comp.gradingForm().valid()).toBe(false);
            expect(comp.invalidGradeStepsMessage()).toBe('negative max points');
        });

        it('should validate invalid grading scale with empty grade step fields correctly', () => {
            patchStep(0, { gradeName: '' });

            expect(comp.gradingForm().valid()).toBe(false);
            // The required() message is resolved once at form construction, so the raw key is asserted here.
            expect(comp.invalidGradeStepsMessage()).toBe('artemisApp.gradingSystem.error.emptyFields');
        });

        it('should validate invalid grading scale with invalid percentages', () => {
            patchStep(0, { lowerBoundPercentage: -10 });
            translateStub.mockReturnValue('invalid percentage');

            expect(comp.gradingForm().valid()).toBe(false);
            expect(comp.invalidGradeStepsMessage()).toBe('invalid percentage');
        });

        it('should validate invalid grading scale with invalid points', () => {
            comp.maxPoints.set(100);
            patchStep(0, { lowerBoundPoints: 0 });
            patchStep(0, { upperBoundPoints: -120 });
            patchStep(1, { lowerBoundPoints: 40 });
            patchStep(1, { upperBoundPoints: 80 });
            patchStep(2, { lowerBoundPoints: 80 });
            patchStep(2, { upperBoundPoints: 100 });
            translateStub.mockReturnValue('invalid points');

            expect(comp.gradingForm().valid()).toBe(false);
            expect(comp.invalidGradeStepsMessage()).toBe('invalid points');
        });

        it('should validate invalid grading scale with non-unique grade names', () => {
            comp.gradeStepsModel.update((model) => ({ ...model, gradeType: GradeType.GRADE }));
            patchStep(1, { gradeName: 'Fail' });
            translateStub.mockReturnValue('non-unique grade names');

            expect(comp.gradingForm().valid()).toBe(false);
            expect(comp.invalidGradeStepsMessage()).toBe('non-unique grade names');
        });

        it('should validate invalid grading scale with unset first passing grade', () => {
            comp.gradeStepsModel.update((model) => ({ ...model, gradeType: GradeType.GRADE }));
            comp.firstPassingGrade.set(undefined);
            translateStub.mockReturnValue('unset first passing grade');

            expect(comp.gradingForm().valid()).toBe(false);
            expect(comp.invalidGradeStepsMessage()).toBe('unset first passing grade');
        });

        it('should validate invalid grading scale with invalid bonus points', () => {
            patchStep(0, { gradeName: '-2' });
            comp.gradeStepsModel.update((model) => ({ ...model, gradeType: GradeType.BONUS }));
            translateStub.mockReturnValue('invalid bonus points');

            expect(comp.gradingForm().valid()).toBe(false);
            expect(comp.invalidGradeStepsMessage()).toBe('invalid bonus points');
        });

        it('should validate invalid grading scale without strictly ascending bonus points', () => {
            patchStep(0, { gradeName: '0' });
            patchStep(1, { gradeName: '2' });
            patchStep(2, { gradeName: '1' });
            comp.gradeStepsModel.update((model) => ({ ...model, gradeType: GradeType.BONUS }));
            translateStub.mockReturnValue('descending bonus points');

            expect(comp.gradingForm().valid()).toBe(false);
            expect(comp.invalidGradeStepsMessage()).toBe('descending bonus points');
        });

        it('should validate invalid grading scale with invalid adjacency', () => {
            const invalidGradeStep: GradeStep = {
                gradeName: 'Grade',
                isPassingGrade: false,
                lowerBoundInclusive: true,
                lowerBoundPercentage: 0,
                upperBoundInclusive: false,
                upperBoundPercentage: 30,
            };
            translateStub.mockReturnValue('invalid adjacency');
            vi.spyOn(gradingService, 'sortGradeSteps').mockReturnValue([invalidGradeStep, gradeStep2, gradeStep3]);

            expect(comp.gradingForm().valid()).toBe(false);
            expect(comp.invalidGradeStepsMessage()).toBe('invalid adjacency');
        });

        it('should validate invalid grading scale with invalid first grade step', () => {
            const invalidFirstGradeStep: GradeStep = {
                gradeName: 'Name',
                isPassingGrade: false,
                lowerBoundInclusive: true,
                lowerBoundPercentage: 20,
                upperBoundInclusive: false,
                upperBoundPercentage: 40,
            };
            vi.spyOn(gradingService, 'sortGradeSteps').mockReturnValue([invalidFirstGradeStep, gradeStep2, gradeStep3]);
            patchStep(0, { lowerBoundPercentage: 10 });
            translateStub.mockReturnValue('invalid first grade step');

            expect(comp.gradingForm().valid()).toBe(false);
            expect(comp.invalidGradeStepsMessage()).toBe('invalid first grade step');
        });

        it('should validate grading scale with basic presentations and invalid presentationScore', () => {
            comp.presentationsConfig.set({ presentationType: PresentationType.BASIC });
            comp.course.set({ presentationScore: 0 } as Course);
            translateStub.mockReturnValue('invalid presentations number');

            expect(comp.validPresentationsConfig()).toBe(false);
            expect(comp.presentationsConfigErrorMessage()).toBe('invalid presentations number');
        });

        it('should validate grading scale with graded presentations and invalid presentationsWeight', () => {
            comp.presentationsConfig.set({ presentationType: PresentationType.GRADED, presentationsNumber: 2, presentationsWeight: 128 });
            translateStub.mockReturnValue('invalid presentations weight');

            expect(comp.validPresentationsConfig()).toBe(false);
            expect(comp.presentationsConfigErrorMessage()).toBe('invalid presentations weight');
        });

        it('should validate grading scale with graded presentations and invalid presentationsNumber', () => {
            comp.presentationsConfig.set({ presentationType: PresentationType.GRADED, presentationsNumber: 0, presentationsWeight: 20 });
            translateStub.mockReturnValue('invalid presentations number');

            expect(comp.validPresentationsConfig()).toBe(false);
            expect(comp.presentationsConfigErrorMessage()).toBe('invalid presentations number');
        });

        it('should detect that max points are valid', () => {
            comp.maxPoints.set(100);

            expect(comp.maxPointsValid()).toBe(true);
        });

        it('should set points correctly', () => {
            const testGradeStep = cloneDeep(gradeStep1);
            testGradeStep.lowerBoundPoints = undefined;

            comp.setPoints(testGradeStep, true);

            expect(testGradeStep.lowerBoundPoints).toBeUndefined();

            comp.maxPoints.set(100);

            comp.setPoints(testGradeStep, true);

            expect(testGradeStep.lowerBoundPoints).toBe(0);

            comp.setPoints(testGradeStep, false);

            expect(testGradeStep.upperBoundPoints).toBe(40);
        });

        it('should set percentages correctly', () => {
            comp.maxPoints.set(100);
            const testGradeStep = cloneDeep(gradeStep2);
            testGradeStep.lowerBoundPoints = 40;
            testGradeStep.upperBoundPoints = 80;

            comp.setPercentage(testGradeStep, true);
            comp.setPercentage(testGradeStep, false);

            expect(testGradeStep.lowerBoundPercentage).toBe(40);
            expect(testGradeStep.upperBoundPercentage).toBe(80);
        });

        it('should set all grade step points correctly', () => {
            comp.maxPoints.set(100);

            comp.onChangeMaxPoints(100);

            expect(comp.gradingScale.gradeSteps[0].lowerBoundPoints).toBe(0);
            expect(comp.gradingScale.gradeSteps[0].upperBoundPoints).toBe(40);
            expect(comp.gradingScale.gradeSteps[1].lowerBoundPoints).toBe(40);
            expect(comp.gradingScale.gradeSteps[1].upperBoundPoints).toBe(80);
            expect(comp.gradingScale.gradeSteps[2].lowerBoundPoints).toBe(80);
            expect(comp.gradingScale.gradeSteps[2].upperBoundPoints).toBe(100);

            comp.onChangeMaxPoints(-10);

            expect(comp.gradingScale.gradeSteps[0].lowerBoundPoints).toBeUndefined();
            expect(comp.gradingScale.gradeSteps[0].upperBoundPoints).toBeUndefined();
            expect(comp.gradingScale.gradeSteps[1].lowerBoundPoints).toBeUndefined();
            expect(comp.gradingScale.gradeSteps[1].upperBoundPoints).toBeUndefined();
            expect(comp.gradingScale.gradeSteps[2].lowerBoundPoints).toBeUndefined();
            expect(comp.gradingScale.gradeSteps[2].upperBoundPoints).toBeUndefined();
        });

        const csvColumnsGrade = 'gradeName,lowerBoundPercentage,upperBoundPercentage,isPassingGrade';

        it('should read no grade steps from csv file without data', async () => {
            const event = { target: { files: [csvColumnsGrade] } } as unknown as Event;
            await comp.onCSVFileSelect(event);

            expect(comp.gradingScale.gradeSteps).toHaveLength(0);
        });

        it('should read grade steps from csv file', async () => {
            const csvData = `gradeName,lowerBoundPercentage,upperBoundPercentage,isPassingGrade\n4.0,0,50,FALSE\n3.0,50,100,TRUE`;
            const blob = new Blob([csvData], { type: 'text/csv' });
            const event = { target: { files: [new File([blob], 'test.csv')] } } as unknown as Event;

            await comp.onCSVFileSelect(event);

            expect(comp.gradingScale.gradeSteps).toHaveLength(2);
            expect(comp.gradingScale.gradeType).toBe(GradeType.GRADE);
        });

        it('should read bonus steps from csv file', async () => {
            const csvData = `bonusPoints,lowerBoundPercentage,upperBoundPercentage\n0,0,50\n1,50,100`;
            const blob = new Blob([csvData], { type: 'text/csv' });
            const event = { target: { files: [new File([blob], 'test.csv')] } } as unknown as Event;

            await comp.onCSVFileSelect(event);

            expect(comp.gradingScale.gradeSteps).toHaveLength(2);
            expect(comp.gradingScale.gradeType).toBe(GradeType.BONUS);
        });

        it('should export grading steps to csv', () => {
            comp.gradeStepsModel.update((model) => ({ ...model, gradeType: GradeType.GRADE }));
            comp.gradeStepsModel.update((model) => ({ ...model, gradeSteps: cloneDeep(gradeSteps) }));

            comp.exportGradingStepsToCsv();

            expect(downloadCsv).toHaveBeenCalled();
        });

        it('should convert grade step to csv row for GRADE type', () => {
            comp.gradeStepsModel.update((model) => ({ ...model, gradeType: GradeType.GRADE }));
            const row = comp.convertToCsvRow(gradeStep1);

            expect(row.gradeName).toBe('Fail');
            expect(row.lowerBoundPercentage).toBe(0);
            expect(row.upperBoundPercentage).toBe(40);
            expect(row.isPassingGrade).toBe(false);
        });

        it('should convert grade step to csv row for BONUS type', () => {
            comp.gradeStepsModel.update((model) => ({ ...model, gradeType: GradeType.BONUS }));
            const bonusStep = { ...gradeStep1, gradeName: '0' };
            const row = comp.convertToCsvRow(bonusStep);

            expect(row.bonusPoints).toBe('0');
            expect(row.lowerBoundPercentage).toBe(0);
            expect(row.upperBoundPercentage).toBe(40);
            expect(row.isPassingGrade).toBeUndefined();
        });
    });

    describe('interval view mode functionality', () => {
        beforeEach(() => setupComponent(true));

        afterEach(() => {
            vi.restoreAllMocks();
        });

        function validateGradeStepBounds(actualGradeStepRow: GradeStep, percentageLowerBound: number, percentageUpperBound: number, maxPoints: number) {
            expect(actualGradeStepRow.lowerBoundPercentage).toBe(percentageLowerBound);
            expect(actualGradeStepRow.upperBoundPercentage).toBe(percentageUpperBound);

            const multiplier = maxPoints / 100;
            expect(actualGradeStepRow.lowerBoundPoints).toBe(percentageLowerBound * multiplier);
            expect(actualGradeStepRow.upperBoundPoints).toBe(percentageUpperBound * multiplier);
        }

        it('should generate default grading scale with max points set', () => {
            const maxPoints = 200;
            comp.maxPoints.set(maxPoints);
            comp.onChangeMaxPoints(maxPoints);

            comp.generateDefaultGradingScale();

            comp.gradingScale.gradeSteps.forEach((gradeStep) => {
                expect(comp.getPointsInterval(gradeStep)).toBe(gradeStep.upperBoundPoints! - gradeStep.lowerBoundPoints!);
            });
        });

        it('should delete grade step in interval mode', () => {
            comp.setViewMode(GradingViewMode.INTERVAL);
            comp.gradeStepsModel.update((model) => ({ ...model, gradeSteps: cloneDeep(intervalGradeSteps) }));
            const maxPoints = 200;
            comp.maxPoints.set(maxPoints);
            comp.onChangeMaxPoints(maxPoints);

            comp.deleteGradeStep(1);

            expect(comp.gradingScale.gradeSteps).toHaveLength(3);

            validateGradeStepBounds(comp.gradingScale.gradeSteps[0], 0, 40, maxPoints);
            validateGradeStepBounds(comp.gradingScale.gradeSteps[1], 40, 75, maxPoints);
            validateGradeStepBounds(comp.gradingScale.gradeSteps[2], 75, 175, maxPoints);
        });

        it('should create grade step in interval mode', () => {
            comp.setViewMode(GradingViewMode.INTERVAL);
            comp.gradeStepsModel.update((model) => ({ ...model, gradeSteps: cloneDeep(intervalGradeSteps) }));
            comp.lowerBoundInclusivity = true;

            comp.createGradeStep();

            expect(comp.gradingScale.gradeSteps).toHaveLength(5);

            const newGradeStep = comp.gradingScale.gradeSteps[3];
            expect(newGradeStep.id).toBeUndefined();
            expect(newGradeStep.gradeName).toBe('');
            expect(newGradeStep.lowerBoundPercentage).toBe(100);
            expect(newGradeStep.upperBoundPercentage).toBe(100);
            expect(newGradeStep.isPassingGrade).toBe(true);
            expect(newGradeStep.lowerBoundInclusive).toBe(true);
            expect(newGradeStep.upperBoundInclusive).toBe(false);

            expect(comp.getPercentageInterval(newGradeStep)).toBe(0);
        });

        it('should set all grade step percentage intervals correctly', () => {
            comp.gradeStepsModel.update((model) => ({ ...model, gradeSteps: cloneDeep(intervalGradeSteps) }));

            expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[0])).toBe(40);
            expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[1])).toBe(25);
            expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[2])).toBe(35);
            expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[3])).toBe(100);
        });

        it('should set all grade step point intervals correctly', () => {
            comp.gradeStepsModel.update((model) => ({ ...model, gradeSteps: cloneDeep(intervalGradeSteps) }));

            expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[0])).toBeUndefined();
            expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[1])).toBeUndefined();
            expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[2])).toBeUndefined();
            expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[3])).toBeUndefined();

            const multiplier = 2;
            const maxPoints = multiplier * 100;
            comp.maxPoints.set(maxPoints);
            comp.onChangeMaxPoints(maxPoints);

            expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[0])).toBe(40 * multiplier);
            expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[1])).toBe(25 * multiplier);
            expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[2])).toBe(35 * multiplier);
            expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[3])).toBe(100 * multiplier);

            const negativeMaxPoints = -10;
            comp.maxPoints.set(negativeMaxPoints);
            comp.onChangeMaxPoints(negativeMaxPoints);

            expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[0])).toBeUndefined();
            expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[1])).toBeUndefined();
            expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[2])).toBeUndefined();
            expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[3])).toBeUndefined();
        });

        it('should cascade percentage interval increase', () => {
            comp.gradeStepsModel.update((model) => ({ ...model, gradeSteps: cloneDeep(intervalGradeSteps) }));
            const multiplier = 2;
            const maxPoints = multiplier * 100;
            comp.maxPoints.set(maxPoints);
            comp.onChangeMaxPoints(maxPoints);

            comp.setPercentageInterval(1, 50);

            expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[0])).toBe(40);
            expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[1])).toBe(50);
            expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[2])).toBe(35);
            expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[3])).toBe(100);

            expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[0])).toBe(40 * multiplier);
            expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[1])).toBe(50 * multiplier);
            expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[2])).toBe(35 * multiplier);
            expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[3])).toBe(100 * multiplier);

            validateGradeStepBounds(comp.gradingScale.gradeSteps[0], 0, 40, maxPoints);
            validateGradeStepBounds(comp.gradingScale.gradeSteps[1], 40, 90, maxPoints);
            validateGradeStepBounds(comp.gradingScale.gradeSteps[2], 90, 125, maxPoints);
            validateGradeStepBounds(comp.gradingScale.gradeSteps[3], 125, 225, maxPoints);
        });

        it('should cascade percentage interval decrease', () => {
            comp.gradeStepsModel.update((model) => ({ ...model, gradeSteps: cloneDeep(intervalGradeSteps) }));
            const multiplier = 2;
            const maxPoints = multiplier * 100;
            comp.maxPoints.set(maxPoints);
            comp.onChangeMaxPoints(maxPoints);

            comp.setPercentageInterval(1, 10);

            expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[0])).toBe(40);
            expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[1])).toBe(10);
            expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[2])).toBe(35);
            expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[3])).toBe(100);

            validateGradeStepBounds(comp.gradingScale.gradeSteps[0], 0, 40, maxPoints);
            validateGradeStepBounds(comp.gradingScale.gradeSteps[1], 40, 50, maxPoints);
            validateGradeStepBounds(comp.gradingScale.gradeSteps[2], 50, 85, maxPoints);
            validateGradeStepBounds(comp.gradingScale.gradeSteps[3], 85, 185, maxPoints);
        });

        it('should cascade points interval increase', () => {
            comp.gradeStepsModel.update((model) => ({ ...model, gradeSteps: cloneDeep(intervalGradeSteps) }));
            const multiplier = 2;
            const maxPoints = multiplier * 100;
            comp.maxPoints.set(maxPoints);
            comp.onChangeMaxPoints(maxPoints);

            comp.setPointsInterval(1, 50 * multiplier);

            expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[0])).toBe(40);
            expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[1])).toBe(50);
            expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[2])).toBe(35);
            expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[3])).toBe(100);

            validateGradeStepBounds(comp.gradingScale.gradeSteps[0], 0, 40, maxPoints);
            validateGradeStepBounds(comp.gradingScale.gradeSteps[1], 40, 90, maxPoints);
            validateGradeStepBounds(comp.gradingScale.gradeSteps[2], 90, 125, maxPoints);
            validateGradeStepBounds(comp.gradingScale.gradeSteps[3], 125, 225, maxPoints);
        });

        it('should cascade points interval decrease', () => {
            comp.gradeStepsModel.update((model) => ({ ...model, gradeSteps: cloneDeep(intervalGradeSteps) }));
            const multiplier = 2;
            const maxPoints = multiplier * 100;
            comp.maxPoints.set(maxPoints);
            comp.onChangeMaxPoints(maxPoints);

            comp.setPointsInterval(1, 10 * multiplier);

            expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[0])).toBe(40);
            expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[1])).toBe(10);
            expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[2])).toBe(35);
            expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[3])).toBe(100);

            validateGradeStepBounds(comp.gradingScale.gradeSteps[0], 0, 40, maxPoints);
            validateGradeStepBounds(comp.gradingScale.gradeSteps[1], 40, 50, maxPoints);
            validateGradeStepBounds(comp.gradingScale.gradeSteps[2], 50, 85, maxPoints);
            validateGradeStepBounds(comp.gradingScale.gradeSteps[3], 85, 185, maxPoints);
        });

        it('should throw on points interval change when max points are not defined', () => {
            comp.gradeStepsModel.update((model) => ({ ...model, gradeSteps: cloneDeep(intervalGradeSteps) }));
            expect(comp.maxPoints()).toBeUndefined();
            expect(() => {
                comp.setPointsInterval(0, 10);
            }).toThrow();
        });

        it('should prevent total percentage is less than 100 when only sticky step remains', () => {
            comp.setViewMode(GradingViewMode.INTERVAL);
            comp.gradeStepsModel.update((model) => ({ ...model, gradeSteps: cloneDeep(intervalGradeSteps) }));
            comp.deleteGradeStep(0);
            comp.deleteGradeStep(0);
            comp.deleteGradeStep(0);

            expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[0])).toBe(100);
        });

        it('should create the initial step when grading scale is empty in interval mode', () => {
            comp.setViewMode(GradingViewMode.INTERVAL);
            comp.gradingScale = new GradingScale();
            comp.lowerBoundInclusivity = true;

            comp.createGradeStep();

            expect(comp.gradingScale.gradeSteps[0].lowerBoundInclusive).toBe(true);
            expect(comp.gradingScale.gradeSteps[0].upperBoundInclusive).toBe(false);

            expect(comp.gradingScale.gradeSteps[0].lowerBoundPercentage).toBe(0);
            expect(comp.gradingScale.gradeSteps[0].upperBoundPercentage).toBe(100);

            expect(comp.gradingScale.gradeSteps[1].lowerBoundInclusive).toBe(true);
            expect(comp.gradingScale.gradeSteps[1].upperBoundInclusive).toBe(false);

            expect(comp.gradingScale.gradeSteps[1].lowerBoundPercentage).toBe(100);

            expect(comp.gradingScale.gradeSteps).toHaveLength(2);
        });

        it('should handle inclusivity setting when there are no grade steps', () => {
            comp.setViewMode(GradingViewMode.INTERVAL);
            comp.gradingScale = new GradingScale();

            comp.setInclusivity();

            expect(comp.gradingScale.gradeSteps).toHaveLength(0);
        });

        it('should set inclusivity to lower bound inclusive in interval mode', () => {
            comp.setViewMode(GradingViewMode.INTERVAL);
            comp.gradeStepsModel.update((model) => ({ ...model, gradeSteps: cloneDeep(intervalGradeSteps) }));
            comp.lowerBoundInclusivity = true;
            comp.setInclusivity();

            expect(comp.gradingScale.gradeSteps[0].lowerBoundInclusive).toBe(true);
            expect(comp.gradingScale.gradeSteps[0].upperBoundInclusive).toBe(false);

            expect(comp.gradingScale.gradeSteps[1].lowerBoundInclusive).toBe(true);
            expect(comp.gradingScale.gradeSteps[1].upperBoundInclusive).toBe(false);

            expect(comp.gradingScale.gradeSteps[2].lowerBoundInclusive).toBe(true);
            expect(comp.gradingScale.gradeSteps[2].upperBoundInclusive).toBe(false);

            expect(comp.gradingScale.gradeSteps[3].lowerBoundInclusive).toBe(true);
            expect(comp.gradingScale.gradeSteps[3].upperBoundInclusive).toBe(true);
        });

        it('should set inclusivity to upper bound inclusive in interval mode', () => {
            comp.setViewMode(GradingViewMode.INTERVAL);
            comp.gradeStepsModel.update((model) => ({ ...model, gradeSteps: cloneDeep(intervalGradeSteps) }));
            comp.lowerBoundInclusivity = false;
            comp.setInclusivity();

            expect(comp.gradingScale.gradeSteps[0].lowerBoundInclusive).toBe(true);
            expect(comp.gradingScale.gradeSteps[0].upperBoundInclusive).toBe(true);

            expect(comp.gradingScale.gradeSteps[1].lowerBoundInclusive).toBe(false);
            expect(comp.gradingScale.gradeSteps[1].upperBoundInclusive).toBe(true);

            expect(comp.gradingScale.gradeSteps[2].lowerBoundInclusive).toBe(false);
            expect(comp.gradingScale.gradeSteps[2].upperBoundInclusive).toBe(true);

            expect(comp.gradingScale.gradeSteps[3].lowerBoundInclusive).toBe(false);
            expect(comp.gradingScale.gradeSteps[3].upperBoundInclusive).toBe(true);
        });

        it('should not show grading steps above max points warning', () => {
            comp.setViewMode(GradingViewMode.INTERVAL);
            comp.gradeStepsModel.update((model) => ({ ...model, gradeSteps: cloneDeep(intervalGradeSteps) }));
            const result = comp.shouldShowGradingStepsAboveMaxPointsWarning();
            expect(result).toBe(false);
        });

        it('should show grading steps above max points warning for inclusive bound', () => {
            comp.setViewMode(GradingViewMode.INTERVAL);
            const gradeStep: GradeStep = {
                gradeName: 'Step',
                lowerBoundPercentage: 100,
                upperBoundPercentage: 101,
                lowerBoundInclusive: true,
                upperBoundInclusive: true,
                isPassingGrade: true,
            };
            comp.gradeStepsModel.update((model) => ({ ...model, gradeSteps: [gradeStep1, gradeStep2, gradeStep3, gradeStep, gradeStep4] }));

            const result = comp.shouldShowGradingStepsAboveMaxPointsWarning();
            expect(result).toBe(true);
        });

        it('should show grading steps above max points warning for exclusive bound', () => {
            comp.setViewMode(GradingViewMode.INTERVAL);
            const gradeStep: GradeStep = {
                gradeName: 'Step',
                lowerBoundPercentage: 100,
                upperBoundPercentage: 100,
                lowerBoundInclusive: true,
                upperBoundInclusive: false,
                isPassingGrade: true,
            };
            comp.gradeStepsModel.update((model) => ({ ...model, gradeSteps: [gradeStep1, gradeStep2, gradeStep3, gradeStep, gradeStep4] }));

            const result = comp.shouldShowGradingStepsAboveMaxPointsWarning();
            expect(result).toBe(true);
        });
    });
});
