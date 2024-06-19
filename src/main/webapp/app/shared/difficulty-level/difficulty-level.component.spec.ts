/* tslint:disable:no-unused-variable */
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DifficultyLevelComponent } from './difficulty-level.component';

describe('DifficultyLevelComponent', () => {
    let component: DifficultyLevelComponent;
    let fixture: ComponentFixture<DifficultyLevelComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [DifficultyLevelComponent],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(DifficultyLevelComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
