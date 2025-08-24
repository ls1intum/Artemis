import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExerciseImportButtonComponent } from './exercise-import-button.component';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseImportWrapperComponent } from 'app/exercise/import/exercise-import-wrapper/exercise-import-wrapper.component';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { IconDefinition, faFileUpload, faFont, faKeyboard, faProjectDiagram } from '@fortawesome/free-solid-svg-icons';

describe('ExerciseImportButtonComponent', () => {
    let component: ExerciseImportButtonComponent;
    let fixture: ComponentFixture<ExerciseImportButtonComponent>;
    let modalService: NgbModal;
    let router: Router;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FaIconComponent, ExerciseImportButtonComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: NgbModal, useClass: MockNgbModalService },
                provideHttpClient(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseImportButtonComponent);
        component = fixture.componentInstance;
        modalService = TestBed.inject(NgbModal);
        fixture.componentRef.setInput('course', { id: 123 });
        fixture.componentRef.setInput('exerciseType', ExerciseType.MODELING);
        router = TestBed.inject(Router);
        fixture.detectChanges();
    });

    it.each([
        { exerciseType: ExerciseType.MODELING, id: 2 },
        { exerciseType: ExerciseType.TEXT, id: 2 },
        { exerciseType: ExerciseType.FILE_UPLOAD, id: 2 },
        { exerciseType: ExerciseType.PROGRAMMING, id: 2 },
        { exerciseType: ExerciseType.PROGRAMMING, id: undefined },
    ])('should open import modal', async ({ exerciseType, id }: { exerciseType: ExerciseType; id?: number }) => {
        fixture.componentRef.setInput('exerciseType', exerciseType);
        const promise = new Promise((resolve) => {
            resolve({ id, maxPoints: 1 } as Exercise);
        });
        const openSpy = jest.spyOn(modalService, 'open').mockReturnValue({ componentInstance: {}, result: promise } as NgbModalRef);
        const modalSpy = jest.spyOn(modalService, 'dismissAll');
        const routerSpy = jest.spyOn(router, 'navigate').mockReturnValue(Promise.resolve(true));

        component.openImportModal();

        expect(openSpy).toHaveBeenCalledOnce();
        expect(openSpy).toHaveBeenCalledWith(ExerciseImportWrapperComponent, { size: 'lg', backdrop: 'static' });

        await expect(promise)
            .toResolve()
            .then(() => {
                expect(modalSpy).toHaveBeenCalledOnce();
                if (id && exerciseType === ExerciseType.PROGRAMMING) {
                    expect(routerSpy).toHaveBeenCalledExactlyOnceWith(['/course-management', 123, `${exerciseType}-exercises`, 'import', 2]);
                } else if (!id && exerciseType === ExerciseType.PROGRAMMING) {
                    expect(routerSpy).toHaveBeenCalledWith(['/course-management', 123, 'programming-exercises', 'import-from-file'], {
                        state: { programmingExerciseForImportFromFile: { id: undefined, maxPoints: 1 } },
                    });
                } else {
                    expect(routerSpy).toHaveBeenCalledExactlyOnceWith(['/course-management', 123, `${exerciseType}-exercises`, 2, 'import']);
                }
            });
    });

    it.each([
        { exerciseType: ExerciseType.MODELING, expectedIcon: faProjectDiagram, expectedTranslationLabel: 'artemisApp.modelingExercise.home.importLabel' },
        { exerciseType: ExerciseType.FILE_UPLOAD, expectedIcon: faFileUpload, expectedTranslationLabel: 'artemisApp.fileUploadExercise.home.importLabel' },
        { exerciseType: ExerciseType.TEXT, expectedIcon: faFont, expectedTranslationLabel: 'artemisApp.textExercise.home.importLabel' },
        { exerciseType: ExerciseType.PROGRAMMING, expectedIcon: faKeyboard, expectedTranslationLabel: 'artemisApp.programmingExercise.home.importLabel' },
    ])(
        'should determine correct translation key and icon',
        ({ exerciseType, expectedIcon, expectedTranslationLabel }: { exerciseType: ExerciseType; expectedIcon: IconDefinition; expectedTranslationLabel: string }) => {
            fixture.componentRef.setInput('exerciseType', exerciseType);
            component.ngOnInit();
            expect(component.icon).toEqual(expectedIcon);
            expect(component.translationLabel).toEqual(expectedTranslationLabel);
        },
    );
});
