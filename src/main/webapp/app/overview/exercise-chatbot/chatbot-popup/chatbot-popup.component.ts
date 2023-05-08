import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';

@Component({
    selector: 'jhi-chatbot-popup',
    templateUrl: './chatbot-popup.component.html',
    styleUrls: ['./chatbot-popup.component.scss'],
})
export class ChatbotPopupComponent {
    firstName;
    @Inject(MAT_DIALOG_DATA) public data: { name: string };

    constructor(@Inject(MAT_DIALOG_DATA) data: { name: string }) {
        this.data = data;
        this.firstName = data.name;
    }
}
