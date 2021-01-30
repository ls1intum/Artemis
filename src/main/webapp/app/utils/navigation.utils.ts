import { Router } from '@angular/router';

export const navigateBack = (router: Router, fallbackUrl: string[]): void => {
    if (window.history.length > 1) {
        window.history.back();
    } else {
        router.navigate(fallbackUrl);
    }
};
