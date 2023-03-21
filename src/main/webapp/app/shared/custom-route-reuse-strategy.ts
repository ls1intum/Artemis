import { ActivatedRouteSnapshot, DetachedRouteHandle, RouteReuseStrategy } from '@angular/router';

export class CustomRouteReuseStrategy implements RouteReuseStrategy {
    private storedRoutes: { [key: string]: DetachedRouteHandle } = {};

    shouldDetach(route: ActivatedRouteSnapshot): boolean {
        return route.data && route.data.reuse;
    }

    store(route: ActivatedRouteSnapshot, handle: DetachedRouteHandle): void {
        this.storedRoutes[route.routeConfig!.path!] = handle;
    }

    shouldAttach(route: ActivatedRouteSnapshot): boolean {
        return Object.prototype.hasOwnProperty.call(this.storedRoutes, route.routeConfig!.path!);
    }

    retrieve(route: ActivatedRouteSnapshot): DetachedRouteHandle | null {
        return this.storedRoutes[route.routeConfig!.path!];
    }

    shouldReuseRoute(future: ActivatedRouteSnapshot, current: ActivatedRouteSnapshot): boolean {
        return future.routeConfig === current.routeConfig;
    }
}
