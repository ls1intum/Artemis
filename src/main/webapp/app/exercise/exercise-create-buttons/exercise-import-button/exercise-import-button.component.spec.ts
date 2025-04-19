import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ExerciseImportButtonComponent } from './exercise-import-button.component';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseImportWrapperComponent } from 'app/exercise/import/exercise-import-wrapper/exercise-import-wrapper.component';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockComponent } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { Router } from '@angular/router';

describe('ExerciseImportButtonComponent', () => {
    let component: ExerciseImportButtonComponent;
    let fixture: ComponentFixture<ExerciseImportButtonComponent>;
    let modalService: NgbModal;
    let router: Router;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MockComponent(FaIconComponent), ExerciseImportButtonComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: NgbModal, useClass: MockNgbModalService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseImportButtonComponent);
        component = fixture.componentInstance;
        modalService = TestBed.inject(NgbModal);
        fixture.componentRef.setInput('course', { id: 123 });
        router = TestBed.inject(Router);
        fixture.detectChanges();
    });

    it.each([ExerciseType.MODELING, ExerciseType.TEXT, ExerciseType.FILE_UPLOAD])('should open import modal', async (exerciseType: ExerciseType) => {
        fixture.componentRef.setInput('exerciseType', exerciseType);
        const promise = new Promise((resolve) => {
            resolve({ id: 2 } as Exercise);
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
                expect(routerSpy).toHaveBeenCalledExactlyOnceWith(['/course-management', 123, `${exerciseType}-exercises`, 2, 'import']);
            });
    });
});
