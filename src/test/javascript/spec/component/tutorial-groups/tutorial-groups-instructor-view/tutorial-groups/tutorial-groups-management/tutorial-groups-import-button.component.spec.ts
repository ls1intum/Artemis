import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';

import { TutorialGroupsImportButtonComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/tutorial-groups-management/tutorial-groups-import-button/tutorial-groups-import-button.component';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { MockNgbModalService } from '../../../../../helpers/mocks/service/mock-ngb-modal.service';
import { MockComponent, MockPipe } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TutorialGroupsRegistrationImportDialog } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/tutorial-groups-management/tutorial-groups-import-dialog/tutorial-groups-registration-import-dialog.component';

describe('TutorialGroupsImportButtonComponent', () => {
    let component: TutorialGroupsImportButtonComponent;
    let fixture: ComponentFixture<TutorialGroupsImportButtonComponent>;
    const exampleCourseId = 1;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [TutorialGroupsImportButtonComponent, MockComponent(FaIconComponent), MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: NgbModal, useClass: MockNgbModalService }],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupsImportButtonComponent);
        component = fixture.componentInstance;
        component.courseId = exampleCourseId;
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should open the import dialog when the button is clicked', fakeAsync(() => {
        // given
        const modalService = TestBed.inject(NgbModal);
        const mockModalRef = {
            componentInstance: { courseId: undefined },
            result: Promise.resolve(),
        };
        const modalOpenSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as unknown as NgbModalRef);

        fixture.detectChanges();
        const openDialogSpy = jest.spyOn(component, 'openTutorialGroupImportDialog');

        const importFinishSpy = jest.spyOn(component.importFinished, 'emit');

        const cancelButton = fixture.debugElement.nativeElement.querySelector('#importDialogButton');
        // when
        cancelButton.click();

        // then
        fixture.whenStable().then(() => {
            expect(openDialogSpy).toHaveBeenCalledOnce();
            expect(modalOpenSpy).toHaveBeenCalledOnce();
            expect(modalOpenSpy).toHaveBeenCalledWith(TutorialGroupsRegistrationImportDialog, { backdrop: 'static', scrollable: false, size: 'xl' });
            expect(mockModalRef.componentInstance.courseId).toEqual(exampleCourseId);
            expect(importFinishSpy).toHaveBeenCalledOnce();
        });
    }));
});
