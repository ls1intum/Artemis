import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { SubmissionExportDialogComponent } from 'app/exercise/submission-export/dialog/submission-export-dialog.component';
import { SubmissionExportButtonComponent } from 'app/exercise/submission-export/button/submission-export-button.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MockNgbModalService } from '../../../../../../test/javascript/spec/helpers/mocks/service/mock-ngb-modal.service';
import { MockTranslateService } from '../../../../../../test/javascript/spec/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

class MockNgbModalRef {
    componentInstance = {
        exerciseId: undefined,
        exerciseType: undefined,
    };
    result: Promise<boolean> = Promise.resolve(true);
}

describe('Submission Export Button Component', () => {
    let fixture: ComponentFixture<SubmissionExportButtonComponent>;
    let component: SubmissionExportButtonComponent;
    let modalService: NgbModal;
    let mouseEvent: MouseEvent;

    const exerciseId = 1;
    const exerciseType = ExerciseType.TEXT;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [SubmissionExportDialogComponent, MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisTimeAgoPipe)],
            providers: [
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(SubmissionExportButtonComponent);
                component = fixture.componentInstance;

                modalService = TestBed.inject(NgbModal);

                component.exerciseId = exerciseId;
                component.exerciseType = exerciseType;

                mouseEvent = new MouseEvent('mouseEvent');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should open export submission dialog', () => {
        const mouseEventSpy = jest.spyOn(mouseEvent, 'stopPropagation');
        const openSpy = jest.spyOn(modalService, 'open');

        component.openSubmissionExportDialog(mouseEvent);

        expect(mouseEventSpy).toHaveBeenCalledOnce();
        expect(openSpy).toHaveBeenCalledOnce();
        expect(openSpy).toHaveBeenCalledWith(SubmissionExportDialogComponent, { keyboard: true, size: 'lg' });
    });

    it('should set input values for dialog', () => {
        const mockModalRef = new MockNgbModalRef();
        modalService.open = jest.fn().mockReturnValue(mockModalRef);

        component.openSubmissionExportDialog(mouseEvent);

        expect(mockModalRef.componentInstance.exerciseId).toBe(exerciseId);
        expect(mockModalRef.componentInstance.exerciseType).toBe(exerciseType);
    });
});
