import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ActivatedRouteSnapshot, Route } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { AccountService } from 'app/core/auth/account.service';
import { RouterTestingModule } from '@angular/router/testing';
import { CookieService } from 'ngx-cookie-service';
import { DeviceDetectorService } from 'ngx-device-detector';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { CourseExerciseDetailsComponent } from 'app/overview/exercise-details/course-exercise-details.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { ArtemisTestModule } from '../../../test.module';

chai.use(sinonChai);
const expect = chai.expect;

describe('UserRouteAccessService', () => {
    let comp: CourseExerciseDetailsComponent;
    let fixture: ComponentFixture<CourseExerciseDetailsComponent>;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [CourseExerciseDetailsComponent],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseExerciseDetailsComponent);
                comp = fixture.componentInstance;
            });
    }));

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).to.be.ok;
    });
});
