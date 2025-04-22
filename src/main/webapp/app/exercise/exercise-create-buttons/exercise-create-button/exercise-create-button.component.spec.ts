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
import { faFileUpload, faFont, faProjectDiagram } from '@fortawesome/free-solid-svg-icons';
import { provideHttpClient } from '@angular/common/http';

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
                provideHttpClient(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseCreateButtonComponent);
        component = fixture.componentInstance;
        router = TestBed.inject(Router);
        modalService = TestBed.inject(NgbModal);
        fixture.componentRef.setInput('course', { id: 123 });
        fixture.detectChanges();
    });

    it.each([ExerciseType.MODELING, ExerciseType.FILE_UPLOAD, ExerciseType.TEXT, ExerciseType.QUIZ])('should link to' + ' exercise creation', (exerciseType: ExerciseType) => {
        fixture.componentRef.setInput('exerciseType', exerciseType);
        jest.spyOn(router, 'navigate');
        jest.spyOn(modalService, 'dismissAll');

        component.linkToExerciseCreation();

        expect(modalService.dismissAll).toHaveBeenCalledOnce();
        expect(router.navigate).toHaveBeenCalledWith(['/course-management', 123, `${exerciseType}-exercises`, 'new']);
    });
    it.each([
        { exerciseType: ExerciseType.MODELING, expectedIcon: faProjectDiagram, expectedTranslationLabel: 'artemisApp.modelingExercise.home.createLabel' },
        { exerciseType: ExerciseType.FILE_UPLOAD, expectedIcon: faFileUpload, expectedTranslationLabel: 'artemisApp.fileUploadExercise.home.createLabel' },
        { exerciseType: ExerciseType.TEXT, expectedIcon: faFont, expectedTranslationLabel: 'artemisApp.textExercise.home.createLabel' },
    ])('should determine correct translation key and icon', ({ exerciseType, expectedIcon, expectedTranslationLabel }) => {
        fixture.componentRef.setInput('exerciseType', exerciseType);
        component.ngOnInit();
        expect(component.icon).toEqual(expectedIcon);
        expect(component.translationLabel).toEqual(expectedTranslationLabel);
    });
    it('should use translation key when provided', () => {
        fixture.componentRef.setInput('exerciseType', ExerciseType.MODELING);
        fixture.componentRef.setInput('translationKey', 'custom.translation.key');
        component.ngOnInit();
        expect(component.translationLabel).toBe('custom.translation.key');
    });

    it('should not set icon or translation label if exerciseType is not provided', () => {
        fixture.componentRef.setInput('exerciseType', undefined);
        component.ngOnInit();
        expect(component.icon).toBeUndefined();
        expect(component.translationLabel).toBeUndefined();
    });
});
