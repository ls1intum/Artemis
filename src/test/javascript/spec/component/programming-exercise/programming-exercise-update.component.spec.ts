import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute, UrlSegment } from '@angular/router';
import { of } from 'rxjs';
import dayjs from 'dayjs';
import { ArtemisTestModule } from '../../test.module';
import { ProgrammingExerciseUpdateComponent } from 'app/exercises/programming/manage/update/programming-exercise-update.component';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import {
    ProgrammingLanguageFeature,
    ProgrammingLanguageFeatureService,
} from 'app/exercises/programming/shared/service/programming-language-feature/programming-language-feature.service';
import { MockProvider, MockComponent, MockDirective, MockPipe, MockModule } from 'ng-mocks';
import { NgbAlert, NgbModal, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseUpdateWarningService } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.service';
import { FileService } from 'app/shared/http/file.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ProgrammingExerciseSimulationService } from 'app/exercises/programming/manage/services/programming-exercise-simulation.service';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { CheckboxControlValueAccessor, DefaultValueAccessor, NgForm, NgModel, NumberValueAccessor, SelectControlValueAccessor } from '@angular/forms';
import { RemoveKeysPipe } from 'app/shared/pipes/remove-keys.pipe';
import { ProgrammingExercisePlansAndRepositoriesPreviewComponent } from 'app/exercises/programming/manage/update/programming-exercise-plans-and-repositories-preview.component';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { TableEditableFieldComponent } from 'app/shared/table/table-editable-field.component';
import { RemoveAuxiliaryRepositoryButtonComponent } from 'app/exercises/programming/manage/update/remove-auxiliary-repository-button.component';
import { CategorySelectorComponent } from 'app/shared/category-selector/category-selector.component';
import { AddAuxiliaryRepositoryButtonComponent } from 'app/exercises/programming/manage/update/add-auxiliary-repository-button.component';
import { DifficultyPickerComponent } from 'app/exercises/shared/difficulty-picker/difficulty-picker.component';
import { TeamConfigFormGroupComponent } from 'app/exercises/shared/team-config-form-group/team-config-form-group.component';
import { ProgrammingExerciseLifecycleComponent } from 'app/exercises/programming/shared/lifecycle/programming-exercise-lifecycle.component';
import { IncludedInOverallScorePickerComponent } from 'app/exercises/shared/included-in-overall-score-picker/included-in-overall-score-picker.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { PresentationScoreComponent } from 'app/exercises/shared/presentation-score/presentation-score.component';
import { ProgrammingExerciseInstructionComponent } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instruction.component';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/exercises/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';
import { GradingInstructionsDetailsComponent } from 'app/exercises/shared/structured-grading-criterion/grading-instructions-details/grading-instructions-details.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { CustomMinDirective } from 'app/shared/validators/custom-min-validator.directive';
import { CustomMaxDirective } from 'app/shared/validators/custom-max-validator.directive';
import { MockFileService } from '../../helpers/mocks/service/mock-file.service';
import { MockProfileService } from '../../helpers/mocks/service/mock-profile.service';
import { MockCourseManagementService } from '../../helpers/mocks/service/mock-course-management.service';

describe('ProgrammingExercise Management Update Component', () => {
    const courseId = 1;
    const course = { id: courseId } as Course;

    let comp: ProgrammingExerciseUpdateComponent;
    let fixture: ComponentFixture<ProgrammingExerciseUpdateComponent>;
    let debugElement: DebugElement;
    let programmingExerciseService: ProgrammingExerciseService;
    let courseService: CourseManagementService;
    let exerciseGroupService: ExerciseGroupService;
    let programmingExerciseFeatureService: ProgrammingLanguageFeatureService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(NgxDatatableModule)],
            declarations: [
                ProgrammingExerciseUpdateComponent,
                MockComponent(AlertComponent),
                MockComponent(AlertErrorComponent),
                MockComponent(HelpIconComponent),
                NgModel,
                CheckboxControlValueAccessor,
                DefaultValueAccessor,
                SelectControlValueAccessor,
                NumberValueAccessor,
                MockPipe(RemoveKeysPipe),
                MockDirective(TranslateDirective),
                MockComponent(ProgrammingExercisePlansAndRepositoriesPreviewComponent),
                MockComponent(TableEditableFieldComponent),
                MockComponent(RemoveAuxiliaryRepositoryButtonComponent),
                MockDirective(NgbTooltip),
                MockComponent(CategorySelectorComponent),
                MockComponent(AddAuxiliaryRepositoryButtonComponent),
                MockComponent(DifficultyPickerComponent),
                MockComponent(TeamConfigFormGroupComponent),
                MockComponent(ProgrammingExerciseLifecycleComponent),
                MockComponent(IncludedInOverallScorePickerComponent),
                MockPipe(ArtemisTranslatePipe),
                NgForm,
                MockComponent(PresentationScoreComponent),
                MockComponent(NgbAlert),
                MockComponent(ProgrammingExerciseInstructionComponent),
                MockComponent(ProgrammingExerciseEditableInstructionComponent),
                MockComponent(GradingInstructionsDetailsComponent),
                MockComponent(ButtonComponent),
                MockDirective(CustomMinDirective),
                MockDirective(CustomMaxDirective),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: FileService, useClass: MockFileService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: CourseManagementService, useClass: MockCourseManagementService },
                MockProvider(ProgrammingExerciseService),
                MockProvider(NgbModal),
                MockProvider(ExerciseUpdateWarningService),
                MockProvider(ProgrammingExerciseSimulationService),
                MockProvider(ExerciseGroupService),
                MockProvider(ProgrammingLanguageFeatureService),
                MockProvider(ArtemisNavigationUtilService),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseUpdateComponent);
        comp = fixture.componentInstance;
        debugElement = fixture.debugElement;
        programmingExerciseService = debugElement.injector.get(ProgrammingExerciseService);
        courseService = debugElement.injector.get(CourseManagementService);
        exerciseGroupService = debugElement.injector.get(ExerciseGroupService);
        programmingExerciseFeatureService = debugElement.injector.get(ProgrammingLanguageFeatureService);
    });

    describe('save', () => {
        it('Should call update service on save for existing entity', fakeAsync(() => {
            // GIVEN
            const entity = new ProgrammingExercise(new Course(), undefined);
            entity.id = 123;
            entity.releaseDate = dayjs(); // We will get a warning if we do not set a release date
            jest.spyOn(programmingExerciseService, 'update').mockReturnValue(of(new HttpResponse({ body: entity })));
            comp.programmingExercise = entity;
            comp.programmingExercise.course = course;
            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(programmingExerciseService.update).toHaveBeenCalledWith(entity, {});
            expect(comp.isSaving).toEqual(false);
        }));

        it('Should call create service on save for new entity', fakeAsync(() => {
            // GIVEN
            const entity = new ProgrammingExercise(undefined, undefined);
            entity.releaseDate = dayjs(); // We will get a warning if we do not set a release date
            jest.spyOn(programmingExerciseService, 'automaticSetup').mockReturnValue(of(new HttpResponse({ body: entity })));
            comp.programmingExercise = entity;
            comp.programmingExercise.course = course;
            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(programmingExerciseService.automaticSetup).toHaveBeenCalledWith(entity);
            expect(comp.isSaving).toEqual(false);
        }));

        it('Should trim the exercise title before saving', fakeAsync(() => {
            // GIVEN
            const entity = new ProgrammingExercise(undefined, undefined);
            entity.releaseDate = dayjs(); // We will get a warning if we do not set a release date
            entity.title = 'My Exercise   ';
            jest.spyOn(programmingExerciseService, 'automaticSetup').mockReturnValue(of(new HttpResponse({ body: entity })));
            comp.programmingExercise = entity;
            comp.programmingExercise.course = course;

            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(programmingExerciseService.automaticSetup).toHaveBeenCalledWith(entity);
            expect(entity.title).toEqual('My Exercise');
        }));
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

        it('Should be in exam mode after onInit', fakeAsync(() => {
            // GIVEN
            jest.spyOn(exerciseGroupService, 'find').mockReturnValue(of(new HttpResponse({ body: exerciseGroup })));
            jest.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature').mockReturnValue(getProgrammingLanguageFeature(ProgrammingLanguage.JAVA));

            // WHEN
            comp.ngOnInit();
            tick(); // simulate async

            // THEN
            expect(exerciseGroupService.find).toHaveBeenCalledWith(courseId, examId, exerciseGroupId);
            expect(comp.isSaving).toEqual(false);
            expect(comp.programmingExercise).toEqual(expectedExamProgrammingExercise);
            expect(comp.isExamMode).toBeTruthy();
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

        it('Should not be in exam mode after onInit', fakeAsync(() => {
            // GIVEN
            jest.spyOn(courseService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
            jest.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature').mockReturnValue(getProgrammingLanguageFeature(ProgrammingLanguage.JAVA));

            // WHEN
            comp.ngOnInit();
            tick(); // simulate async

            // THEN
            expect(courseService.find).toHaveBeenCalledWith(courseId);
            expect(comp.isSaving).toEqual(false);
            expect(comp.programmingExercise).toEqual(expectedProgrammingExercise);
            expect(comp.isExamMode).toBeFalsy();
        }));
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

        it('Should reset sca settings if new programming language does not support sca', fakeAsync(() => {
            comp.ngOnInit();
            fixture.detectChanges();
            tick();

            // Activate sca
            let scaCheckbox = fixture.nativeElement.querySelector('#field_staticCodeAnalysisEnabled');
            scaCheckbox.click();
            scaCheckbox.dispatchEvent(new Event('input'));
            fixture.detectChanges();
            tick();

            // Set a max penalty
            const maxPenaltyInput = fixture.nativeElement.querySelector('#field_maxPenalty');
            maxPenaltyInput.value = 50;
            maxPenaltyInput.dispatchEvent(new Event('input'));
            fixture.detectChanges();
            tick();

            expect(scaCheckbox.checked).toBeTruthy();
            expect(comp.programmingExercise.staticCodeAnalysisEnabled).toBeTruthy();
            expect(comp.programmingExercise.maxStaticCodeAnalysisPenalty).toBe(50);

            // Switch to another programming language not supporting sca
            const languageInput = fixture.nativeElement.querySelector('#field_programmingLanguage');
            languageInput.value = ProgrammingLanguage.HASKELL;
            languageInput.dispatchEvent(new Event('change'));
            fixture.detectChanges();
            tick();
            scaCheckbox = fixture.nativeElement.querySelector('#field_staticCodeAnalysisEnabled');

            expect(scaCheckbox).toBeFalsy();
            expect(comp.programmingExercise.staticCodeAnalysisEnabled).toBeFalsy();
            expect(comp.programmingExercise.maxStaticCodeAnalysisPenalty).toBeUndefined();
            expect(comp.programmingExercise.programmingLanguage).toBe(ProgrammingLanguage.HASKELL);
        }));

        it('Should activate SCA for Swift', fakeAsync(() => {
            // WHEN
            fixture.detectChanges();
            tick();
            comp.onProgrammingLanguageChange(ProgrammingLanguage.SWIFT);

            // THEN
            expect(courseService.find).toHaveBeenCalledWith(courseId);
            expect(comp.selectedProgrammingLanguage).toEqual(ProgrammingLanguage.SWIFT);
            expect(comp.staticCodeAnalysisAllowed).toEqual(true);
            expect(comp.packageNamePattern).toEqual(comp.appNamePatternForSwift);
        }));

        it('Should activate SCA for C', fakeAsync(() => {
            // WHEN
            fixture.detectChanges();
            tick();
            comp.onProgrammingLanguageChange(ProgrammingLanguage.C);

            // THEN
            expect(courseService.find).toHaveBeenCalledWith(courseId);
            expect(comp.selectedProgrammingLanguage).toEqual(ProgrammingLanguage.C);
            expect(comp.staticCodeAnalysisAllowed).toEqual(true);
        }));

        it('Should activate SCA for Java', fakeAsync(() => {
            // WHEN
            fixture.detectChanges();
            tick();
            comp.onProgrammingLanguageChange(ProgrammingLanguage.JAVA);

            // THEN
            expect(comp.selectedProgrammingLanguage).toEqual(ProgrammingLanguage.JAVA);
            expect(comp.staticCodeAnalysisAllowed).toEqual(true);
            expect(comp.packageNamePattern).toEqual(comp.packageNamePatternForJavaKotlin);
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

        it.each([
            [true, 80],
            [false, undefined],
        ])(
            'Should activate recreate build plans and update template when sca changes',
            fakeAsync((scaActivatedOriginal: boolean, maxPenalty: number | undefined) => {
                const newMaxPenalty = 50;
                const programmingExercise = new ProgrammingExercise(undefined, undefined);
                programmingExercise.programmingLanguage = ProgrammingLanguage.JAVA;
                programmingExercise.staticCodeAnalysisEnabled = scaActivatedOriginal;
                programmingExercise.maxStaticCodeAnalysisPenalty = maxPenalty;
                route.data = of({ programmingExercise });
                comp.ngOnInit();
                fixture.detectChanges();
                tick();

                let scaCheckbox = fixture.nativeElement.querySelector('#field_staticCodeAnalysisEnabled');
                let maxPenaltyInput = fixture.nativeElement.querySelector('#field_maxPenalty');
                const recreateBuildPlanCheckbox = fixture.nativeElement.querySelector('#field_recreateBuildPlans');
                const updateTemplateCheckbox = fixture.nativeElement.querySelector('#field_updateTemplateFiles');

                expect(comp.isImport).toBeTruthy();
                expect(comp.originalStaticCodeAnalysisEnabled).toBe(scaActivatedOriginal);
                expect(comp.programmingExercise.staticCodeAnalysisEnabled).toBe(scaActivatedOriginal);
                expect(comp.programmingExercise.maxStaticCodeAnalysisPenalty).toBe(maxPenalty);
                expect(scaCheckbox.checked).toBe(scaActivatedOriginal);
                expect(!!maxPenaltyInput).toBe(scaActivatedOriginal);
                expect(recreateBuildPlanCheckbox.checked).toBeFalsy();
                expect(updateTemplateCheckbox.checked).toBeFalsy();
                expect(comp.programmingExercise).toEqual(programmingExercise);
                expect(courseService.find).toHaveBeenCalledWith(courseId);

                // Activate SCA and set a max penalty
                scaCheckbox.click();
                scaCheckbox.dispatchEvent(new Event('input'));
                fixture.detectChanges();
                tick();
                scaCheckbox = fixture.nativeElement.querySelector('#field_staticCodeAnalysisEnabled');

                // SCA penalty field disappears or appears after the sca checkbox click
                maxPenaltyInput = fixture.nativeElement.querySelector('#field_maxPenalty');
                if (scaActivatedOriginal) {
                    expect(maxPenaltyInput).toBeFalsy();
                } else {
                    maxPenaltyInput.value = newMaxPenalty;
                    maxPenaltyInput.dispatchEvent(new Event('input'));
                    fixture.detectChanges();
                    tick();
                }

                // Recreate build plan and template update should be automatically selected
                expect(scaCheckbox.checked).toBe(!scaActivatedOriginal);
                expect(comp.programmingExercise.staticCodeAnalysisEnabled).toBe(!scaActivatedOriginal);
                expect(comp.programmingExercise.maxStaticCodeAnalysisPenalty).toEqual(scaActivatedOriginal ? undefined : newMaxPenalty);
                expect(comp.recreateBuildPlans).toBeTruthy();
                expect(comp.updateTemplate).toBeTruthy();

                // Deactivate recreation of build plans
                recreateBuildPlanCheckbox.click();
                recreateBuildPlanCheckbox.dispatchEvent(new Event('input'));
                fixture.detectChanges();
                tick();

                // SCA should revert to the state of the original exercise, maxPenalty will revert to undefined
                expect(comp.programmingExercise.staticCodeAnalysisEnabled).toEqual(comp.originalStaticCodeAnalysisEnabled);
                expect(comp.programmingExercise.maxStaticCodeAnalysisPenalty).toBeUndefined();
            }),
        );
    });
});

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
            } as ProgrammingLanguageFeature;
        case ProgrammingLanguage.JAVA:
            return {
                programmingLanguage: ProgrammingLanguage.JAVA,
                sequentialTestRuns: true,
                staticCodeAnalysis: true,
                plagiarismCheckSupported: true,
                packageNameRequired: true,
                checkoutSolutionRepositoryAllowed: true,
                projectTypes: [ProjectType.ECLIPSE, ProjectType.MAVEN],
            } as ProgrammingLanguageFeature;
        case ProgrammingLanguage.HASKELL:
            return {
                programmingLanguage: ProgrammingLanguage.HASKELL,
                sequentialTestRuns: false,
                staticCodeAnalysis: false,
                plagiarismCheckSupported: false,
                packageNameRequired: false,
                checkoutSolutionRepositoryAllowed: true,
                projectTypes: [],
            } as ProgrammingLanguageFeature;
        case ProgrammingLanguage.C:
            return {
                programmingLanguage: ProgrammingLanguage.C,
                sequentialTestRuns: false,
                staticCodeAnalysis: true,
                plagiarismCheckSupported: true,
                packageNameRequired: false,
                checkoutSolutionRepositoryAllowed: true,
                projectTypes: [],
            } as ProgrammingLanguageFeature;
        default:
            throw new Error();
    }
};
