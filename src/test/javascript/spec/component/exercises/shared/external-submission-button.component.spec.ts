import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgForm, NgModel } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { NgbModal, NgbModalRef, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { Exercise } from 'app/entities/exercise.model';
import { ExternalSubmissionButtonComponent } from 'app/exercises/shared/external-submission/external-submission-button.component';
import { ExternalSubmissionDialogComponent } from 'app/exercises/shared/external-submission/external-submission-dialog.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockDirective } from 'ng-mocks';
import { TranslatePipeMock } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../test.module';

describe('External Submission Dialog', () => {
    let fixture: ComponentFixture<ExternalSubmissionButtonComponent>;
    let component: ExternalSubmissionButtonComponent;
    let modalService: NgbModal;

    beforeEach(() => {
        modalService = { open: jest.fn() } as any as NgbModal;
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                ExternalSubmissionButtonComponent,
                ButtonComponent,
                TranslatePipeMock,
                MockDirective(TranslateDirective),
                MockDirective(NgForm),
                MockDirective(NgModel),
                MockDirective(NgbTooltip),
                MockDirective(FeatureToggleDirective),
            ],
            providers: [{ provide: NgbModal, useValue: modalService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExternalSubmissionButtonComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should open external submission dialog on click', () => {
        const exercise = { id: 1 } as Exercise;
        component.exercise = exercise;
        const modalRefMock = { componentInstance: {} } as NgbModalRef;
        const openMock = jest.spyOn(modalService, 'open').mockReturnValue(modalRefMock);

        fixture.detectChanges();
        fixture.debugElement.query(By.css('.btn')).nativeElement.click();

        expect(openMock).toHaveBeenCalledOnce();
        expect(openMock).toHaveBeenCalledWith(ExternalSubmissionDialogComponent, { keyboard: true, size: 'lg', backdrop: 'static' });
        expect(modalRefMock.componentInstance.exercise).toBe(exercise);
    });
});
