import { ComponentFixture, TestBed } from '@angular/core/testing';

import { NewTutorialGroupDetailComponent } from './new-tutorial-group-detail.component';

describe('NewTutorialGroupDetail', () => {
    let component: NewTutorialGroupDetailComponent;
    let fixture: ComponentFixture<NewTutorialGroupDetailComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [NewTutorialGroupDetailComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(NewTutorialGroupDetailComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
