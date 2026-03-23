import { ComponentRef } from '@angular/core';
import { NgbModalRef } from '@ng-bootstrap/ng-bootstrap';

export function getModalContentComponentRef<T>(modalRef: NgbModalRef): ComponentRef<T> {
    // ng-bootstrap 20.0.0 exposes only componentInstance publicly, but signal inputs need ComponentRef.setInput().
    // this workaround should be removed as soon as ng-bootstrap exposes a componentInstance publicly
    return (modalRef as unknown as { _contentRef: { componentRef: ComponentRef<T> } })._contentRef.componentRef;
}
