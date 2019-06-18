import { Component, Input, OnInit } from '@angular/core';
import { DragItem } from '../../../entities/drag-item';
import { DeviceInfo, DeviceDetectorService } from 'ngx-device-detector';

@Component({
    selector: 'jhi-drag-item',
    templateUrl: './drag-item.component.html',
})
export class DragItemComponent implements OnInit {
    @Input() minWidth: string;
    @Input() dragItem: DragItem;
    @Input() clickDisabled: boolean;
    @Input() invalid: boolean;
    deviceInfo: DeviceInfo = null;
    isMobile = false;

    constructor(private deviceService: DeviceDetectorService) {}

    ngOnInit(): void {
        this.deviceInfo = this.deviceService.getDeviceInfo();
        this.isMobile = this.deviceService.isMobile();
    }
}
