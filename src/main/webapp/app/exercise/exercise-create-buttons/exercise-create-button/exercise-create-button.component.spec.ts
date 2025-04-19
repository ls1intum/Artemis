import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Router } from '@angular/router';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { ExerciseCreateButtonComponent } from 'app/exercise/exercise-create-buttons/exercise-create-button/exercise-create-button.component';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockComponent } from 'ng-mocks';

describe('ExerciseCreateButtonComponent', () => {
    let component: ExerciseCreateButtonComponent;
    let fixture: ComponentFixture<ExerciseCreateButtonComponent>;
    let router: Router;
    let modalService: NgbModal;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MockComponent(FaIconComponent), ExerciseCreateButtonComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: NgbModal, useClass: MockNgbModalService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseCreateButtonComponent);
        component = fixture.componentInstance;
        router = TestBed.inject(Router);
        modalService = TestBed.inject(NgbModal);
        fixture.componentRef.setInput('course', { id: 123 });
        fixture.detectChanges();
    });

    it.each([ExerciseType.MODELING, ExerciseType.FILE_UPLOAD, ExerciseType.TEXT])('should link to exercise creation', (exerciseType: ExerciseType) => {
        fixture.componentRef.setInput('exerciseType', exerciseType);
        jest.spyOn(router, 'navigate');
        jest.spyOn(modalService, 'dismissAll');

        component.linkToExerciseCreation();

        expect(modalService.dismissAll).toHaveBeenCalledOnce();
        expect(router.navigate).toHaveBeenCalledWith(['/course-management', 123, `${exerciseType}-exercises`, 'new']);
    });
});
