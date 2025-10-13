import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ServerAdministration } from './server-administration';

describe('ServerAdministration', () => {
    let component: ServerAdministration;
    let fixture: ComponentFixture<ServerAdministration>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ServerAdministration],
        }).compileComponents();

        fixture = TestBed.createComponent(ServerAdministration);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
