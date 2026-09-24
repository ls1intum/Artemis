import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { AlertService } from 'app/foundation/service/alert.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { ExerciseGroupImportResultDTO } from 'app/exam/shared/entities/exam-import-result.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { ExamExerciseImportComponent } from 'app/exam/manage/exams/exam-exercise-import/exam-exercise-import.component';
import { ExamImportPagingService } from 'app/exam/manage/exams/exam-import/exam-import-paging.service';
import { ExamImportComponent } from 'app/exam/manage/exams/exam-import/exam-import.component';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { DifficultyBadgeComponent } from 'app/exercise/exercise-headers/difficulty-badge/difficulty-badge.component';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { SortService } from 'app/foundation/service/sort.service';
import { SortByDirective } from 'app/foundation/sort/directive/sort-by.directive';
import { SortDirective } from 'app/foundation/sort/directive/sort.directive';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { Subject, of, throwError } from 'rxjs';
import { UMLDiagramType } from '@tumaet/apollon';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MODULE_FEATURE_TEXT } from 'app/app.constants';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

describe('Exam Import Component', () => {
    let component: ExamImportComponent;
    let fixture: ComponentFixture<ExamImportComponent>;
    let dialogRef: DynamicDialogRef;
    let dialogRefCloseSpy: ReturnType<typeof vi.fn>;
    let examManagementService: ExamManagementService;
    let alertService: AlertService;
    let profileService: ProfileService;
    let getProfileInfoStub: ReturnType<typeof vi.spyOn>;

    const exam1 = { id: 1 } as Exam;

    // Initializing one Exercise Group
    const exerciseGroup1 = { title: 'exerciseGroup1' } as ExerciseGroup;
    const modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, exerciseGroup1);
    modelingExercise.id = 1;
    modelingExercise.title = 'ModelingExercise';
    exerciseGroup1.exercises = [modelingExercise];
    const exam1WithExercises = { id: 1, exerciseGroups: [exerciseGroup1] } as Exam;

    beforeEach(async () => {
        dialogRefCloseSpy = vi.fn();
        dialogRef = {
            close: dialogRefCloseSpy,
            onClose: new Subject<any>(),
        } as unknown as DynamicDialogRef;

        await TestBed.configureTestingModule({
            imports: [
                FormsModule,
                FaIconComponent,
                ExamImportComponent,
                ExamExerciseImportComponent,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(SortByDirective),
                MockDirective(SortDirective),
                MockComponent(ButtonComponent),
                MockComponent(HelpIconComponent),
                MockComponent(DifficultyBadgeComponent),
            ],
            providers: [
                MockProvider(SortService),
                MockProvider(ExamImportPagingService, { search: () => of({ resultsOnPage: [], numberOfPages: 0 }) }),
                { provide: DynamicDialogRef, useValue: dialogRef },
                MockProvider(ExamManagementService),
                MockProvider(AlertService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                {
                    provide: ExerciseService,
                    useValue: { getExistingExerciseDetailsInCourse: () => of({ exerciseTitles: new Set<string>(), shortNames: new Set<string>() }) },
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamImportComponent);
        component = fixture.componentInstance;
        examManagementService = TestBed.inject(ExamManagementService);
        alertService = TestBed.inject(AlertService);

        profileService = TestBed.inject(ProfileService);

        getProfileInfoStub = vi.spyOn(profileService, 'getProfileInfo');
        getProfileInfoStub.mockReturnValue(of({ activeModuleFeatures: [MODULE_FEATURE_TEXT] }));
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should correctly open the exercise selection', () => {
        vi.spyOn(examManagementService, 'findWithExercisesAndWithoutCourseId').mockReturnValue(of(new HttpResponse({ body: exam1WithExercises })));
        component.openExerciseSelection(exam1);
        expect(component.exam()).toEqual(exam1WithExercises);
    });

    it('should correctly show an error for the exercise selection, if the server throws an error', () => {
        const error = new HttpErrorResponse({
            status: 400,
        });
        vi.spyOn(examManagementService, 'findWithExercisesAndWithoutCourseId').mockReturnValue(throwError(() => error));
        const alertSpy = vi.spyOn(alertService, 'error');
        component.openExerciseSelection(exam1);
        expect(component.exam()).toBeUndefined();
        expect(alertSpy).toHaveBeenCalledOnce();
    });

    it('should only perform input of exercise groups if prerequisites are met', () => {
        const importSpy = vi.spyOn(examManagementService, 'importExerciseGroup');
        const alertSpy = vi.spyOn(alertService, 'error');
        component.subsequentExerciseGroupSelection.set(false);
        component.performImportOfExerciseGroups();

        component.subsequentExerciseGroupSelection.set(true);
        component.exam.set(undefined);
        component.performImportOfExerciseGroups();

        component.exam.set(exam1WithExercises);
        component.targetExamId.set(undefined);
        component.performImportOfExerciseGroups();

        component.targetExamId.set(1);
        component.targetCourseId.set(undefined);
        component.performImportOfExerciseGroups();

        expect(importSpy).not.toHaveBeenCalled();
        expect(alertSpy).not.toHaveBeenCalled();
        expect(dialogRefCloseSpy).not.toHaveBeenCalled();
    });

    it('should perform import of exercise groups successfully', async () => {
        const importSpy = vi.spyOn(examManagementService, 'importExerciseGroup').mockReturnValue(of(new HttpResponse({ status: 200, body: { exerciseGroups: [exerciseGroup1] } })));
        const alertSpy = vi.spyOn(alertService, 'error');

        // The progress dialog resolves with the import result once the user dismisses it
        await performImport(importSpy, { resolve: new HttpResponse({ status: 200, body: { exerciseGroups: [exerciseGroup1] } }) });
        expect(alertSpy).not.toHaveBeenCalled();
        expect(dialogRefCloseSpy).toHaveBeenCalledOnce();
        expect(dialogRefCloseSpy).toHaveBeenCalledWith([exerciseGroup1]);
    });

    it('should trigger an alarm for a wrong user input', () => {
        const importSpy = vi.spyOn(examManagementService, 'importExerciseGroup').mockReturnValue(
            of(
                new HttpResponse({
                    status: 200,
                    body: { exerciseGroups: [exerciseGroup1] },
                }),
            ),
        );
        const alertSpy = vi.spyOn(alertService, 'error');

        component.subsequentExerciseGroupSelection.set(true);
        const exerciseGroup2 = { title: 'exerciseGroup2' } as ExerciseGroup;
        const modelingExercise2 = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, exerciseGroup2);
        modelingExercise2.id = 2;
        exerciseGroup2.exercises = [modelingExercise2];
        component.exam.set({ id: 1, exerciseGroups: [exerciseGroup2] } as Exam);
        component.targetCourseId.set(1);
        component.targetExamId.set(3);
        fixture.detectChanges();
        component.performImportOfExerciseGroups();
        expect(importSpy).not.toHaveBeenCalled();
        expect(alertSpy).toHaveBeenCalledOnce();
        expect(dialogRefCloseSpy).not.toHaveBeenCalled();
    });

    it.each(['duplicatedProgrammingExerciseShortName', 'duplicatedProgrammingExerciseTitle', 'invalidKey'])(
        'should perform import of exercise groups AND correctly process conflict from server',
        async (errorKey) => {
            const preCheckError = new HttpErrorResponse({
                error: { errorKey: errorKey, numberOfInvalidProgrammingExercises: 0, params: { exerciseGroups: [exerciseGroup1] } },
                status: 400,
            });
            const importSpy = vi.spyOn(examManagementService, 'importExerciseGroup').mockReturnValue(throwError(() => preCheckError));
            const alertSpy = vi.spyOn(alertService, 'error');

            // A validation error is returned before the progress dialog shows a summary; the dialog rejects with it
            await performImport(importSpy, { reject: preCheckError });
            if (errorKey == 'invalidKey') {
                expect(alertSpy).toHaveBeenCalledWith('artemisApp.examManagement.exerciseGroup.importModal.invalidKey', { number: 0 });
            } else {
                expect(alertSpy).toHaveBeenCalledWith('artemisApp.examManagement.exerciseGroup.importModal.' + errorKey);
            }
            expect(dialogRefCloseSpy).not.toHaveBeenCalled();
        },
    );

    it('should perform import of exercise groups AND correctly process arbitrary exception from server', async () => {
        const error = new HttpErrorResponse({
            status: 400,
        });
        const importSpy = vi.spyOn(examManagementService, 'importExerciseGroup').mockReturnValue(throwError(() => error));
        const alertSpy = vi.spyOn(alertService, 'error');
        await performImport(importSpy, { reject: error });

        expect(alertSpy).toHaveBeenCalledOnce();
        expect(dialogRefCloseSpy).not.toHaveBeenCalled();
    });

    it.each(['invalidKey', 'duplicatedProgrammingExerciseShortName', 'duplicatedProgrammingExerciseTitle'])(
        'keeps multiple rejected groups editable and supports retry after %s',
        async (errorKey) => {
            const originalGroups: ExerciseGroup[] = [
                { id: 10, title: 'First group', exercises: [createProgrammingExercise(101, 'First program', 'firstProg')] },
                { id: 20, title: 'Second group', exercises: [createProgrammingExercise(102, 'Second program', 'secondProg')] },
            ];
            component.exam.set({ id: 1, exerciseGroups: originalGroups });
            component.subsequentExerciseGroupSelection.set(true);
            component.targetCourseId.set(1);
            component.targetExamId.set(2);
            fixture.detectChanges();
            await fixture.whenStable();
            vi.spyOn(examManagementService, 'generateImportId').mockReturnValue('test-import-id');
            const importSpy = vi.spyOn(examManagementService, 'importExerciseGroup').mockReturnValue(of(new HttpResponse({ body: { exerciseGroups: [] } })));
            const progressSpy = vi.spyOn(component.examImportProgressDialog(), 'runImport');
            const alertSpy = vi.spyOn(alertService, 'error');
            const warnSpy = vi.spyOn(console, 'warn');
            const child = component.examExerciseImportComponent();

            for (let attempt = 0; attempt < 2; attempt++) {
                // Every rejection contains fresh, idless groups, as returned by the server.
                const rejectedGroups: ExerciseGroup[] = [
                    { title: 'First group', exercises: [createProgrammingExercise(101)] },
                    { title: 'Second group', exercises: [createProgrammingExercise(102)] },
                ];
                const rejection = new HttpErrorResponse({ status: 400, error: { errorKey, numberOfInvalidProgrammingExercises: 2, params: { exerciseGroups: rejectedGroups } } });
                progressSpy.mockRejectedValueOnce(rejection);
                component.performImportOfExerciseGroups();
                await Promise.resolve();
                await Promise.resolve();
                fixture.detectChanges();
                await fixture.whenStable();

                expect(component.isImportingExercises()).toBe(false);
                expect(dialogRefCloseSpy).not.toHaveBeenCalled();
                expect(child.mapSelectedExercisesToExerciseGroups()).toEqual(rejectedGroups);
                expect(child.selectedExercises.has(originalGroups[0])).toBe(false);
                const rows: NodeListOf<HTMLTableRowElement> = fixture.nativeElement.querySelectorAll('jhi-exam-exercise-import > table > tbody > tr');
                expect(rows).toHaveLength(2);
                const groupTitles: HTMLInputElement[] = Array.from(fixture.nativeElement.querySelectorAll('input[id^="exerciseGroup-"][id$="-title"]'));
                expect(new Set(groupTitles.map((input) => input.id)).size).toBe(2);
                expect(warnSpy.mock.calls.flat().join(' ')).not.toContain('NG0955');
                for (const [index, group] of rejectedGroups.entries()) {
                    const exercise = group.exercises![0];
                    expect(child.exerciseIsSelected(exercise, group)).toBe(true);
                    const title: HTMLInputElement = fixture.nativeElement.querySelector(`#exercise-${exercise.id}-title`);
                    const shortName: HTMLInputElement = fixture.nativeElement.querySelector(`#programming-exercise-${exercise.id}-shortName`);
                    expect(title.value).toBe('');
                    expect(shortName.value).toBe('');
                    if (errorKey === 'invalidKey' && attempt === 0) {
                        expect(title.placeholder).toBe(originalGroups[index].exercises![0].title);
                        expect(shortName.placeholder).toBe(originalGroups[index].exercises![0].shortName);
                    }
                    for (const [input, value] of [
                        [groupTitles[index], `Renamed group ${index}`],
                        [title, `Retry program ${attempt} ${index}`],
                        [shortName, `retry${attempt}${index}`],
                    ] as const) {
                        input.value = value;
                        input.dispatchEvent(new Event('input'));
                        input.dispatchEvent(new Event('change'));
                    }
                }
                await fixture.whenStable();
                expect(rejectedGroups.map((group) => group.title)).toEqual(['Renamed group 0', 'Renamed group 1']);
                expect(child.validateUserInput()).toBe(true);
            }

            const correctedGroups = child.mapSelectedExercisesToExerciseGroups();
            progressSpy.mockResolvedValueOnce(new HttpResponse({ body: { exerciseGroups: correctedGroups } }));
            component.performImportOfExerciseGroups();
            await Promise.resolve();
            await Promise.resolve();
            expect(importSpy).toHaveBeenCalledTimes(3);
            expect(importSpy).toHaveBeenLastCalledWith(1, 2, correctedGroups, 'test-import-id');
            expect(alertSpy).toHaveBeenCalledTimes(2);
            expect(dialogRefCloseSpy).toHaveBeenCalledWith(correctedGroups);
        },
    );

    function createProgrammingExercise(id: number, title?: string, shortName?: string): ProgrammingExercise {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.id = id;
        exercise.title = title;
        exercise.shortName = shortName;
        return exercise;
    }

    /**
     * Drives a group import: sets up the selection state, stubs the import id, and mocks the progress dialog's runImport
     * to either resolve with the given response (user dismissed the success summary) or reject with the given error.
     */
    async function performImport(importSpy: ReturnType<typeof vi.spyOn>, outcome: { resolve?: HttpResponse<ExerciseGroupImportResultDTO>; reject?: unknown }): Promise<void> {
        component.exam.set(exam1WithExercises);
        component.subsequentExerciseGroupSelection.set(true);
        component.targetCourseId.set(1);
        component.targetExamId.set(2);
        fixture.detectChanges();
        vi.spyOn(examManagementService, 'generateImportId').mockReturnValue('test-import-id');
        if (outcome.reject !== undefined) {
            vi.spyOn(component.examImportProgressDialog(), 'runImport').mockRejectedValue(outcome.reject);
        } else {
            vi.spyOn(component.examImportProgressDialog(), 'runImport').mockResolvedValue(outcome.resolve!);
        }
        component.performImportOfExerciseGroups();
        // flush the runImport promise's then/catch microtasks
        await Promise.resolve();
        await Promise.resolve();
        expect(importSpy).toHaveBeenCalledOnce();
        expect(importSpy).toHaveBeenCalledWith(1, 2, [exerciseGroup1], 'test-import-id');
    }
});
