import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { LoginService } from 'app/core/login/login.service';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';

@Component({
    selector: 'jhi-logout',
    template: '',
})
export class LogoutComponent implements OnInit {
    constructor(private router: Router, private loginService: LoginService, private participationWebsocketService: ParticipationWebsocketService) {}

    ngOnInit(): void {
        this.participationWebsocketService.resetLocalCache();
        this.loginService.logout(true);
    }
}
