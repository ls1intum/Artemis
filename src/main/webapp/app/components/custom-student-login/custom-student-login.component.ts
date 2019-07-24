import { Component, EventEmitter, Output, OnInit } from '@angular/core';
import { CUSTOM_STUDENT_LOGIN_KEY } from 'app/app.constants';
import { UserService } from 'app/core';
import { LocalStorageService } from 'ngx-webstorage';

@Component({
    selector: 'jhi-custom-student-login',
    templateUrl: './custom-student-login.component.html',
})
export class CustomStudentLoginComponent implements OnInit {
    @Output() loginSuccess = new EventEmitter();
    loading = false;
    public startLoginProcess = false;
    public selectedUserLogin: string | null;
    public userNotFound: boolean;

    constructor(private userService: UserService, private localStorageService: LocalStorageService) {}

    ngOnInit(): void {
        if (this.localStorageService.retrieve(CUSTOM_STUDENT_LOGIN_KEY)) {
            this.selectedUserLogin = this.localStorageService.retrieve(CUSTOM_STUDENT_LOGIN_KEY);
        }
    }

    startUsingLogin(): void {
        if (!this.selectedUserLogin) {
            this.startLoginProcess = false;
            this.localStorageService.clear(CUSTOM_STUDENT_LOGIN_KEY);
            return;
        }
        this.userService.find(this.selectedUserLogin).subscribe(
            res => {
                this.startLoginProcess = false;
                if (res.body) {
                    this.localStorageService.store(CUSTOM_STUDENT_LOGIN_KEY, this.selectedUserLogin);
                    this.loginSuccess.emit();
                } else {
                }
            },
            error => {
                this.userNotFound = true;
                this.startLoginProcess = false;
                this.selectedUserLogin = null;
                this.localStorageService.clear(CUSTOM_STUDENT_LOGIN_KEY);
                setTimeout(() => {
                    this.userNotFound = false;
                }, 1500);
            },
        );
    }

    removeUserLogin(): void {
        this.selectedUserLogin = null;
        this.startLoginProcess = false;
        this.localStorageService.clear(CUSTOM_STUDENT_LOGIN_KEY);
        this.loginSuccess.emit();
    }
}
