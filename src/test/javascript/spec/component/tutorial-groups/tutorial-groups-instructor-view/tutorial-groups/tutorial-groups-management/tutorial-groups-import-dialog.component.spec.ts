import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TutorialGroupsImportDialogComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/tutorial-groups-management/tutorial-groups-import-dialog/tutorial-groups-import-dialog.component';

describe('TutorialGroupsImportDialogComponent', () => {
    let component: TutorialGroupsImportDialogComponent;
    let fixture: ComponentFixture<TutorialGroupsImportDialogComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [TutorialGroupsImportDialogComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupsImportDialogComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
