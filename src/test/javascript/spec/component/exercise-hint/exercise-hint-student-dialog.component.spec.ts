import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';
import { of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';

import { ExerciseHintUpdateComponent } from 'app/exercises/shared/exercise-hint/manage/exercise-hint-update.component';
import { ArtemisTestModule } from '../../test.module';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';
import { ExerciseHintService } from 'app/exercises/shared/exercise-hint/manage/exercise-hint.service';
import { ExerciseHint } from 'app/entities/exercise-hint.model';
import { MockExerciseService } from '../../helpers/mocks/service/mock-exercise.service';
import { ExerciseHintStudentComponent, ExerciseHintStudentDialogComponent } from 'app/exercises/shared/exercise-hint/participate/exercise-hint-student-dialog.component';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';

describe('ExerciseHint Hint Student Component', () => {
    let comp: ExerciseHintStudentComponent;
    let fixture: ComponentFixture<ExerciseHintStudentComponent>;
    let service: ExerciseHintService;
    let modalService: NgbModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ExerciseHintUpdateComponent],
            providers: [FormBuilder, MockExerciseService, MockProvider(TranslateService), { provide: NgbModal, useClass: MockNgbModalService }],
        })
            .overrideTemplate(ExerciseHintUpdateComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(ExerciseHintStudentComponent);
        comp = fixture.componentInstance;
        service = fixture.debugElement.injector.get(ExerciseHintService);
        modalService = TestBed.inject(NgbModal);
    });

    it('should load all hints onInit', () => {
        const exerciseHint = new ExerciseHint();
        exerciseHint.id = 123;
        comp.exerciseId = 15;
        const headers = new HttpHeaders().append('link', 'link;link');
        const findByExerciseIdSpy = jest.spyOn(service, 'findByExerciseId').mockReturnValue(
            of(
                new HttpResponse({
                    body: [exerciseHint],
                    headers,
                }),
            ),
        );

        comp.ngOnInit();

        expect(findByExerciseIdSpy).toHaveBeenCalledTimes(1);
        expect(findByExerciseIdSpy).toHaveBeenCalledWith(15);
        expect(comp.exerciseHints).toEqual([exerciseHint]);
    });

    it('should open modal with exercise hints', () => {
        const exerciseHint = new ExerciseHint();
        exerciseHint.id = 123;
        comp.exerciseHints = [exerciseHint];

        const componentInstance = { exerciseHints: [] };
        const result = new Promise((resolve) => resolve(true));
        const openModalSpy = jest.spyOn(modalService, 'open').mockReturnValue(<NgbModalRef>{
            componentInstance,
            result,
        });

        comp.openModal();

        expect(openModalSpy).toHaveBeenCalledTimes(1);
        expect(openModalSpy).toHaveBeenCalledWith(ExerciseHintStudentDialogComponent, { size: 'lg', backdrop: 'static' });
        expect(componentInstance.exerciseHints).toEqual([exerciseHint]);
    });
});
