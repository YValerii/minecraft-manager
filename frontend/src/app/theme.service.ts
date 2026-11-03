import { DOCUMENT, Injectable, inject, signal } from '@angular/core';

export type Theme = 'dark' | 'light';

/** The initial theme is chosen by the script in index.html, so the page never flashes the wrong one. */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly root = inject(DOCUMENT).documentElement;

  readonly theme = signal<Theme>(this.root.dataset['theme'] === 'light' ? 'light' : 'dark');

  toggle(): void {
    const next: Theme = this.theme() === 'dark' ? 'light' : 'dark';
    this.theme.set(next);
    this.root.dataset['theme'] = next;
    try {
      localStorage.setItem('mcmanager.theme', next);
    } catch {
      // Storage unavailable: the choice just lasts until the page is reloaded.
    }
  }
}
