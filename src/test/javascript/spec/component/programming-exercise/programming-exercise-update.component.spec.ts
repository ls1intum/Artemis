import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, UrlSegment } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { ArtemisTestModule } from '../../test.module';
import { ProgrammingExerciseUpdateComponent } from 'app/exercises/programming/manage/update/programming-exercise-update.component';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType, WindFile } from 'app/entities/programming-exercise.model';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/entities/course.model';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import {
    ProgrammingLanguageFeature,
    ProgrammingLanguageFeatureService,
} from 'app/exercises/programming/shared/service/programming-language-feature/programming-language-feature.service';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { CustomMinDirective } from 'app/shared/validators/custom-min-validator.directive';
import { ButtonComponent } from 'app/shared/components/button.component';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/exercises/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';
import { GradingInstructionsDetailsComponent } from 'app/exercises/shared/structured-grading-criterion/grading-instructions-details/grading-instructions-details.component';
import { CustomMaxDirective } from 'app/shared/validators/custom-max-validator.directive';
import { ProgrammingExerciseInstructionComponent } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instruction.component';
import { PresentationScoreComponent } from 'app/exercises/shared/presentation-score/presentation-score.component';
import { ProgrammingExerciseLifecycleComponent } from 'app/exercises/programming/shared/lifecycle/programming-exercise-lifecycle.component';
import { TeamConfigFormGroupComponent } from 'app/exercises/shared/team-config-form-group/team-config-form-group.component';
import { DifficultyPickerComponent } from 'app/exercises/shared/difficulty-picker/difficulty-picker.component';
import { RemoveAuxiliaryRepositoryButtonComponent } from 'app/exercises/programming/manage/update/remove-auxiliary-repository-button.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CheckboxControlValueAccessor, DefaultValueAccessor, NgForm, NgModel, NumberValueAccessor, SelectControlValueAccessor } from '@angular/forms';
import { IncludedInOverallScorePickerComponent } from 'app/exercises/shared/included-in-overall-score-picker/included-in-overall-score-picker.component';
import { CategorySelectorComponent } from 'app/shared/category-selector/category-selector.component';
import { AddAuxiliaryRepositoryButtonComponent } from 'app/exercises/programming/manage/update/add-auxiliary-repository-button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ProgrammingExercisePlansAndRepositoriesPreviewComponent } from 'app/exercises/programming/manage/update/programming-exercise-plans-and-repositories-preview.component';
import { TableEditableFieldComponent } from 'app/shared/table/table-editable-field.component';
import { RemoveKeysPipe } from 'app/shared/pipes/remove-keys.pipe';
import { SubmissionPolicyUpdateComponent } from 'app/exercises/shared/submission-policy/submission-policy-update.component';
import { LockRepositoryPolicy, SubmissionPenaltyPolicy } from 'app/entities/submission-policy.model';
import { OwlDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import '@angular/localize/init';
import { ModePickerComponent } from 'app/exercises/shared/mode-picker/mode-picker.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { NgbTooltipMocksModule } from '../../helpers/mocks/directive/ngbTooltipMocks.module';
import { NgbAlertsMocksModule } from '../../helpers/mocks/directive/ngbAlertsMocks.module';
import { CompetencySelectionComponent } from 'app/shared/competency-selection/competency-selection.component';
import { ProgrammingExerciseInformationComponent } from 'app/exercises/programming/manage/update/update-components/programming-exercise-information.component';
import { ProgrammingExerciseDifficultyComponent } from 'app/exercises/programming/manage/update/update-components/programming-exercise-difficulty.component';
import { ProgrammingExerciseLanguageComponent } from 'app/exercises/programming/manage/update/update-components/programming-exercise-language.component';
import { ProgrammingExerciseGradingComponent } from 'app/exercises/programming/manage/update/update-components/programming-exercise-grading.component';
import { ProgrammingExerciseProblemComponent } from 'app/exercises/programming/manage/update/update-components/programming-exercise-problem.component';
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { ExerciseUpdateNotificationComponent } from 'app/exercises/shared/exercise-update-notification/exercise-update-notification.component';
import { ExerciseUpdatePlagiarismComponent } from 'app/exercises/shared/plagiarism/exercise-update-plagiarism/exercise-update-plagiarism.component';
import * as Utils from 'app/exercises/shared/course-exercises/course-utils';
import { AuxiliaryRepository } from 'app/entities/programming-exercise-auxiliary-repository-model';
import { AlertService, AlertType } from 'app/core/util/alert.service';
import { FormStatusBarComponent } from 'app/forms/form-status-bar/form-status-bar.component';
import { FormFooterComponent } from 'app/forms/form-footer/form-footer.component';

describe('ProgrammingExerciseUpdateComponent', () => {
    const courseId = 1;
    const course = { id: courseId } as Course;

    let comp: ProgrammingExerciseUpdateComponent;
    let fixture: ComponentFixture<ProgrammingExerciseUpdateComponent>;
    let debugElement: DebugElement;
    let programmingExerciseService: ProgrammingExerciseService;
    let courseService: CourseManagementService;
    let exerciseGroupService: ExerciseGroupService;
    let programmingExerciseFeatureService: ProgrammingLanguageFeatureService;
    let alertService: AlertService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgxDatatableModule, OwlDateTimeModule, NgbTooltipMocksModule, NgbAlertsMocksModule],
            declarations: [
                ProgrammingExerciseUpdateComponent,
                // The following directives need to be imported raw because the SCA tests heavily rely on the UI interaction with the native inputs.
                // Mocking that interaction defeats the purpose of interacting with the UI in the first place.
                NgForm,
                NgModel,
                CheckboxControlValueAccessor,
                DefaultValueAccessor,
                SelectControlValueAccessor,
                NumberValueAccessor,
                MockComponent(HelpIconComponent),
                MockComponent(ProgrammingExercisePlansAndRepositoriesPreviewComponent),
                MockComponent(TableEditableFieldComponent),
                MockComponent(RemoveAuxiliaryRepositoryButtonComponent),
                MockComponent(CategorySelectorComponent),
                MockComponent(AddAuxiliaryRepositoryButtonComponent),
                MockComponent(DifficultyPickerComponent),
                MockComponent(TeamConfigFormGroupComponent),
                MockComponent(ProgrammingExerciseLifecycleComponent),
                MockComponent(IncludedInOverallScorePickerComponent),
                MockComponent(SubmissionPolicyUpdateComponent),
                MockComponent(PresentationScoreComponent),
                MockComponent(ProgrammingExerciseInstructionComponent),
                MockComponent(ProgrammingExerciseEditableInstructionComponent),
                MockComponent(GradingInstructionsDetailsComponent),
                MockComponent(ButtonComponent),
                MockComponent(CompetencySelectionComponent),
                MockComponent(ProgrammingExerciseInformationComponent),
                MockComponent(ProgrammingExerciseDifficultyComponent),
                MockComponent(ProgrammingExerciseLanguageComponent),
                MockComponent(ProgrammingExerciseGradingComponent),
                MockComponent(ProgrammingExerciseProblemComponent),
                MockComponent(DocumentationButtonComponent),
                MockComponent(FormStatusBarComponent),
                MockComponent(FormFooterComponent),
                MockPipe(RemoveKeysPipe),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(CustomMinDirective),
                MockDirective(CustomMaxDirective),
                MockDirective(TranslateDirective),
                MockComponent(ModePickerComponent),
                MockComponent(ExerciseUpdateNotificationComponent),
                MockComponent(ExerciseUpdatePlagiarismComponent),
            ],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({}) },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: AlertService, useValue: { addAlert: () => {} } },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseUpdateComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                programmingExerciseService = debugElement.injector.get(ProgrammingExerciseService);
                courseService = debugElement.injector.get(CourseManagementService);
                exerciseGroupService = debugElement.injector.get(ExerciseGroupService);
                programmingExerciseFeatureService = debugElement.injector.get(ProgrammingLanguageFeatureService);
                alertService = debugElement.injector.get(AlertService);
            });
    });

    describe('save', () => {
        it('should call update service on save for existing entity', fakeAsync(() => {
            // GIVEN
            const entity = new ProgrammingExercise(new Course(), undefined);
            entity.id = 123;
            entity.releaseDate = dayjs(); // We will get a warning if we do not set a release date
            jest.spyOn(programmingExerciseService, 'update').mockReturnValue(of(new HttpResponse({ body: entity })));
            comp.programmingExercise = entity;
            comp.backupExercise = {} as ProgrammingExercise;
            comp.programmingExercise.course = course;
            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(programmingExerciseService.update).toHaveBeenCalledWith(entity, {});
            expect(comp.isSaving).toBeFalse();
        }));

        it('should call create service on save for new entity', fakeAsync(() => {
            // GIVEN
            const entity = new ProgrammingExercise(undefined, undefined);
            entity.releaseDate = dayjs(); // We will get a warning if we do not set a release date
            jest.spyOn(programmingExerciseService, 'automaticSetup').mockReturnValue(
                of(
                    new HttpResponse({
                        body: {
                            ...entity,
                            id: 2,
                        },
                    }),
                ),
            );
            comp.programmingExercise = entity;
            comp.backupExercise = {} as ProgrammingExercise;
            comp.programmingExercise.course = course;
            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(programmingExerciseService.automaticSetup).toHaveBeenCalledWith(entity);
            expect(comp.isSaving).toBeFalse();
        }));

        it('should trim the exercise title before saving', fakeAsync(() => {
            // GIVEN
            const entity = new ProgrammingExercise(undefined, undefined);
            entity.releaseDate = dayjs(); // We will get a warning if we do not set a release date
            entity.title = 'My Exercise   ';
            jest.spyOn(programmingExerciseService, 'automaticSetup').mockReturnValue(
                of(
                    new HttpResponse({
                        body: {
                            ...entity,
                            id: 1,
                        },
                    }),
                ),
            );
            comp.programmingExercise = entity;
            comp.backupExercise = {} as ProgrammingExercise;
            comp.programmingExercise.course = course;

            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(programmingExerciseService.automaticSetup).toHaveBeenCalledWith(entity);
            expect(entity.title).toBe('My Exercise');
        }));

        it('should fail on error', async () => {
            // GIVEN
            const entity = new ProgrammingExercise(undefined, undefined);
            entity.id = 1;
            jest.spyOn(programmingExerciseService, 'update').mockReturnValue(
                throwError(
                    new HttpResponse({
                        headers: new HttpHeaders({ 'X-artemisApp-alert': 'error-message' }),
                    }),
                ),
            );
            const alertSpy = jest.spyOn(alertService, 'addAlert');
            comp.programmingExercise = entity;
            comp.backupExercise = {} as ProgrammingExercise;
            comp.programmingExercise.course = course;
            // WHEN
            comp.save();

            // THEN
            expect(comp.isSaving).toBeFalse();
            expect(alertSpy).toHaveBeenCalledWith({ type: AlertType.DANGER, message: 'error-message', disableTranslation: true });
        });
    });

    describe('exam mode', () => {
        const examId = 1;
        const exerciseGroupId = 1;
        const exerciseGroup = new ExerciseGroup();
        exerciseGroup.id = exerciseGroupId;
        const expectedExamProgrammingExercise = new ProgrammingExercise(undefined, undefined);
        expectedExamProgrammingExercise.exerciseGroup = exerciseGroup;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId, examId, exerciseGroupId });
            route.url = of([{ path: 'new' } as UrlSegment]);
            route.data = of({ programmingExercise: new ProgrammingExercise(undefined, undefined) });
        });

        it('should be in exam mode after onInit', fakeAsync(() => {
            // GIVEN
            jest.spyOn(exerciseGroupService, 'find').mockReturnValue(of(new HttpResponse({ body: exerciseGroup })));
            jest.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature').mockReturnValue(getProgrammingLanguageFeature(ProgrammingLanguage.JAVA));

            // WHEN
            comp.ngOnInit();
            tick(); // simulate async

            // THEN
            expect(exerciseGroupService.find).toHaveBeenCalledWith(courseId, examId, exerciseGroupId);
            expect(comp.isSaving).toBeFalse();
            expect(comp.programmingExercise).toStrictEqual(expectedExamProgrammingExercise);
            expect(comp.isExamMode).toBeTrue();
        }));
    });

    describe('course mode', () => {
        const expectedProgrammingExercise = new ProgrammingExercise(undefined, undefined);
        expectedProgrammingExercise.course = course;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId });
            route.url = of([{ path: 'new' } as UrlSegment]);
            route.data = of({ programmingExercise: new ProgrammingExercise(undefined, undefined) });
        });

        it('should not be in exam mode after onInit', fakeAsync(() => {
            // GIVEN
            jest.spyOn(courseService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
            jest.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature').mockReturnValue(getProgrammingLanguageFeature(ProgrammingLanguage.JAVA));

            // WHEN
            comp.ngOnInit();
            tick(); // simulate async

            // THEN
            expect(courseService.find).toHaveBeenCalledWith(courseId);
            expect(comp.isSaving).toBeFalse();
            expect(comp.programmingExercise).toStrictEqual(expectedProgrammingExercise);
            expect(comp.isExamMode).toBeFalse();
        }));
    });

    describe('default programming language', () => {
        it.each([true, false])(
            'should set default programming language',
            fakeAsync((isExamExercise: boolean) => {
                // SETUP
                const route = TestBed.inject(ActivatedRoute);
                route.url = of([{ path: 'new' } as UrlSegment]);
                if (isExamExercise) {
                    const examId = 1;
                    const exerciseGroupId = 1;
                    const exerciseGroup = new ExerciseGroup();
                    exerciseGroup.id = exerciseGroupId;
                    exerciseGroup.exam = { id: examId, course };
                    route.params = of({ courseId, examId, exerciseGroupId });
                    route.data = of({ programmingExercise: new ProgrammingExercise(undefined, exerciseGroup) });
                    jest.spyOn(exerciseGroupService, 'find').mockReturnValue(of(new HttpResponse({ body: exerciseGroup })));
                } else {
                    route.params = of({ courseId });
                    route.data = of({ programmingExercise: new ProgrammingExercise(course, undefined) });
                }
                jest.spyOn(courseService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
                // GIVEN
                const testProgrammingLanguage = ProgrammingLanguage.SWIFT;
                expect(new ProgrammingExercise(undefined, undefined).programmingLanguage).not.toBe(testProgrammingLanguage);
                course.defaultProgrammingLanguage = testProgrammingLanguage;
                jest.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature').mockReturnValue(getProgrammingLanguageFeature(testProgrammingLanguage));

                // WHEN
                comp.ngOnInit();
                tick();

                // THEN
                expect(comp.programmingExercise.programmingLanguage).toBe(testProgrammingLanguage);
            }),
        );
    });

    describe('programming language change and features', () => {
        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId });
            route.url = of([{ path: 'new' } as UrlSegment]);
            route.data = of({ programmingExercise: new ProgrammingExercise(undefined, undefined) });
            jest.spyOn(courseService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
            jest.spyOn(programmingExerciseFeatureService, 'supportsProgrammingLanguage').mockReturnValue(true);

            const getFeaturesStub = jest.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature');
            getFeaturesStub.mockImplementation((language: ProgrammingLanguage) => getProgrammingLanguageFeature(language));
        });

        it('should reset sca settings if new programming language does not support sca', fakeAsync(() => {
            comp.ngOnInit();
            fixture.detectChanges();
            tick();

            // Activate sca
            comp.programmingExercise.staticCodeAnalysisEnabled = true;
            tick();

            comp.programmingExercise.maxStaticCodeAnalysisPenalty = 50;
            tick();

            expect(comp.programmingExercise.staticCodeAnalysisEnabled).toBeTrue();
            expect(comp.programmingExercise.maxStaticCodeAnalysisPenalty).toBe(50);

            // Switch to another programming language not supporting sca
            comp.onProgrammingLanguageChange(ProgrammingLanguage.HASKELL);
            tick();

            expect(comp.programmingExercise.staticCodeAnalysisEnabled).toBeFalse();
            expect(comp.programmingExercise.maxStaticCodeAnalysisPenalty).toBeUndefined();
            expect(comp.programmingExercise.programmingLanguage).toBe(ProgrammingLanguage.HASKELL);
        }));

        it('should activate SCA for Swift', fakeAsync(() => {
            // WHEN
            fixture.detectChanges();
            tick();
            comp.onProgrammingLanguageChange(ProgrammingLanguage.SWIFT);

            // THEN
            expect(courseService.find).toHaveBeenCalledWith(courseId);
            expect(comp.selectedProgrammingLanguage).toBe(ProgrammingLanguage.SWIFT);
            expect(comp.staticCodeAnalysisAllowed).toBeTrue();
            expect(comp.packageNamePattern).toBe(comp.appNamePatternForSwift);
        }));

        it('should activate SCA for C', fakeAsync(() => {
            // WHEN
            fixture.detectChanges();
            tick();
            comp.onProgrammingLanguageChange(ProgrammingLanguage.C);
            comp.onProjectTypeChange(ProjectType.GCC);

            // THEN
            expect(courseService.find).toHaveBeenCalledWith(courseId);
            expect(comp.selectedProgrammingLanguage).toBe(ProgrammingLanguage.C);
            expect(comp.selectedProjectType).toBe(ProjectType.GCC);
            expect(comp.staticCodeAnalysisAllowed).toBeTrue();
        }));

        it('should activate SCA for Java', fakeAsync(() => {
            // WHEN
            fixture.detectChanges();
            tick();
            comp.onProgrammingLanguageChange(ProgrammingLanguage.JAVA);

            // THEN
            expect(comp.selectedProgrammingLanguage).toBe(ProgrammingLanguage.JAVA);
            expect(comp.staticCodeAnalysisAllowed).toBeTrue();
            expect(comp.packageNamePattern).toBe(comp.packageNamePatternForJavaKotlin);
        }));

        it('should deactivate SCA for C (FACT)', fakeAsync(() => {
            // WHEN
            fixture.detectChanges();
            tick();
            comp.onProgrammingLanguageChange(ProgrammingLanguage.C);
            comp.onProjectTypeChange(ProjectType.FACT);

            // THEN
            expect(comp.selectedProgrammingLanguage).toBe(ProgrammingLanguage.C);
            expect(comp.selectedProjectType).toBe(ProjectType.FACT);
            expect(comp.programmingExercise.staticCodeAnalysisEnabled).toBeFalse();
            expect(comp.programmingExercise.maxStaticCodeAnalysisPenalty).toBeUndefined();
        }));

        it('should clear custom build definition on programming language change', fakeAsync(() => {
            // WHEN
            fixture.detectChanges();
            comp.programmingExercise.buildPlanConfiguration = 'some custom build definition';
            comp.programmingExercise.windFile = new WindFile();
            tick();
            comp.onProgrammingLanguageChange(ProgrammingLanguage.C);
            comp.onProjectTypeChange(ProjectType.FACT);

            // THEN
            expect(comp.programmingExercise.buildPlanConfiguration).toBeUndefined();
            expect(comp.programmingExercise.windFile).toBeUndefined();
        }));
    });

    describe('import with static code analysis', () => {
        let route: ActivatedRoute;

        beforeEach(() => {
            jest.spyOn(courseService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
            jest.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature').mockReturnValue(getProgrammingLanguageFeature(ProgrammingLanguage.JAVA));

            route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId });
            route.url = of([{ path: 'import' } as UrlSegment]);
        });

        it('should correctly import into an exam exercise', fakeAsync(() => {
            const examId = 1;
            const exerciseGroupId = 1;
            const exerciseGroup = new ExerciseGroup();
            exerciseGroup.id = exerciseGroupId;
            exerciseGroup.exam = { id: examId, course };
            const programmingExercise = new ProgrammingExercise(undefined, exerciseGroup);
            jest.spyOn(exerciseGroupService, 'find').mockReturnValue(of(new HttpResponse({ body: exerciseGroup })));
            route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId, examId, exerciseGroupId });
            route.data = of({ programmingExercise });

            comp.ngOnInit();
            tick();
            expect(comp.programmingExercise.exerciseGroup).toBe(exerciseGroup);
            expect(comp.programmingExercise.course).toBeUndefined();
            expect(comp.isImportFromExistingExercise).toBeTrue();
            expect(comp.isExamMode).toBeTrue();
        }));

        it('should reset dates, id and project key', fakeAsync(() => {
            const programmingExercise = getProgrammingExerciseForImport();

            route.data = of({ programmingExercise });
            const getFeaturesStub = jest.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature');
            getFeaturesStub.mockImplementation((language: ProgrammingLanguage) => getProgrammingLanguageFeature(language));
            comp.ngOnInit();
            fixture.detectChanges();
            tick();
            expect(comp.isImportFromFile).toBeFalse();
            expect(comp.isImportFromExistingExercise).toBeTrue();

            verifyImport(programmingExercise);
        }));

        it.each([
            [true, 80, ProjectType.PLAIN_MAVEN],
            [false, undefined, ProjectType.PLAIN_GRADLE],
        ])(
            'should activate recreate build plans and update template when sca changes',
            fakeAsync((scaActivatedOriginal: boolean, maxPenalty: number | undefined, projectType: ProjectType) => {
                const newMaxPenalty = 50;
                const programmingExercise = new ProgrammingExercise(undefined, undefined);
                programmingExercise.programmingLanguage = ProgrammingLanguage.JAVA;
                programmingExercise.projectType = projectType;
                programmingExercise.staticCodeAnalysisEnabled = scaActivatedOriginal;
                programmingExercise.maxStaticCodeAnalysisPenalty = maxPenalty;
                route.data = of({ programmingExercise });
                comp.ngOnInit();
                fixture.detectChanges();
                tick();

                expect(comp.isImportFromExistingExercise).toBeTrue();
                expect(comp.originalStaticCodeAnalysisEnabled).toBe(scaActivatedOriginal);
                expect(comp.programmingExercise.staticCodeAnalysisEnabled).toBe(scaActivatedOriginal);
                expect(comp.programmingExercise.maxStaticCodeAnalysisPenalty).toBe(maxPenalty);
                expect(comp.programmingExercise).toBe(programmingExercise);
                expect(courseService.find).toHaveBeenCalledWith(courseId);

                // Only available for Maven
                if (projectType === ProjectType.PLAIN_MAVEN) {
                    // Needed to trigger setting of update template since we can't use UI components.
                    comp.programmingExercise.staticCodeAnalysisEnabled = !scaActivatedOriginal;
                    comp.onStaticCodeAnalysisChanged();

                    expect(comp.importOptions.updateTemplate).toBeTrue();

                    comp.programmingExercise.staticCodeAnalysisEnabled = !scaActivatedOriginal;
                    comp.onStaticCodeAnalysisChanged();
                }

                comp.programmingExercise.staticCodeAnalysisEnabled = !scaActivatedOriginal;
                comp.onStaticCodeAnalysisChanged();

                if (!scaActivatedOriginal) {
                    comp.programmingExercise.maxStaticCodeAnalysisPenalty = newMaxPenalty;
                }

                // Recreate build plan and template update should be automatically selected
                expect(comp.programmingExercise.staticCodeAnalysisEnabled).toBe(!scaActivatedOriginal);
                expect(comp.programmingExercise.maxStaticCodeAnalysisPenalty).toBe(scaActivatedOriginal ? undefined : newMaxPenalty);
                expect(comp.importOptions.recreateBuildPlans).toBeTrue();
                expect(comp.importOptions.updateTemplate).toBeTrue();

                comp.importOptions.recreateBuildPlans = !comp.importOptions.recreateBuildPlans;
                comp.onRecreateBuildPlanOrUpdateTemplateChange();
                tick();

                // SCA should revert to the state of the original exercise, maxPenalty will revert to undefined
                expect(comp.programmingExercise.staticCodeAnalysisEnabled).toBe(comp.originalStaticCodeAnalysisEnabled);
                expect(comp.programmingExercise.maxStaticCodeAnalysisPenalty).toBeUndefined();
            }),
        );

        it('should load exercise categories on import', () => {
            const programmingExercise = getProgrammingExerciseForImport();
            route.data = of({ programmingExercise });
            const loadExerciseCategoriesSpy = jest.spyOn(Utils, 'loadCourseExerciseCategories');

            comp.ngOnInit();

            expect(loadExerciseCategoriesSpy).toHaveBeenCalledOnce();
        });
    });

    describe('import from file', () => {
        let route: ActivatedRoute;

        beforeEach(() => {
            route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId });

            route.url = of([{ path: 'import-from-file' } as UrlSegment]);
        });

        it('should reset dates, id, project key and store zipFile', fakeAsync(() => {
            const programmingExercise = getProgrammingExerciseForImport();

            route.data = of({ programmingExercise });
            const getFeaturesStub = jest.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature');
            getFeaturesStub.mockImplementation((language: ProgrammingLanguage) => getProgrammingLanguageFeature(language));
            comp.ngOnInit();
            fixture.detectChanges();
            tick();
            expect(comp.isImportFromFile).toBeTrue();
            expect(comp.isImportFromExistingExercise).toBeFalse();

            verifyImport(programmingExercise);
        }));

        it('should call import-from-file from service on import for entity from file', fakeAsync(() => {
            // GIVEN
            const entity = new ProgrammingExercise(undefined, undefined);
            entity.zipFileForImport = new File([''], 'test.zip');
            comp.isImportFromFile = true;
            entity.releaseDate = dayjs(); // We will get a warning if we do not set a release date
            jest.spyOn(programmingExerciseService, 'importFromFile').mockReturnValue(
                of(
                    new HttpResponse({
                        body: {
                            ...entity,
                            id: 2,
                        },
                    }),
                ),
            );
            comp.programmingExercise = entity;
            comp.backupExercise = {} as ProgrammingExercise;
            comp.programmingExercise.course = course;
            comp.courseId = course.id!;
            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(programmingExerciseService.importFromFile).toHaveBeenCalledWith(entity, 1);
            expect(comp.isSaving).toBeFalse();
        }));
    });

    describe('input error validation', () => {
        beforeEach(() => {
            // GIVEN
            const entity = new ProgrammingExercise(new Course(), undefined);
            entity.id = 123;
            comp.programmingExercise = entity;
            comp.programmingExercise.course = course;
        });

        it('find validation errors for undefined input values', () => {
            // invalid input
            comp.programmingExercise.title = undefined;
            comp.programmingExercise.shortName = undefined;
            comp.programmingExercise.maxPoints = undefined;
            comp.programmingExercise.bonusPoints = undefined;
            comp.programmingExercise.packageName = undefined;

            const reasons: any[] = comp.getInvalidReasons();
            expect(reasons).toHaveLength(5);
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.title.undefined',
                translateValues: {},
            });
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.shortName.undefined',
                translateValues: {},
            });
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.points.undefined',
                translateValues: {},
            });
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.bonusPoints.undefined',
                translateValues: {},
            });
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.packageName.undefined',
                translateValues: {},
            });
        });

        it('find validation errors for empty input strings', () => {
            // invalid input
            comp.programmingExercise.title = '';
            comp.programmingExercise.shortName = '';
            comp.programmingExercise.packageName = '';

            const reasons: any[] = comp.getInvalidReasons();
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.title.undefined',
                translateValues: {},
            });
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.shortName.undefined',
                translateValues: {},
            });
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.packageName.undefined',
                translateValues: {},
            });
        });

        it('find validation errors for input values not matching the pattern', () => {
            comp.programmingExercise.title = '%§"$"§';
            comp.programmingExercise.shortName = '123';
            comp.programmingExercise.maxPoints = 0;
            comp.programmingExercise.bonusPoints = -1;
            comp.programmingExercise.staticCodeAnalysisEnabled = true;
            comp.programmingExercise.maxStaticCodeAnalysisPenalty = -1;

            const reasons: any[] = comp.getInvalidReasons();
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.title.pattern',
                translateValues: {},
            });
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.shortName.pattern',
                translateValues: {},
            });
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.points.customMin',
                translateValues: {},
            });
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.bonusPoints.customMin',
                translateValues: {},
            });
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.maxPenalty.pattern',
                translateValues: {},
            });
        });

        it('find validation errors for package name not matching the pattern', () => {
            comp.programmingExercise.packageName = 'de.tum.in';
            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.SWIFT;
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.exercise.form.packageName.pattern.SWIFT',
                translateValues: {},
            });

            comp.programmingExercise.packageName = 'de/';
            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.JAVA;
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.exercise.form.packageName.pattern.JAVA',
                translateValues: {},
            });

            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.KOTLIN;
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.exercise.form.packageName.pattern.KOTLIN',
                translateValues: {},
            });
        });

        it('Check that no package name related validation error occurs for language C', () => {
            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.C;
            expect(comp.getInvalidReasons()).not.toContainEqual({
                translateKey: 'artemisApp.exercise.form.packageName.undefined',
                translateValues: {},
            });
        });

        it('Check that no package name related validation error occurs for language Empty', () => {
            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.EMPTY;
            expect(comp.getInvalidReasons()).not.toContainEqual({
                translateKey: 'artemisApp.exercise.form.packageName.undefined',
                translateValues: {},
            });
        });

        it('Check that no package name related validation error occurs for language Python', () => {
            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.PYTHON;
            expect(comp.getInvalidReasons()).not.toContainEqual({
                translateKey: 'artemisApp.exercise.form.packageName.undefined',
                translateValues: {},
            });
        });

        it('Check that no package name related validation error occurs for language Assembler', () => {
            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.ASSEMBLER;
            expect(comp.getInvalidReasons()).not.toContainEqual({
                translateKey: 'artemisApp.exercise.form.packageName.undefined',
                translateValues: {},
            });
        });

        it('Check that no package name related validation error occurs for language OCAML', () => {
            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.OCAML;
            expect(comp.getInvalidReasons()).not.toContainEqual({
                translateKey: 'artemisApp.exercise.form.packageName.undefined',
                translateValues: {},
            });
        });

        it('Check that no package name related validation error occurs for language VHDL', () => {
            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.VHDL;
            expect(comp.getInvalidReasons()).not.toContainEqual({
                translateKey: 'artemisApp.exercise.form.packageName.undefined',
                translateValues: {},
            });
        });

        it('find validation errors for invalid auxiliary repositories', () => {
            comp.auxiliaryRepositoriesValid = false;
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.programmingExercise.auxiliaryRepository.error',
                translateValues: {},
            });
        });

        it('find validation errors for invalid ide selection', () => {
            comp.programmingExercise.allowOnlineEditor = false;
            comp.programmingExercise.allowOfflineIde = false;
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.programmingExercise.allowOnlineEditor.alert',
                translateValues: {},
            });
        });

        it('should update AuxiliaryRepository checkout directory', () => {
            const auxiliaryRepository = new AuxiliaryRepository();
            auxiliaryRepository.checkoutDirectory = 'aux';
            auxiliaryRepository.name = 'aux';
            auxiliaryRepository.repositoryUri = 'auxurl';
            comp.programmingExercise.auxiliaryRepositories = [auxiliaryRepository];
            const returned = comp.updateCheckoutDirectory(auxiliaryRepository)('new-value');
            expect(auxiliaryRepository.checkoutDirectory).toBe('new-value');
            expect(returned).toBe('new-value');
        });

        it('should update AuxiliaryRepository name', () => {
            const auxiliaryRepository = new AuxiliaryRepository();
            auxiliaryRepository.checkoutDirectory = 'aux';
            auxiliaryRepository.name = 'aux';
            auxiliaryRepository.repositoryUri = 'auxurl';
            comp.programmingExercise.auxiliaryRepositories = [auxiliaryRepository];
            const returned = comp.updateRepositoryName(auxiliaryRepository)('new-value');
            expect(auxiliaryRepository.name).toBe('new-value');
            expect(returned).toBe('new-value');
        });

        it('should find no validation errors for valid input', () => {
            comp.programmingExercise.title = 'New title';
            comp.programmingExercise.shortName = 'home2';
            comp.programmingExercise.maxPoints = 10;
            comp.programmingExercise.bonusPoints = 0;
            comp.programmingExercise.staticCodeAnalysisEnabled = true;
            comp.programmingExercise.maxStaticCodeAnalysisPenalty = 60;
            comp.programmingExercise.allowOfflineIde = true;
            comp.programmingExercise.allowOnlineEditor = false;
            comp.programmingExercise.packageName = 'de.tum.in';
            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.JAVA;

            expect(comp.getInvalidReasons()).toBeEmpty();
        });

        it('should find validation errors for invalid submission limit value', () => {
            comp.programmingExercise.submissionPolicy = new LockRepositoryPolicy();
            comp.programmingExercise.submissionPolicy.submissionLimit = undefined;
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.programmingExercise.submissionPolicy.submissionLimitWarning.required',
                translateValues: {},
            });

            const patternViolatingValues = [0, 501, 30.3];
            for (const value of patternViolatingValues) {
                comp.programmingExercise.submissionPolicy.submissionLimit = value;
                expect(comp.getInvalidReasons()).toContainEqual({
                    translateKey: 'artemisApp.programmingExercise.submissionPolicy.submissionLimitWarning.pattern',
                    translateValues: {},
                });
            }
        });

        it('should find validation errors invalid submission exceeding penalty', () => {
            comp.programmingExercise.submissionPolicy = new SubmissionPenaltyPolicy();

            comp.programmingExercise.submissionPolicy.exceedingPenalty = undefined;
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.programmingExercise.submissionPolicy.submissionPenalty.penaltyInputFieldValidationWarning.required',
                translateValues: {},
            });

            comp.programmingExercise.submissionPolicy.exceedingPenalty = 0;
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.programmingExercise.submissionPolicy.submissionPenalty.penaltyInputFieldValidationWarning.pattern',
                translateValues: {},
            });
        });

        it('should find no package name related validation error for languages that do not need a package name', () => {
            for (const programmingLanguage of [ProgrammingLanguage.C, ProgrammingLanguage.EMPTY, ProgrammingLanguage.PYTHON]) {
                comp.programmingExercise.programmingLanguage = programmingLanguage;
                expect(comp.getInvalidReasons()).not.toContainEqual({
                    translateKey: 'artemisApp.exercise.form.packageName.undefined',
                    translateValues: {},
                });
            }
        });
    });

    describe('disable features based on selected language and project type', () => {
        beforeEach(() => {
            const entity = new ProgrammingExercise(new Course(), undefined);
            entity.id = 123;
            entity.channelName = 'notificationText';
            comp.programmingExercise = entity;
            comp.programmingExercise.course = course;
            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.JAVA;

            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId });
            route.url = of([{ path: 'edit' } as UrlSegment]);
            route.data = of({ programmingExercise: comp.programmingExercise });
        });

        it('should disable checkboxes for certain options of existing exercise', fakeAsync(() => {
            const getFeaturesStub = jest.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature');
            getFeaturesStub.mockImplementation((language: ProgrammingLanguage) => getProgrammingLanguageFeature(language));

            fixture.detectChanges();
            tick();

            expect(comp.programmingExercise.staticCodeAnalysisEnabled).toBeFalse();
            expect(comp.programmingExercise.testwiseCoverageEnabled).toBeFalse();
        }));

        it('should disable options for java dejagnu project type and re-enable them after changing back to maven or gradle', fakeAsync(() => {
            const getFeaturesStub = jest.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature');
            getFeaturesStub.mockImplementation((language: ProgrammingLanguage) => getProgrammingLanguageFeature(language));
            comp.selectedProjectType = ProjectType.MAVEN_BLACKBOX;
            expect(comp.sequentialTestRunsAllowed).toBeFalse();
            expect(comp.testwiseCoverageAnalysisSupported).toBeFalse();

            comp.selectedProjectType = ProjectType.MAVEN_MAVEN;
            expect(comp.sequentialTestRunsAllowed).toBeTrue();
            expect(comp.testwiseCoverageAnalysisSupported).toBeTrue();

            comp.selectedProjectType = ProjectType.MAVEN_BLACKBOX;
            expect(comp.sequentialTestRunsAllowed).toBeFalse();
            expect(comp.testwiseCoverageAnalysisSupported).toBeFalse();

            comp.selectedProjectType = ProjectType.GRADLE_GRADLE;
            expect(comp.sequentialTestRunsAllowed).toBeTrue();
            expect(comp.testwiseCoverageAnalysisSupported).toBeTrue();
        }));
    });

    it('should return the exercise creation config', fakeAsync(() => {
        const route = TestBed.inject(ActivatedRoute);
        route.params = of({ courseId });
        route.url = of([{ path: 'new' } as UrlSegment]);
        route.data = of({ programmingExercise: new ProgrammingExercise(undefined, undefined) });

        const getFeaturesStub = jest.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature');
        getFeaturesStub.mockImplementation((language: ProgrammingLanguage) => getProgrammingLanguageFeature(language));

        fixture.detectChanges();
        tick();

        const problemStepInputs = comp.getProgrammingExerciseCreationConfig();
        expect(problemStepInputs).not.toBeNull();
    }));

    it('stores withdependenices when changed', fakeAsync(() => {
        const route = TestBed.inject(ActivatedRoute);
        route.params = of({ courseId });
        route.url = of([{ path: 'new' } as UrlSegment]);
        route.data = of({ programmingExercise: new ProgrammingExercise(undefined, undefined) });

        const getFeaturesStub = jest.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature');
        getFeaturesStub.mockImplementation((language: ProgrammingLanguage) => getProgrammingLanguageFeature(language));

        fixture.detectChanges();
        tick();

        expect(comp.withDependencies).toBeFalse();
        comp.onWithDependenciesChanged(true);
        expect(comp.withDependencies).toBeTrue();
    }));

    it('stores updated categories', fakeAsync(() => {
        const route = TestBed.inject(ActivatedRoute);
        route.params = of({ courseId });
        route.url = of([{ path: 'new' } as UrlSegment]);
        route.data = of({ programmingExercise: new ProgrammingExercise(undefined, undefined) });

        const getFeaturesStub = jest.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature');
        getFeaturesStub.mockImplementation((language: ProgrammingLanguage) => getProgrammingLanguageFeature(language));

        fixture.detectChanges();
        tick();

        const categories = [new ExerciseCategory()];
        expect(comp.exerciseCategories).toBeUndefined();
        comp.updateCategories(categories);
        expect(comp.exerciseCategories).toBe(categories);
    }));

    it('should validate form sections', () => {
        const calculateFormValidSectionsSpy = jest.spyOn(comp, 'calculateFormStatusSections');
        comp.programmingExercise = new ProgrammingExercise(undefined, undefined);
        comp.exerciseInfoComponent = { formValidChanges: new Subject(), formValid: true } as ProgrammingExerciseInformationComponent;
        comp.exerciseDifficultyComponent = {
            teamConfigComponent: {
                formValidChanges: new Subject(),
                formValid: true,
            },
        } as ProgrammingExerciseDifficultyComponent;
        comp.exerciseLanguageComponent = { formValidChanges: new Subject(), formValid: true } as ProgrammingExerciseLanguageComponent;
        comp.exerciseGradingComponent = { formValidChanges: new Subject(), formValid: true } as ProgrammingExerciseGradingComponent;
        comp.exercisePlagiarismComponent = { formValidChanges: new Subject(), formValid: true } as ExerciseUpdatePlagiarismComponent;

        comp.ngAfterViewInit();
        expect(comp.inputFieldSubscriptions).toHaveLength(5);
        comp.calculateFormStatusSections();

        for (const section of comp.formStatusSections) {
            expect(section.valid).toBeTrue();
        }

        comp.exerciseInfoComponent.formValid = false;
        comp.exerciseInfoComponent.formValidChanges.next(false);

        expect(comp.formStatusSections[0].valid).toBeFalse();

        comp.exerciseLanguageComponent.formValidChanges.next(false);
        comp.exerciseGradingComponent.formValidChanges.next(false);
        comp.exerciseDifficultyComponent.teamConfigComponent.formValidChanges.next(false);
        comp.exercisePlagiarismComponent.formValidChanges.next(false);

        expect(calculateFormValidSectionsSpy).toHaveBeenCalledTimes(6);

        comp.programmingExercise.allowOfflineIde = false;
        comp.programmingExercise.allowOnlineEditor = false;
        comp.calculateFormStatusSections();
        expect(comp.formStatusSections[1].valid).toBeFalse();
        comp.programmingExercise.allowOnlineEditor = true;
        comp.calculateFormStatusSections();
        expect(comp.formStatusSections[1].valid).toBeTrue();

        comp.ngOnDestroy();

        for (const subscription of comp.inputFieldSubscriptions) {
            expect(subscription?.closed ?? true).toBeTrue();
        }
    });

    function verifyImport(importedProgrammingExercise: ProgrammingExercise) {
        expect(comp.programmingExercise.projectKey).toBeUndefined();
        expect(comp.programmingExercise.id).toBeUndefined();
        expect(comp.programmingExercise.dueDate).toBeUndefined();
        expect(comp.programmingExercise.releaseDate).toBeUndefined();
        expect(comp.programmingExercise.startDate).toBeUndefined();
        expect(comp.programmingExercise.assessmentDueDate).toBeUndefined();
        expect(comp.programmingExercise.exampleSolutionPublicationDate).toBeUndefined();
        expect(comp.programmingExercise.zipFileForImport?.name).toBe('test.zip');
        expect(comp.programmingExercise.allowComplaintsForAutomaticAssessments).toBeFalse();
        expect(comp.programmingExercise.allowManualFeedbackRequests).toBeFalse();
        expect(comp.programmingExercise.allowOfflineIde).toBeTrue();
        expect(comp.programmingExercise.allowOnlineEditor).toBeTrue();
        expect(comp.programmingExercise.programmingLanguage).toBe(ProgrammingLanguage.JAVA);
        expect(comp.programmingExercise.projectType).toBe(ProjectType.PLAIN_MAVEN);
        // allow manual feedback requests and complaints for automatic assessments should be set to false because we reset all dates and hence they can only be false
        expect(comp.programmingExercise.allowManualFeedbackRequests).toBeFalse();
        expect(comp.programmingExercise.allowComplaintsForAutomaticAssessments).toBeFalse();
        // name and short name should also be imported
        expect(comp.programmingExercise.title).toEqual(importedProgrammingExercise.title);
        expect(comp.programmingExercise.shortName).toEqual(importedProgrammingExercise.shortName);
    }
});

const getProgrammingExerciseForImport = () => {
    const programmingExercise = new ProgrammingExercise(undefined, undefined);
    programmingExercise.programmingLanguage = ProgrammingLanguage.JAVA;
    programmingExercise.projectType = ProjectType.PLAIN_MAVEN;
    programmingExercise.dueDate = dayjs();
    programmingExercise.releaseDate = dayjs();
    programmingExercise.startDate = dayjs();
    programmingExercise.projectKey = 'projectKey';

    programmingExercise.assessmentDueDate = dayjs();
    programmingExercise.exampleSolutionPublicationDate = dayjs();
    programmingExercise.programmingLanguage = ProgrammingLanguage.JAVA;
    programmingExercise.zipFileForImport = new File([''], 'test.zip');
    programmingExercise.allowOfflineIde = true;
    programmingExercise.allowOnlineEditor = true;
    programmingExercise.allowComplaintsForAutomaticAssessments = true;
    programmingExercise.allowManualFeedbackRequests = true;

    history.pushState({ programmingExerciseForImportFromFile: programmingExercise }, '');

    programmingExercise.shortName = 'shortName';
    programmingExercise.title = 'title';

    return programmingExercise;
};

const getProgrammingLanguageFeature = (programmingLanguage: ProgrammingLanguage) => {
    switch (programmingLanguage) {
        case ProgrammingLanguage.SWIFT:
            return {
                programmingLanguage: ProgrammingLanguage.SWIFT,
                sequentialTestRuns: false,
                staticCodeAnalysis: true,
                plagiarismCheckSupported: false,
                packageNameRequired: true,
                checkoutSolutionRepositoryAllowed: false,
                projectTypes: [ProjectType.PLAIN, ProjectType.XCODE],
                testwiseCoverageAnalysisSupported: false,
                auxiliaryRepositoriesSupported: true,
            } as ProgrammingLanguageFeature;
        case ProgrammingLanguage.JAVA:
            return {
                programmingLanguage: ProgrammingLanguage.JAVA,
                sequentialTestRuns: true,
                staticCodeAnalysis: true,
                plagiarismCheckSupported: true,
                packageNameRequired: true,
                checkoutSolutionRepositoryAllowed: true,
                projectTypes: [ProjectType.PLAIN_MAVEN, ProjectType.MAVEN_MAVEN],
                testwiseCoverageAnalysisSupported: true,
                auxiliaryRepositoriesSupported: true,
            } as ProgrammingLanguageFeature;
        case ProgrammingLanguage.HASKELL:
            return {
                programmingLanguage: ProgrammingLanguage.HASKELL,
                sequentialTestRuns: false,
                staticCodeAnalysis: false,
                plagiarismCheckSupported: false,
                packageNameRequired: false,
                checkoutSolutionRepositoryAllowed: true,
                testwiseCoverageAnalysisSupported: false,
                auxiliaryRepositoriesSupported: true,
            } as ProgrammingLanguageFeature;
        case ProgrammingLanguage.C:
            return {
                programmingLanguage: ProgrammingLanguage.C,
                sequentialTestRuns: false,
                staticCodeAnalysis: true,
                plagiarismCheckSupported: true,
                packageNameRequired: false,
                checkoutSolutionRepositoryAllowed: true,
                projectTypes: [ProjectType.FACT, ProjectType.GCC],
                testwiseCoverageAnalysisSupported: false,
                auxiliaryRepositoriesSupported: true,
            } as ProgrammingLanguageFeature;
        default:
            throw new Error();
    }
};
