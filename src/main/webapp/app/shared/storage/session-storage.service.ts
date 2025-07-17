import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class SessionStorageService {
    /**
     * Stores a value in the session storage under the specified key.
     * @param key The key under which the value will be stored.
     * @param value The value to be stored, which will be stringified.
     */
    store<T>(key: string, value: T) {
        sessionStorage.setItem(key, JSON.stringify(value));
    }

    /**
     * Retrieves a value from the session storage by its key.
     * @param key The key to retrieve the value from session storage.
     * @returns The parsed value of type T or undefined if not found.
     */
    retrieve<T>(key: string): T | undefined {
        const value = sessionStorage.getItem(key) || undefined;
        return value ? (JSON.parse(value) as T) : undefined;
    }

    /**
     * Removes a specific key from the session storage.
     * @param key The key to remove from session storage.
     */
    remove(key: string) {
        sessionStorage.removeItem(key);
    }

    /**
     * Clears the entire session storage.
     */
    clear() {
        sessionStorage.clear();
    }
}
