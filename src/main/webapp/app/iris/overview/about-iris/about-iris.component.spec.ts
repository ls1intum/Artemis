// src/main/webapp/app/iris/overview/about-iris/about-iris.component.spec.ts
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { By } from '@angular/platform-browser';

import { AboutIrisComponent } from './about-iris.component';

import { TranslateLoader, TranslateModule, TranslateService } from '@ngx-translate/core';
import { TranslateFakeLoader } from '@ngx-translate/core';

describe('AboutIrisComponent', () => {
    let component: AboutIrisComponent;
    let fixture: ComponentFixture<AboutIrisComponent>;

    beforeEach(waitForAsync(async () => {
        await TestBed.configureTestingModule({
            imports: [
                AboutIrisComponent,
                TranslateModule.forRoot({
                    loader: { provide: TranslateLoader, useClass: TranslateFakeLoader },
                }),
            ],
        })
            .overrideComponent(AboutIrisComponent, {
                set: {
                    template: `<jhi-iris-logo></jhi-iris-logo>`,
                },
            })
            .compileComponents();

        const translate = TestBed.inject(TranslateService);
        translate.setDefaultLang('en');
        translate.use('en');

        fixture = TestBed.createComponent(AboutIrisComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    }));

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should render the Iris logo element', () => {
        const logo = fixture.debugElement.query(By.css('jhi-iris-logo'));
        expect(logo).toBeTruthy();
    });

    it('should have faRobot icon defined', () => {
        expect(component.faRobot).toBeTruthy();
    });

    it('should define bulletPoints with expected keys and values', () => {
        const bp = component.bulletPoints;
        expect(Object.keys(bp)).toHaveLength(4);
        expect(bp['1']).toBe(2);
        expect(bp['2']).toBe(5);
        expect(bp['3']).toBe(3);
        expect(bp['4']).toBe(5);
    });
});
