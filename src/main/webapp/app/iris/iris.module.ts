import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IrisMessageStore } from 'app/iris/message-store.service';

@NgModule({
    declarations: [],
    imports: [CommonModule],
    providers: [IrisMessageStore],
})
export class IrisModule {}
