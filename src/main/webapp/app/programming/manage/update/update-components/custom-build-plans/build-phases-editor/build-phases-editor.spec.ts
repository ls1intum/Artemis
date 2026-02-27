import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BuildPhasesEditor } from './build-phases-editor';

describe('BuildPhasesEditor', () => {
    let component: BuildPhasesEditor;
    let fixture: ComponentFixture<BuildPhasesEditor>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [BuildPhasesEditor],
        }).compileComponents();

        fixture = TestBed.createComponent(BuildPhasesEditor);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
