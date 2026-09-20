import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ActivatedRoute, Params } from '@angular/router';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { FilterProp, ParticipationComponent } from 'app/exercise/participation/participation.component';
import { Course } from 'app/course/shared/entities/course.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { of, throwError } from 'rxjs';
import { ParticipationManagementDTO } from 'app/exercise/participation/participation-management-dto.model';
import dayjs from 'dayjs/esm';
import { ProgrammingSubmissionService } from 'app/programming/shared/services/programming-submission.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { MockProvider } from 'ng-mocks';
import { MockProgrammingSubmissionService } from 'test/helpers/mocks/service/mock-programming-submission.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { By } from '@angular/platform-browser';
import { FormDateTimePickerComponent } from 'app/shared-ui/date-time-picker/date-time-picker.component';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { PageableResult } from 'app/foundation/pagination/pageable-table';

describe('ParticipationComponent', () => {
    let component: ParticipationComponent;
    let componentFixture: ComponentFixture<ParticipationComponent>;
    let participationService: ParticipationService;
    let exerciseService: ExerciseService;
    let alertService: AlertService;

    const course: Course = { id: 10, presentationScore: 1 };

    const exercise: Exercise = {
        numberOfAssessmentsOfCorrectionRounds: [],
        studentAssignedTeamIdComputed: false,
        id: 1,
        secondCorrectionEnabled: true,
        type: ExerciseType.TEXT,
        course,
    };

    const sampleDto: ParticipationManagementDTO = {
        participationId: 3,
        submissionCount: 1,
        testRun: false,
        participantName: 'Alice',
        participantIdentifier: 'alice',
    };

    const route = { params: of({ exerciseId: '1' } as Params) } as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: ProgrammingSubmissionService, useClass: MockProgrammingSubmissionService },
                { provide: AlertService, useClass: MockAlertService },
                LocalStorageService,
                SessionStorageService,
                MockProvider(ExerciseService),
                MockProvider(ParticipationService),
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(EventManager),
                { provide: WebsocketService, useClass: MockWebsocketService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(ParticipationComponent);
                component = componentFixture.componentInstance;
                participationService = TestBed.inject(ParticipationService);
                exerciseService = TestBed.inject(ExerciseService);
                alertService = TestBed.inject(AlertService);
                component.exercise.set(exercise);
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('Initialization', () => {
        it('should initialize with exerciseId from route', () => {
            const exerciseFindStub = vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));

            component.ngOnInit();

            expect(exerciseFindStub).toHaveBeenCalledExactlyOnceWith(1);
            expect(component.exercise()).toEqual(exercise);
        });
    });

    describe('Anonymous participation list', () => {
        it.each([false, true])('should show participation IDs without identity search or team links for tutors (team mode: %s)', async (teamMode) => {
            const tutorExercise = { ...exercise, course: undefined, type: ExerciseType.PROGRAMMING, isAtLeastTutor: true, isAtLeastInstructor: false, teamMode };
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: tutorExercise })));
            vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: [sampleDto], totalElements: 1 }));

            componentFixture.detectChanges();
            await componentFixture.whenStable();
            componentFixture.detectChanges();

            expect(component.columns()[0]).toMatchObject({ headerKey: 'artemisApp.participation.participationId', field: 'participationId', sort: true });
            expect(
                component.columns().some((column) => column.headerKey === 'artemisApp.participation.students' || column.headerKey === 'artemisApp.participation.repository'),
            ).toBe(false);
            const table: HTMLElement = componentFixture.nativeElement.querySelector('jhi-table-view');
            expect(table.querySelector('tbody td')?.textContent?.trim()).toBe(String(sampleDto.participationId));
            expect(table.textContent).not.toContain(sampleDto.participantName);
            expect(table.querySelector('a[href*="/teams/"]')).toBeNull();
            expect(table.querySelector('jhi-search-filter')).toBeNull();
        });

        it.each([false, true])('should retain identity columns and search for instructors (team mode: %s)', (teamMode) => {
            component.exercise.set({ ...exercise, teamMode, isAtLeastInstructor: true });

            expect(component.tableOptions().showSearch).not.toBe(false);
            expect(component.columns()[0]).toMatchObject({
                headerKey: teamMode ? 'artemisApp.participation.team' : 'artemisApp.participation.student',
                field: 'participantName',
                sort: true,
            });
            expect(component.columns().some((column) => column.headerKey === 'artemisApp.participation.students')).toBe(teamMode);
        });

        it.each(['participantName', 'participantIdentifier', 'buildPlanId'])(
            'should discard stale identity search and sorting for tutors while retaining filters and paging (%s)',
            (sortField) => {
                component.exercise.set({ ...exercise, isAtLeastInstructor: false });
                component.activeFilter.set(FilterProp.NO_SUBMISSIONS);
                const searchSpy = vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: [], totalElements: 0 }));

                component.onLazyLoad({ first: 50, rows: 25, globalFilter: 'alice', sortField });

                expect(searchSpy).toHaveBeenCalledWith(
                    exercise.id,
                    expect.objectContaining({ page: 2, pageSize: 25, searchTerm: '', sortedColumn: 'id', filterProp: FilterProp.NO_SUBMISSIONS }),
                );
            },
        );

        it('should retain instructor identity search and sorting', () => {
            component.exercise.set({ ...exercise, isAtLeastInstructor: true });
            const searchSpy = vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: [], totalElements: 0 }));

            component.onLazyLoad({ globalFilter: 'alice', sortField: 'participantName' });

            expect(searchSpy).toHaveBeenCalledWith(exercise.id, expect.objectContaining({ searchTerm: 'alice', sortedColumn: 'participantName' }));
        });

        it('should identify an anonymous participation in the due-date success message', () => {
            const dto: ParticipationManagementDTO = { participationId: 42, submissionCount: 1, testRun: false };
            vi.spyOn(participationService, 'updateIndividualDueDates').mockReturnValue(of(new HttpResponse({ body: [] })));
            const successSpy = vi.spyOn(alertService, 'success');
            component.startEditDueDate(dto);

            component.saveIndividualDueDate(dto);

            expect(successSpy).toHaveBeenCalledWith('artemisApp.participation.updateDueDates.success', { name: '42' });
        });
    });

    describe('Pagination / lazy loading', () => {
        it('should load page when a lazy load event fires', () => {
            const searchStub = vi
                .spyOn(participationService, 'searchParticipations')
                .mockReturnValue(of({ content: [sampleDto], totalElements: 1 } as PageableResult<ParticipationManagementDTO>));

            component.onLazyLoad({ first: 0, rows: 50 });

            expect(searchStub).toHaveBeenCalledOnce();
            expect(component.participations()).toEqual([sampleDto]);
            expect(component.totalRows()).toBe(1);
            expect(component.isLoading()).toBe(false);
        });

        it('should update active filter and reload on filter change', () => {
            const searchStub = vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: [], totalElements: 0 }));

            component.onLazyLoad({ first: 0, rows: 50 });
            searchStub.mockClear();

            component.updateParticipationFilter(FilterProp.FAILED);

            expect(component.activeFilter()).toBe(FilterProp.FAILED);
            expect(searchStub).toHaveBeenCalledOnce();
        });
    });

    describe('Relevant filters', () => {
        it('should not include FAILED and NO_PRACTICE filters for non-programming exercises', () => {
            component.exercise.set({ ...exercise, type: ExerciseType.TEXT });

            expect(component.relevantFilters()).not.toContain(FilterProp.FAILED);
            expect(component.relevantFilters()).not.toContain(FilterProp.NO_PRACTICE);
            expect(component.relevantFilters()).toContain(FilterProp.ALL);
            expect(component.relevantFilters()).toContain(FilterProp.NO_SUBMISSIONS);
        });

        it('should include FAILED and NO_PRACTICE filters for programming exercises', () => {
            component.exercise.set({ ...exercise, type: ExerciseType.PROGRAMMING });

            expect(component.relevantFilters()).toContain(FilterProp.FAILED);
            expect(component.relevantFilters()).toContain(FilterProp.NO_PRACTICE);
        });
    });

    describe('Navigation', () => {
        it('should return correct participation link for non-exam exercise', () => {
            expect(component.getParticipationLink(42)).toEqual(['42', 'submissions']);
        });

        it('should return correct participation link for exam exercise', () => {
            component.exercise.set({ ...exercise, exerciseGroup: { id: 5, exam: { id: 2 } } });

            expect(component.getParticipationLink(42)).toEqual(['42']);
        });

        it('should compute scoresRoute for non-exam exercise', () => {
            component.exercise.set({ ...exercise, type: ExerciseType.TEXT, id: 1, course: { id: 10 } });

            expect(component.scoresRoute()).toEqual(['/course-management', 10, 'text-exercises', 1, 'scores']);
        });

        it('should compute scoresRoute for exam exercise', () => {
            component.exercise.set({
                ...exercise,
                type: ExerciseType.TEXT,
                id: 1,
                exerciseGroup: { id: 5, exam: { id: 2, course: { id: 10 } } },
            });

            expect(component.scoresRoute()).toEqual(['/course-management', 10, 'exams', 2, 'exercise-groups', 5, 'text-exercises', 1, 'scores']);
        });
    });

    describe('Presentation enabled computed signals', () => {
        it('should compute basicPresentationEnabled correctly', () => {
            component.exercise.set({
                ...exercise,
                isAtLeastTutor: true,
                presentationScoreEnabled: true,
                course: { ...course, presentationScore: 1 },
            });
            expect(component.basicPresentationEnabled()).toBe(true);

            component.exercise.set({ ...exercise, presentationScoreEnabled: false, course });
            expect(component.basicPresentationEnabled()).toBe(false);
        });

        it('should compute gradedPresentationEnabled correctly', () => {
            component.exercise.set({
                ...exercise,
                isAtLeastTutor: true,
                presentationScoreEnabled: true,
                course,
            });
            component.gradeStepsDTO.set({ presentationsNumber: 2, gradeSteps: [], gradeType: undefined as any, title: '', plagiarismGrade: '', noParticipationGrade: '' });

            expect(component.gradedPresentationEnabled()).toBe(true);

            component.gradeStepsDTO.set({ presentationsNumber: 0, gradeSteps: [], gradeType: undefined as any, title: '', plagiarismGrade: '', noParticipationGrade: '' });
            expect(component.gradedPresentationEnabled()).toBe(false);
        });
    });

    describe('Individual due date input validation', () => {
        let picker: FormDateTimePickerComponent;
        let saveButton: HTMLButtonElement;
        let dto: ParticipationManagementDTO;
        const dueDate = dayjs('2030-06-01T12:00:00');
        const individualDueDate = dayjs('2030-06-03T12:00:00');

        beforeEach(async () => {
            dto = { ...sampleDto, individualDueDate };
            const exerciseWithDueDate = { ...exercise, course: undefined, dueDate };
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: exerciseWithDueDate })));
            vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: [dto], totalElements: 1 }));
            component.startEditDueDate(dto);
            componentFixture.detectChanges();
            await componentFixture.whenStable();
            componentFixture.detectChanges();
            picker = componentFixture.debugElement.query(By.directive(FormDateTimePickerComponent)).componentInstance;
            saveButton = componentFixture.nativeElement.querySelector('button[title="Save"]');
        });

        it.each(['invalid date', new Date('2020-01-01T12:00:00')])('should prevent saving invalid input %s', async (invalidInput) => {
            const updateSpy = vi.spyOn(participationService, 'updateIndividualDueDates').mockReturnValue(of(new HttpResponse({ body: [] })));
            const successSpy = vi.spyOn(alertService, 'success');

            picker.updateField(invalidInput);
            componentFixture.detectChanges();
            await componentFixture.whenStable();

            expect(saveButton.disabled).toBe(true);
            saveButton.click();
            expect(updateSpy).not.toHaveBeenCalled();
            expect(successSpy).not.toHaveBeenCalled();
            expect(dto.individualDueDate).toEqual(individualDueDate);
        });

        it('should save when invalid input is corrected', async () => {
            const updateSpy = vi.spyOn(participationService, 'updateIndividualDueDates').mockReturnValue(of(new HttpResponse({ body: [] })));
            const successSpy = vi.spyOn(alertService, 'success');
            picker.updateField('invalid date');
            componentFixture.detectChanges();
            await componentFixture.whenStable();
            expect(saveButton.disabled).toBe(true);

            picker.updateField(individualDueDate.toDate());
            componentFixture.detectChanges();
            await componentFixture.whenStable();

            expect(saveButton.disabled).toBe(false);
            expect(component.getPendingDueDate(dto.participationId)).toEqual(individualDueDate);
            saveButton.click();
            expect(updateSpy).toHaveBeenCalledExactlyOnceWith(component.exercise(), [expect.objectContaining({ id: dto.participationId, individualDueDate })]);
            expect(successSpy).toHaveBeenCalledOnce();
        });

        it('should allow clearing an existing individual due date after invalid input', async () => {
            const updateSpy = vi.spyOn(participationService, 'updateIndividualDueDates').mockReturnValue(of(new HttpResponse({ body: [{ id: dto.participationId }] })));
            picker.updateField('invalid date');
            componentFixture.detectChanges();
            await componentFixture.whenStable();
            expect(saveButton.disabled).toBe(true);

            picker.updateField(null);
            componentFixture.detectChanges();
            await componentFixture.whenStable();

            expect(saveButton.disabled).toBe(false);
            saveButton.click();
            expect(updateSpy).toHaveBeenCalledExactlyOnceWith(component.exercise(), [expect.objectContaining({ id: dto.participationId, individualDueDate: undefined })]);
            expect(dto.individualDueDate).toBeUndefined();
        });
    });

    describe('Individual due date', () => {
        it('should track individual due date editing lifecycle', () => {
            expect(component.isEditingDueDate(sampleDto.participationId)).toBe(false);

            component.startEditDueDate(sampleDto);

            expect(component.isEditingDueDate(sampleDto.participationId)).toBe(true);
            expect(component.getPendingDueDate(sampleDto.participationId)).toEqual(sampleDto.individualDueDate);

            const newDate = dayjs().add(1, 'day');
            component.setPendingDueDate(sampleDto.participationId, newDate);
            expect(component.getPendingDueDate(sampleDto.participationId)).toEqual(newDate);

            component.cancelEditDueDate(sampleDto);

            expect(component.isEditingDueDate(sampleDto.participationId)).toBe(false);
            expect(component.getPendingDueDate(sampleDto.participationId)).toBeUndefined();
        });

        it('should save individual due date and reload', () => {
            const newDate = dayjs().add(2, 'days');
            component.startEditDueDate(sampleDto);
            component.setPendingDueDate(sampleDto.participationId, newDate);

            const updateStub = vi.spyOn(participationService, 'updateIndividualDueDates').mockReturnValue(of(new HttpResponse({ body: [] })));
            const searchStub = vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: [], totalElements: 0 }));
            component.onLazyLoad({ first: 0, rows: 50 });
            searchStub.mockClear();

            component.saveIndividualDueDate(sampleDto);

            expect(updateStub).toHaveBeenCalledOnce();
            expect(sampleDto.individualDueDate).toEqual(newDate);
            expect(component.isEditingDueDate(sampleDto.participationId)).toBe(false);
            expect(component.isSaving()).toBe(false);
            expect(searchStub).toHaveBeenCalledOnce();
        });

        it('should show error alert when saving due date fails', () => {
            component.startEditDueDate(sampleDto);
            vi.spyOn(participationService, 'updateIndividualDueDates').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
            const errorSpy = vi.spyOn(alertService, 'error');

            component.saveIndividualDueDate(sampleDto);

            expect(errorSpy).toHaveBeenCalledOnce();
            expect(component.isSaving()).toBe(false);
        });
    });

    describe('Delete participation', () => {
        it('should delete participation and reload', () => {
            vi.spyOn(participationService, 'delete').mockReturnValue(of(new HttpResponse<void>()));
            const searchStub = vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: [], totalElements: 0 }));
            component.onLazyLoad({ first: 0, rows: 50 });
            searchStub.mockClear();

            component.deleteParticipation(sampleDto.participationId);

            expect(searchStub).toHaveBeenCalledOnce();
        });

        it('should clean up pending due date and presentation map on delete', () => {
            vi.spyOn(participationService, 'delete').mockReturnValue(of(new HttpResponse<void>()));
            vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: [], totalElements: 0 }));
            component.onLazyLoad({ first: 0, rows: 50 });

            component.startEditDueDate(sampleDto);
            component.changeGradedPresentation(sampleDto);

            expect(component.isEditingDueDate(sampleDto.participationId)).toBe(true);
            expect(component.hasGradedPresentationChanged(sampleDto)).toBe(true);

            component.deleteParticipation(sampleDto.participationId);

            expect(component.isEditingDueDate(sampleDto.participationId)).toBe(false);
            expect(component.hasGradedPresentationChanged(sampleDto)).toBe(false);
        });
    });

    describe('Cleanup programming participation', () => {
        it('should call cleanupBuildPlan and reload on success', () => {
            const cleanupStub = vi.spyOn(participationService, 'cleanupBuildPlan').mockReturnValue(of(new HttpResponse({ body: {} as any })));
            const searchStub = vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: [], totalElements: 0 }));
            component.onLazyLoad({ first: 0, rows: 50 });
            searchStub.mockClear();

            component.cleanupProgrammingExerciseParticipation(sampleDto);

            expect(cleanupStub).toHaveBeenCalledOnce();
            expect(searchStub).toHaveBeenCalledOnce();
        });
    });

    describe('Graded presentation tracking', () => {
        it('should track graded presentation changes', () => {
            expect(component.hasGradedPresentationChanged(sampleDto)).toBe(false);

            component.changeGradedPresentation(sampleDto);

            expect(component.hasGradedPresentationChanged(sampleDto)).toBe(true);
        });
    });

    describe('Basic presentation', () => {
        const basicExercise: Exercise = {
            ...exercise,
            isAtLeastTutor: true,
            presentationScoreEnabled: true,
            course: { id: 10, presentationScore: 1 },
        };

        it('should call update with presentationScore=1 and reload on success', () => {
            component.exercise.set(basicExercise);
            const updateStub = vi.spyOn(participationService, 'update').mockReturnValue(of(new HttpResponse({ body: {} as any })));
            const searchStub = vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: [], totalElements: 0 }));
            component.onLazyLoad({ first: 0, rows: 50 });
            searchStub.mockClear();

            component.addBasicPresentation(sampleDto);

            expect(updateStub).toHaveBeenCalledOnce();
            const passedParticipation = updateStub.mock.calls[0][1];
            expect(passedParticipation.presentationScore).toBe(1);
            expect(searchStub).toHaveBeenCalledOnce();
        });

        it('should show error alert when addBasicPresentation fails', () => {
            component.exercise.set(basicExercise);
            vi.spyOn(participationService, 'update').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
            const errorSpy = vi.spyOn(alertService, 'error');

            component.addBasicPresentation(sampleDto);

            expect(errorSpy).toHaveBeenCalledOnce();
        });

        it('should not call update when basicPresentationEnabled is false', () => {
            component.exercise.set({ ...exercise, presentationScoreEnabled: false });
            const updateStub = vi.spyOn(participationService, 'update');

            component.addBasicPresentation(sampleDto);

            expect(updateStub).not.toHaveBeenCalled();
        });
    });

    describe('Graded presentation', () => {
        const gradedExercise: Exercise = {
            ...exercise,
            isAtLeastTutor: true,
            presentationScoreEnabled: true,
            course,
        };

        beforeEach(() => {
            component.exercise.set(gradedExercise);
            component.gradeStepsDTO.set({ presentationsNumber: 2, gradeSteps: [], gradeType: undefined as any, title: '', plagiarismGrade: '', noParticipationGrade: '' });
        });

        it('should call update and reload on success', () => {
            const dto = { ...sampleDto, presentationScore: 75 };
            const updateStub = vi.spyOn(participationService, 'update').mockReturnValue(of(new HttpResponse({ body: {} as any })));
            const searchStub = vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: [], totalElements: 0 }));
            component.onLazyLoad({ first: 0, rows: 50 });
            component.changeGradedPresentation(dto);
            searchStub.mockClear();

            component.addGradedPresentation(dto);

            expect(updateStub).toHaveBeenCalledOnce();
            expect(component.hasGradedPresentationChanged(dto)).toBe(false);
            expect(searchStub).toHaveBeenCalledOnce();
        });

        it('should not call update when score > 100', () => {
            const dto = { ...sampleDto, presentationScore: 101 };
            const updateStub = vi.spyOn(participationService, 'update');

            component.addGradedPresentation(dto);

            expect(updateStub).not.toHaveBeenCalled();
        });

        it('should not call update when score < 0', () => {
            const dto = { ...sampleDto, presentationScore: -1 };
            const updateStub = vi.spyOn(participationService, 'update');

            component.addGradedPresentation(dto);

            expect(updateStub).not.toHaveBeenCalled();
        });

        it('should clear presentationScore when maxPresentationsExceeded error is returned', () => {
            const dto = { ...sampleDto, presentationScore: 80 };
            vi.spyOn(participationService, 'update').mockReturnValue(
                throwError(
                    () =>
                        new HttpErrorResponse({
                            status: 400,
                            error: { errorKey: 'invalid.presentations.maxNumberOfPresentationsExceeded' },
                        }),
                ),
            );

            component.addGradedPresentation(dto);

            expect(dto.presentationScore).toBeUndefined();
        });

        it('should show error alert on generic error', () => {
            const dto = { ...sampleDto, presentationScore: 80 };
            vi.spyOn(participationService, 'update').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500, error: { errorKey: 'other' } })));
            const errorSpy = vi.spyOn(alertService, 'error');

            component.addGradedPresentation(dto);

            expect(errorSpy).toHaveBeenCalledOnce();
        });

        it('should not call update when gradedPresentationEnabled is false', () => {
            component.gradeStepsDTO.set({ presentationsNumber: 0, gradeSteps: [], gradeType: undefined as any, title: '', plagiarismGrade: '', noParticipationGrade: '' });
            const updateStub = vi.spyOn(participationService, 'update');

            component.addGradedPresentation({ ...sampleDto, presentationScore: 50 });

            expect(updateStub).not.toHaveBeenCalled();
        });
    });

    describe('Remove presentation', () => {
        it('should call update with undefined presentationScore and reload (basic enabled)', () => {
            component.exercise.set({
                ...exercise,
                isAtLeastTutor: true,
                presentationScoreEnabled: true,
                course: { id: 10, presentationScore: 1 },
            });
            const updateStub = vi.spyOn(participationService, 'update').mockReturnValue(of(new HttpResponse({ body: {} as any })));
            const searchStub = vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: [], totalElements: 0 }));
            component.onLazyLoad({ first: 0, rows: 50 });
            searchStub.mockClear();

            component.removePresentation(sampleDto);

            expect(updateStub).toHaveBeenCalledOnce();
            const passedParticipation = updateStub.mock.calls[0][1];
            expect(passedParticipation.presentationScore).toBeUndefined();
            expect(searchStub).toHaveBeenCalledOnce();
        });

        it('should show error alert when removePresentation fails', () => {
            component.exercise.set({
                ...exercise,
                isAtLeastTutor: true,
                presentationScoreEnabled: true,
                course: { id: 10, presentationScore: 1 },
            });
            vi.spyOn(participationService, 'update').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
            const errorSpy = vi.spyOn(alertService, 'error');

            component.removePresentation(sampleDto);

            expect(errorSpy).toHaveBeenCalledOnce();
        });

        it('should not call update when neither basic nor graded presentation is enabled', () => {
            component.exercise.set({ ...exercise, presentationScoreEnabled: false });
            const updateStub = vi.spyOn(participationService, 'update');

            component.removePresentation(sampleDto);

            expect(updateStub).not.toHaveBeenCalled();
        });
    });
});
