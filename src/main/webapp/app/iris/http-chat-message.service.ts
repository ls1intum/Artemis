import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { IrisHttpMessageService } from 'app/iris/http-message.service';

@Injectable({ providedIn: 'root' })
export class IrisHttpChatMessageService extends IrisHttpMessageService {
    constructor(httpClient: HttpClient) {
        super(httpClient, 'sessions');
    }
}
