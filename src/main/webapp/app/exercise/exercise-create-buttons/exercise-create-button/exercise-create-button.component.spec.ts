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
import { faFileUpload, faFont, faKeyboard, faProjectDiagram } from '@fortawesome/free-solid-svg-icons';
import { provideHttpClient } from '@angular/common/http';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';

describe('ExerciseCreateButtonComponent', () => {
    let component: ExerciseCreateButtonComponent;
    let fixture: ComponentFixture<ExerciseCreateButtonComponent>;
    let router: Router;
    let modalService: NgbModal;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FaIconComponent, ExerciseCreateButtonComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: DialogService, useClass: MockDialogService },
                provideHttpClient(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseCreateButtonComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('exerciseType', ExerciseType.MODELING);
        router = TestBed.inject(Router);
        modalService = TestBed.inject(NgbModal);
        fixture.componentRef.setInput('course', { id: 123 });
        fixture.detectChanges();
    });

    it.each([ExerciseType.MODELING, ExerciseType.FILE_UPLOAD, ExerciseType.TEXT, ExerciseType.QUIZ])('should link to' + ' exercise creation', (exerciseType: ExerciseType) => {
        fixture.componentRef.setInput('exerciseType', exerciseType);
        jest.spyOn(router, 'navigate');
        jest.spyOn(modalService, 'dismissAll');
        const beforeNavigateSpy = jest.spyOn(component.beforeNavigate, 'emit');

        component.linkToExerciseCreation();

        expect(beforeNavigateSpy).toHaveBeenCalledOnce();
        expect(modalService.dismissAll).toHaveBeenCalledOnce();
        expect(router.navigate).toHaveBeenCalledWith(['/course-management', 123, `${exerciseType}-exercises`, 'new']);
    });
    it.each([
        { exerciseType: ExerciseType.MODELING, expectedIcon: faProjectDiagram, expectedTranslationLabel: 'artemisApp.modelingExercise.home.createLabel' },
        { exerciseType: ExerciseType.FILE_UPLOAD, expectedIcon: faFileUpload, expectedTranslationLabel: 'artemisApp.fileUploadExercise.home.createLabel' },
        { exerciseType: ExerciseType.TEXT, expectedIcon: faFont, expectedTranslationLabel: 'artemisApp.textExercise.home.createLabel' },
        { exerciseType: ExerciseType.PROGRAMMING, expectedIcon: faKeyboard, expectedTranslationLabel: 'artemisApp.programmingExercise.home.createLabel' },
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
});
