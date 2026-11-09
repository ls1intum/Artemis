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
import { of } from 'rxjs';
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

describe('Exercise detail common actions Component', () => {
    let comp: NonProgrammingExerciseDetailCommonActionsComponent;
    let fixture: ComponentFixture<NonProgrammingExerciseDetailCommonActionsComponent>;

    const course: Course = { id: 123 };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [NonProgrammingExerciseDetailCommonActionsComponent],
            providers: [
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
        const deleteFileUploadExerciseStub = vi.spyOn(fileUploadExerciseService, 'delete').mockReturnValue(of(undefined));
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
});

describe('Exercise detail common action rendering', () => {
    let fixture: ComponentFixture<NonProgrammingExerciseDetailCommonActionsComponent>;
    const course: Course = { id: 123 };
    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
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
