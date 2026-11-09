import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FileUploadExerciseService } from 'app/fileupload/manage/services/file-upload-exercise.service';
import { NonProgrammingExerciseDetailCommonActionsComponent } from 'app/exercise/exercise-detail-common-actions/non-programming-exercise-detail-common-actions.component';
import { MockFileUploadExerciseService } from 'test/helpers/mocks/service/mock-file-upload-exercise.service';
import { MockProvider } from 'ng-mocks';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { TextExerciseService } from 'app/text/manage/text-exercise/service/text-exercise.service';
import { Observable, Subject, of } from 'rxjs';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { ModelingExerciseService } from 'app/modeling/manage/services/modeling-exercise.service';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { UMLDiagramType } from '@tumaet/apollon';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { EntityDeletionProgress, EntityDeletionStatus, EntityDeletionType } from 'app/shared-ui/delete-dialog/entity-deletion-progress.model';
import { EntityDeletionService } from 'app/shared-ui/delete-dialog/service/entity-deletion.service';
import { MockEntityDeletionService } from 'test/helpers/mocks/service/mock-entity-deletion.service';

describe('Exercise detail common actions Component', () => {
    let comp: NonProgrammingExerciseDetailCommonActionsComponent;
    let fixture: ComponentFixture<NonProgrammingExerciseDetailCommonActionsComponent>;

    const course: Course = { id: 123 };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [NonProgrammingExerciseDetailCommonActionsComponent],
            providers: [
                { provide: EntityDeletionService, useClass: MockEntityDeletionService },
                MockProvider(TextExerciseService),
                { provide: FileUploadExerciseService, useClass: MockFileUploadExerciseService },
                MockProvider(ModelingExerciseService),
                { provide: Router, useClass: MockRouter },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(ProfileService),
            ],
        })
            .overrideTemplate(NonProgrammingExerciseDetailCommonActionsComponent, '')
            .compileComponents();
        fixture = TestBed.createComponent(NonProgrammingExerciseDetailCommonActionsComponent);
        comp = fixture.componentInstance;
    });

    it('should be ok', () => {
        fixture.componentRef.setInput('exercise', { type: ExerciseType.TEXT, id: 1 } as TextExercise);
        fixture.componentRef.setInput('course', course);
        fixture.detectChanges();
        expect(comp).not.toBeNull();
    });

    it('should get the correct edit routes for non exam exercise', () => {
        const textExercise: TextExercise = new TextExercise(course, undefined);
        textExercise.id = 123;

        fixture.componentRef.setInput('exercise', textExercise);
        fixture.componentRef.setInput('course', course);
        comp.ngOnInit();
        expect(comp.baseResource()).toBe('/course-management/123/text-exercises/123/');

        const fileUploadExercise: FileUploadExercise = new FileUploadExercise(course, undefined);
        fileUploadExercise.id = 123;

        fixture.componentRef.setInput('exercise', fileUploadExercise);
        comp.ngOnInit();
        expect(comp.baseResource()).toBe('/course-management/123/file-upload-exercises/123/');

        const modelingExercise: ModelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
        modelingExercise.id = 123;

        fixture.componentRef.setInput('exercise', modelingExercise);
        comp.ngOnInit();
        expect(comp.baseResource()).toBe('/course-management/123/modeling-exercises/123/');
    });

    it('updates destinations when the routed exercise changes without recreating the toolbar', () => {
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('exercise', Object.assign(new TextExercise(course, undefined), { id: 1 }));
        comp.ngOnInit();
        fixture.componentRef.setInput('exercise', Object.assign(new TextExercise(course, undefined), { id: 2 }));
        expect(comp.baseResource()).toBe('/course-management/123/text-exercises/2/');
        expect(comp.teamBaseResource()).toBe('/course-management/123/exercises/2/');
    });

    it('should get the correct edit routes for exam exercise', () => {
        const exam: Exam = { id: 2, course };
        const exerciseGroup: ExerciseGroup = { id: 3, exam };
        const textExercise: TextExercise = new TextExercise(course, exerciseGroup);
        textExercise.id = 4;

        fixture.componentRef.setInput('isExamExercise', true);
        fixture.componentRef.setInput('exercise', textExercise);
        fixture.componentRef.setInput('course', course);
        comp.ngOnInit();
        expect(comp.baseResource()).toBe('/course-management/123/exams/2/exercise-groups/3/text-exercises/4/');

        const fileUploadExercise: FileUploadExercise = new FileUploadExercise(course, exerciseGroup);
        fileUploadExercise.id = 5;

        fixture.componentRef.setInput('exercise', fileUploadExercise);
        comp.ngOnInit();
        expect(comp.baseResource()).toBe('/course-management/123/exams/2/exercise-groups/3/file-upload-exercises/5/');

        const modelingExercise: ModelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, exerciseGroup);
        modelingExercise.id = 6;

        fixture.componentRef.setInput('exercise', modelingExercise);
        comp.ngOnInit();
        expect(comp.baseResource()).toBe('/course-management/123/exams/2/exercise-groups/3/modeling-exercises/6/');
    });

    describe('canAccessParticipationsAndScores', () => {
        it('should return true for course exercise when user is at least tutor', () => {
            const textExercise: TextExercise = new TextExercise(course, undefined);
            textExercise.id = 1;
            textExercise.isAtLeastTutor = true;
            textExercise.isAtLeastInstructor = false;

            fixture.componentRef.setInput('exercise', textExercise);
            fixture.componentRef.setInput('course', course);
            fixture.componentRef.setInput('isExamExercise', false);

            expect(comp.canAccessParticipationsAndScores()).toBe(true);
        });

        it('should return false for course exercise when user is not at least tutor', () => {
            const textExercise: TextExercise = new TextExercise(course, undefined);
            textExercise.id = 1;
            textExercise.isAtLeastTutor = false;
            textExercise.isAtLeastInstructor = false;

            fixture.componentRef.setInput('exercise', textExercise);
            fixture.componentRef.setInput('course', course);
            fixture.componentRef.setInput('isExamExercise', false);

            expect(comp.canAccessParticipationsAndScores()).toBe(false);
        });

        it('should return false for exam exercise when user is only tutor', () => {
            const exam: Exam = { id: 2, course };
            const exerciseGroup: ExerciseGroup = { id: 3, exam };
            const textExercise: TextExercise = new TextExercise(course, exerciseGroup);
            textExercise.id = 1;
            textExercise.isAtLeastTutor = true;
            textExercise.isAtLeastInstructor = false;

            fixture.componentRef.setInput('exercise', textExercise);
            fixture.componentRef.setInput('course', course);
            fixture.componentRef.setInput('isExamExercise', true);

            expect(comp.canAccessParticipationsAndScores()).toBe(false);
        });

        it('should return true for exam exercise when user is at least instructor', () => {
            const exam: Exam = { id: 2, course };
            const exerciseGroup: ExerciseGroup = { id: 3, exam };
            const textExercise: TextExercise = new TextExercise(course, exerciseGroup);
            textExercise.id = 1;
            textExercise.isAtLeastTutor = true;
            textExercise.isAtLeastInstructor = true;

            fixture.componentRef.setInput('exercise', textExercise);
            fixture.componentRef.setInput('course', course);
            fixture.componentRef.setInput('isExamExercise', true);

            expect(comp.canAccessParticipationsAndScores()).toBe(true);
        });
    });

    it('should call event manager on delete exercises', () => {
        const textExerciseService = TestBed.inject(TextExerciseService);
        const fileUploadExerciseService = TestBed.inject(FileUploadExerciseService);
        const modelingExerciseService = TestBed.inject(ModelingExerciseService);

        const deleteTextExerciseService = vi.spyOn(textExerciseService, 'delete').mockReturnValue(of({} as HttpResponse<any>));
        const deleteFileUploadExerciseStub = vi.spyOn(fileUploadExerciseService, 'delete').mockReturnValue(of({} as HttpResponse<any>));
        const deleteModelingExerciseService = vi.spyOn(modelingExerciseService, 'delete').mockReturnValue(of({} as HttpResponse<any>));

        fixture.componentRef.setInput('course', course);

        fixture.componentRef.setInput('exercise', new TextExercise({ id: 123 }, undefined));
        comp.deleteExercise();
        expect(deleteTextExerciseService).toHaveBeenCalledOnce();

        fixture.componentRef.setInput('exercise', new FileUploadExercise({ id: 123 }, undefined));
        comp.deleteExercise();
        expect(deleteFileUploadExerciseStub).toHaveBeenCalledOnce();

        fixture.componentRef.setInput('exercise', new ModelingExercise(UMLDiagramType.ClassDiagram, { id: 123 }, undefined));
        comp.deleteExercise();
        expect(deleteModelingExerciseService).toHaveBeenCalledOnce();
    });

    it('should leave the page of the exercise only when the background deletion completed', () => {
        const deletionService = TestBed.inject(EntityDeletionService) as unknown as MockEntityDeletionService;
        const router = TestBed.inject(Router);
        const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
        vi.spyOn(TestBed.inject(TextExerciseService), 'delete').mockReturnValue(of({} as HttpResponse<any>));
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('exercise', new TextExercise({ id: 123 }, undefined));

        deletionService.status = EntityDeletionStatus.FAILED;
        comp.deleteExercise();
        expect(navigateSpy).not.toHaveBeenCalled();

        deletionService.status = EntityDeletionStatus.COMPLETED;
        comp.deleteExercise();
        expect(navigateSpy).toHaveBeenCalledOnce();
    });

    it('should not leave the page when the deletion ends after the reused component moved on to another exercise', () => {
        const deletionService = TestBed.inject(EntityDeletionService) as unknown as MockEntityDeletionService;
        const ended$ = new Subject<EntityDeletionProgress>();
        deletionService.delete.mockReturnValueOnce(ended$);
        const router = TestBed.inject(Router);
        const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
        const broadcastSpy = vi.spyOn(TestBed.inject(EventManager), 'broadcast');
        vi.spyOn(TestBed.inject(TextExerciseService), 'delete').mockReturnValue(of({} as HttpResponse<any>));
        const deletedExercise = new TextExercise(undefined, undefined);
        deletedExercise.id = 123;
        const otherExercise = new TextExercise(undefined, undefined);
        otherExercise.id = 456;
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('exercise', deletedExercise);
        comp.deleteExercise();

        fixture.componentRef.setInput('exercise', otherExercise);
        ended$.next({
            entityType: EntityDeletionType.EXERCISE,
            entityId: 123,
            status: EntityDeletionStatus.COMPLETED,
            stepsCompleted: 1,
            totalSteps: 1,
            itemsProcessed: 0,
            totalItems: 0,
            weightedProgressPercent: 100,
        });

        expect(broadcastSpy).toHaveBeenCalled();
        expect(navigateSpy).not.toHaveBeenCalled();
    });

    it('should show the progress of a deletion that is already running when an instructor opens the page', () => {
        const deletionService = TestBed.inject(EntityDeletionService) as unknown as MockEntityDeletionService;
        const exercise = new TextExercise({ id: 123 }, undefined);
        exercise.id = 5;
        exercise.isAtLeastInstructor = true;
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('exercise', exercise);

        comp.ngOnInit();

        expect(deletionService.resume).toHaveBeenCalledWith(expect.objectContaining({ entityType: EntityDeletionType.EXERCISE, entityId: 5 }), expect.anything());
    });

    it('should end the pending check for a running deletion when the reused component shows another exercise', () => {
        const deletionService = TestBed.inject(EntityDeletionService) as unknown as MockEntityDeletionService;
        const first = new TextExercise({ id: 123 }, undefined);
        first.id = 5;
        first.isAtLeastInstructor = true;
        const second = new TextExercise({ id: 123 }, undefined);
        second.id = 6;
        second.isAtLeastInstructor = true;
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('exercise', first);
        fixture.detectChanges();
        expect(deletionService.resume).toHaveBeenCalledOnce();
        let checkEnded = false;
        (deletionService.resume.mock.calls[0][1] as Observable<unknown>).subscribe(() => (checkEnded = true));
        fixture.detectChanges();
        expect(checkEnded).toBe(false);

        // the status response of the first exercise must not open its dialog over the second one
        fixture.componentRef.setInput('exercise', second);
        fixture.detectChanges();

        expect(checkEnded).toBe(true);
    });

    it('should look for a running deletion of the other exercise when the reused component shows it', () => {
        const deletionService = TestBed.inject(EntityDeletionService) as unknown as MockEntityDeletionService;
        const first = new TextExercise({ id: 123 }, undefined);
        first.id = 5;
        first.isAtLeastInstructor = true;
        const second = new TextExercise({ id: 123 }, undefined);
        second.id = 6;
        second.isAtLeastInstructor = true;
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('exercise', first);
        fixture.detectChanges();
        expect(deletionService.resume).toHaveBeenCalledOnce();

        fixture.componentRef.setInput('exercise', second);
        fixture.detectChanges();

        // Another instructor may be deleting the exercise that is shown now: its progress and its completion must not be missed.
        expect(deletionService.resume).toHaveBeenCalledTimes(2);
        expect(deletionService.resume.mock.calls[1][0]).toEqual(expect.objectContaining({ entityType: EntityDeletionType.EXERCISE, entityId: 6 }));
    });

    it('should not look for a running deletion of the other exercise when the user is no instructor of it', () => {
        const deletionService = TestBed.inject(EntityDeletionService) as unknown as MockEntityDeletionService;
        const first = new TextExercise({ id: 123 }, undefined);
        first.id = 5;
        first.isAtLeastInstructor = true;
        const second = new TextExercise({ id: 123 }, undefined);
        second.id = 6;
        second.isAtLeastInstructor = false;
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('exercise', first);
        fixture.detectChanges();

        fixture.componentRef.setInput('exercise', second);
        fixture.detectChanges();

        expect(deletionService.resume).toHaveBeenCalledOnce();
    });

    it('should end the pending check for a running deletion when the component is destroyed', () => {
        const deletionService = TestBed.inject(EntityDeletionService) as unknown as MockEntityDeletionService;
        const exercise = new TextExercise({ id: 123 }, undefined);
        exercise.id = 5;
        exercise.isAtLeastInstructor = true;
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        let checkEnded = false;
        (deletionService.resume.mock.calls[0][1] as Observable<unknown>).subscribe(() => (checkEnded = true));

        fixture.destroy();

        expect(checkEnded).toBe(true);
    });

    it('should only refresh the list and stay on the page when the outcome of the deletion is unknown', () => {
        const deletionService = TestBed.inject(EntityDeletionService) as unknown as MockEntityDeletionService;
        const router = TestBed.inject(Router);
        const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
        const broadcastSpy = vi.spyOn(TestBed.inject(EventManager), 'broadcast');
        vi.spyOn(TestBed.inject(TextExerciseService), 'delete').mockReturnValue(of({} as HttpResponse<any>));
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('exercise', new TextExercise({ id: 123 }, undefined));
        deletionService.status = EntityDeletionStatus.UNKNOWN;

        comp.deleteExercise();

        expect(navigateSpy).not.toHaveBeenCalled();
        expect(broadcastSpy).toHaveBeenCalledOnce();
        expect(broadcastSpy).toHaveBeenCalledWith(expect.objectContaining({ name: 'textExerciseListModification', content: expect.not.stringContaining('Deleted') }));
    });

    it('should not look for a running deletion when the user is no instructor', () => {
        const deletionService = TestBed.inject(EntityDeletionService) as unknown as MockEntityDeletionService;
        const exercise = new TextExercise({ id: 123 }, undefined);
        exercise.isAtLeastInstructor = false;
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('exercise', exercise);

        comp.ngOnInit();

        expect(deletionService.resume).not.toHaveBeenCalled();
    });
});

describe('Exercise detail common action rendering', () => {
    let fixture: ComponentFixture<NonProgrammingExerciseDetailCommonActionsComponent>;
    const course: Course = { id: 123 };
    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: EntityDeletionService, useClass: MockEntityDeletionService },
                provideHttpClient(),
                provideRouter([]),
                MockProvider(TextExerciseService),
                MockProvider(FileUploadExerciseService),
                MockProvider(ModelingExerciseService),
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(ProfileService, { isModuleFeatureActive: () => true }),
            ],
        });
        fixture = TestBed.createComponent(NonProgrammingExerciseDetailCommonActionsComponent);
        fixture.componentRef.setInput('course', course);
    });
    const exercise = (editor = true, instructor = true): TextExercise =>
        Object.assign(new TextExercise(course, undefined), {
            id: 456,
            title: 'Saved title',
            isAtLeastTutor: true,
            isAtLeastEditor: editor,
            isAtLeastInstructor: instructor,
        });

    it('keeps editing separate from navigation and deletion outside the toolbar', () => {
        fixture.componentRef.setInput('exercise', exercise());
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="exercise-editing-actions"] a').getAttribute('href')).toContain('/text-exercises/456/edit');
        expect(fixture.nativeElement.querySelector('[jhiDeleteButton]')).toBeNull();
    });

    it('names the compact edit link when its visible label is hidden', () => {
        fixture.componentRef.setInput('exercise', exercise());
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="exercise-editing-actions"] a').getAttribute('aria-label')).toBe('entity.action.edit');
    });

    it('offers exactly plagiarism and tutor effort in the secondary text menu', async () => {
        fixture.componentRef.setInput('exercise', exercise());
        fixture.detectChanges();
        fixture.nativeElement.querySelector('[data-testid="exercise-more-actions"]').click();
        await fixture.whenStable();
        const items = document.querySelectorAll('tumaet-ui-menu [tumAetUiMenuItem]');
        expect(items).toHaveLength(2);
        expect(items[0].getAttribute('href')).toContain('plagiarism');
        expect(items[1].getAttribute('href')).toContain('/text-exercises/456/tutor-effort-statistics');
    });

    it('removes the plagiarism destination when its module is disabled', async () => {
        fixture.componentRef.setInput('exercise', exercise());
        fixture.detectChanges();
        fixture.componentInstance.plagiarismEnabled.set(false);
        fixture.detectChanges();
        fixture.nativeElement.querySelector('[data-testid="exercise-more-actions"]').click();
        await fixture.whenStable();
        const plagiarism = document.querySelector('#check-plagiarism-download');
        expect(plagiarism?.getAttribute('aria-disabled')).toBe('true');
        expect(plagiarism?.hasAttribute('href')).toBe(false);
    });

    it('does not show editor or secondary actions to tutors', () => {
        fixture.componentRef.setInput('exercise', exercise(false, false));
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="exercise-editing-actions"] a')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="exercise-more-actions"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('a[href$="participations"]')).not.toBeNull();
    });

    it('restricts administration to instructors and renders it separately', () => {
        fixture.componentRef.setInput('exercise', exercise(false, false));
        fixture.componentRef.setInput('section', 'administration');
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[jhiDeleteButton]')).toBeNull();
        fixture.componentRef.setInput('exercise', exercise());
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[jhiDeleteButton]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('jhi-exercise-detail-actions')).toBeNull();
    });
});
