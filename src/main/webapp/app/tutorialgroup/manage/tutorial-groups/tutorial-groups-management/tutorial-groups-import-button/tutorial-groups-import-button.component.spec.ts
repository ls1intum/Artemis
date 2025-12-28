import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { TutorialGroupsImportButtonComponent } from 'app/tutorialgroup/manage/tutorial-groups/tutorial-groups-management/tutorial-groups-import-button/tutorial-groups-import-button.component';
import { MockComponent, MockPipe } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TutorialGroupsRegistrationImportDialogComponent } from 'app/tutorialgroup/manage/tutorial-groups/tutorial-groups-management/tutorial-groups-import-dialog/tutorial-groups-registration-import-dialog.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('TutorialGroupsImportButtonComponent', () => {
    let component: TutorialGroupsImportButtonComponent;
    let fixture: ComponentFixture<TutorialGroupsImportButtonComponent>;
    const exampleCourseId = 1;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [TutorialGroupsImportButtonComponent, MockComponent(FaIconComponent), MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupsImportButtonComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', exampleCourseId);
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should open the import dialog when the button is clicked', fakeAsync(() => {
        const mockImportDialog = { open: jest.fn() } as unknown as TutorialGroupsRegistrationImportDialogComponent;
        jest.spyOn(component, 'importDialog').mockReturnValue(mockImportDialog);
        const openDialogSpy = jest.spyOn(component, 'openTutorialGroupImportDialog');

        const importButton = fixture.debugElement.nativeElement.querySelector('#importDialogButton');
        importButton.click();

        fixture.whenStable().then(() => {
            expect(openDialogSpy).toHaveBeenCalledOnce();
            expect(mockImportDialog.open).toHaveBeenCalledOnce();
        });
    }));

    it('should show warning dialog when import is completed', fakeAsync(() => {
        component.onImportCompleted();

        fixture.whenStable().then(() => {
            expect(component.warningDialogVisible()).toBeTrue();
        });
    }));

    it('should close warning dialog and emit importFinished when closeWarningDialog is called', fakeAsync(() => {
        const importFinishedSpy = jest.spyOn(component.importFinished, 'emit');
        component.warningDialogVisible.set(true);

        component.closeWarningDialog();

        fixture.whenStable().then(() => {
            expect(component.warningDialogVisible()).toBeFalse();
            expect(importFinishedSpy).toHaveBeenCalledOnce();
        });
    }));
});
