import { ComponentFixture, TestBed } from '@angular/core/testing';

import { IrisSettingsComponent } from './iris-settings.component';

describe('IrisSettingsComponent', () => {
    let component: IrisSettingsComponent;
    let fixture: ComponentFixture<IrisSettingsComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [IrisSettingsComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(IrisSettingsComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
