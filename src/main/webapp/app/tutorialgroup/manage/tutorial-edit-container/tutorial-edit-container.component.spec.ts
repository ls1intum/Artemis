import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TutorialEditContainerComponent } from './tutorial-edit-container.component';

describe('TutorialEditContainer', () => {
    let component: TutorialEditContainerComponent;
    let fixture: ComponentFixture<TutorialEditContainerComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TutorialEditContainerComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialEditContainerComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
