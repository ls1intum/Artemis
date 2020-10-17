import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ModuleCreationCardComponent } from 'app/lecture/lecture-component/lecture-component-management/module-creation-card/module-creation-card.component';

describe('ModuleCreationCardComponent', () => {
    let component: ModuleCreationCardComponent;
    let fixture: ComponentFixture<ModuleCreationCardComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [ModuleCreationCardComponent],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(ModuleCreationCardComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
