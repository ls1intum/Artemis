import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CreateProgrammingButtonComponent } from './create-programming-button.component';
import { ActivatedRoute } from '@angular/router';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockComponent } from 'ng-mocks';

describe('ProgrammingCreateButton', () => {
    let component: CreateProgrammingButtonComponent;
    let fixture: ComponentFixture<CreateProgrammingButtonComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MockComponent(FaIconComponent)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, { provide: ActivatedRoute, useValue: new MockActivatedRoute() }, provideHttpClient()],
        }).compileComponents();

        fixture = TestBed.createComponent(CreateProgrammingButtonComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
