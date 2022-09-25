import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TutorialGroupsImportButtonComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/tutorial-groups-management/tutorial-groups-import-button/tutorial-groups-import-button.component';

describe('TutorialGroupsImportButtonComponent', () => {
    let component: TutorialGroupsImportButtonComponent;
    let fixture: ComponentFixture<TutorialGroupsImportButtonComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [TutorialGroupsImportButtonComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupsImportButtonComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
