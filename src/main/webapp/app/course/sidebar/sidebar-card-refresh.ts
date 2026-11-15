import { Location } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';

/** Refreshes the active detail route while retaining each card's history behavior. */
export function refreshSidebarCard(
    router: Router,
    route: ActivatedRoute,
    location: Location,
    currentSelection: () => { id: number | string; selected: boolean },
): Promise<boolean> {
    return router.navigate(['../'], { skipLocationChange: true, relativeTo: route.firstChild }).then(() => {
        const selection = currentSelection();
        return selection.selected ? router.navigate(['./' + selection.id], { relativeTo: route }) : router.navigate([location.path(), selection.id], { replaceUrl: true });
    });
}
