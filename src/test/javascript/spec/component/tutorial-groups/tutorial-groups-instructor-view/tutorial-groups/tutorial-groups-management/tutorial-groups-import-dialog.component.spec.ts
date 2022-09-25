import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TutorialGroupsRegistrationImportDialog } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/tutorial-groups-management/tutorial-groups-import-dialog/tutorial-groups-registration-import-dialog.component';

describe('TutorialGroupsImportDialogComponent', () => {
    let component: TutorialGroupsRegistrationImportDialog;
    let fixture: ComponentFixture<TutorialGroupsRegistrationImportDialog>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [TutorialGroupsRegistrationImportDialog],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupsRegistrationImportDialog);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
