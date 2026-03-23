import { ComponentRef } from '@angular/core';
import { NgbModalRef } from '@ng-bootstrap/ng-bootstrap';

export function getModalContentComponentRef<T>(modalRef: NgbModalRef): ComponentRef<T> | undefined {
    // ng-bootstrap 20.0.0 exposes only componentInstance publicly, but signal inputs need ComponentRef.setInput().
    return (modalRef as unknown as { _contentRef?: { componentRef?: ComponentRef<T> } })._contentRef?.componentRef;
}
