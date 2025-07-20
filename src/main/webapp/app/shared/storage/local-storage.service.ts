import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class LocalStorageService {
    /**
     * Stores a value in the local storage under the specified key.
     * @param key The key under which the value will be stored.
     * @param value The value to be stored, which will be stringified.
     */
    store<T>(key: string, value: T) {
        localStorage.setItem(key, JSON.stringify(value));
    }

    /**
     * Retrieves a value from the local storage by its key.
     * @param key The key to retrieve the value from local storage.
     * @returns The parsed value of type T or undefined if not found.
     */
    retrieve<T>(key: string): T | undefined {
        const raw = localStorage.getItem(key) || undefined;
        if (!raw) return undefined;
        const value = JSON.parse(raw);
        const date: Date | undefined = this.parseDate(value);
        if (date) {
            return date;
        }
        return value as T;
    }

    private parseDate(value: string): Date | undefined {
        const parsed = new Date(value);
        return !isNaN(parsed.getTime()) && value === parsed.toISOString() ? parsed : undefined;
    }

    /**
     * Removes a specific key from the local storage.
     * @param key The key to remove from local storage.
     */
    remove(key: string) {
        localStorage.removeItem(key);
    }

    /**
     * Clears the entire local storage.
     */
    clear() {
        localStorage.clear();
    }
}
