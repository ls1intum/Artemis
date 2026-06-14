import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Router } from '@angular/router';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { ExerciseCreateButtonComponent } from 'app/exercise/exercise-create-buttons/exercise-create-button/exercise-create-button.component';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { provideHttpClient } from '@angular/common/http';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';

describe('ExerciseCreateButtonComponent', () => {
    setupTestBed({ zoneless: true });

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
        vi.spyOn(router, 'navigate');
        vi.spyOn(modalService, 'dismissAll');
        const beforeNavigateSpy = vi.spyOn(component.beforeNavigate, 'emit');

        component.linkToExerciseCreation();

        expect(beforeNavigateSpy).toHaveBeenCalledTimes(1);
        expect(modalService.dismissAll).toHaveBeenCalledTimes(1);
        expect(router.navigate).toHaveBeenCalledWith(['/course-management', 123, `${exerciseType}-exercises`, 'new']);
    });
});
