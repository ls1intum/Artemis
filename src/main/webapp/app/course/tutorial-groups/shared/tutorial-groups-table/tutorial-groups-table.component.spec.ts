import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TutorialGroupsTableComponent } from './tutorial-groups-table.component';

describe('TutorialGroupsTableComponent', () => {
    let component: TutorialGroupsTableComponent;
    let fixture: ComponentFixture<TutorialGroupsTableComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [TutorialGroupsTableComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupsTableComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
