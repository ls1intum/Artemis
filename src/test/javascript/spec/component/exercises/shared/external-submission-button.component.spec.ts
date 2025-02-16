import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExternalSubmissionDialogComponent } from 'app/exercises/shared/external-submission/external-submission-dialog.component';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ExternalSubmissionButtonComponent } from 'app/exercises/shared/external-submission/external-submission-button.component';
import { Exercise } from 'app/entities/exercise.model';
import { By } from '@angular/platform-browser';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { MockProvider } from 'ng-mocks';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('External Submission Dialog', () => {
    let fixture: ComponentFixture<ExternalSubmissionButtonComponent>;
    let component: ExternalSubmissionButtonComponent;
    let modalService: NgbModal;

    beforeEach(() => {
        modalService = { open: jest.fn() } as any as NgbModal;
        TestBed.configureTestingModule({
            providers: [{ provide: NgbModal, useValue: modalService }, MockProvider(FeatureToggleService), { provide: TranslateService, useClass: MockTranslateService }],
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
