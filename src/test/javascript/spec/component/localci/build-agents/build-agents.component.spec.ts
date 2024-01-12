import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BuildAgentsComponent } from 'app/localci/build-agents/build-agents.component';

describe('BuildAgentsComponent', () => {
    let component: BuildAgentsComponent;
    let fixture: ComponentFixture<BuildAgentsComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [BuildAgentsComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(BuildAgentsComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
