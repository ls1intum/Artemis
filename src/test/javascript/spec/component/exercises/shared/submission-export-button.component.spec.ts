import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbModal, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseType } from 'app/entities/exercise.model';
import { SubmissionExportButtonComponent } from 'app/exercises/shared/submission-export/submission-export-button.component';
import { SubmissionExportDialogComponent } from 'app/exercises/shared/submission-export/submission-export-dialog.component';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockDirective, MockPipe } from 'ng-mocks';
import { MockNgbModalService } from '../../../helpers/mocks/service/mock-ngb-modal.service';
import { ArtemisTestModule } from '../../../test.module';

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
            imports: [ArtemisTestModule],
            declarations: [SubmissionExportDialogComponent, MockDirective(NgbTooltip), MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisTimeAgoPipe)],
            providers: [{ provide: NgbModal, useClass: MockNgbModalService }],
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
