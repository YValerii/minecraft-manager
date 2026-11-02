import { Injectable, signal } from '@angular/core';

import { en } from './translations/en';
import { uk } from './translations/uk';

export type Lang = 'en' | 'uk';

const DICTIONARIES: Record<Lang, unknown> = { en, uk };
const STORAGE_KEY = 'mcmanager.lang';

function detectInitialLang(): Lang {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved === 'en' || saved === 'uk') {
      return saved;
    }
  } catch {
    // localStorage unavailable (privacy mode, SSR, etc.) — fall back to browser language.
  }
  const nav = typeof navigator !== 'undefined' ? navigator.language : '';
  return nav?.toLowerCase().startsWith('uk') ? 'uk' : 'en';
}

@Injectable({ providedIn: 'root' })
export class I18nService {
  readonly lang = signal<Lang>(detectInitialLang());

  setLang(lang: Lang): void {
    this.lang.set(lang);
    try {
      localStorage.setItem(STORAGE_KEY, lang);
    } catch {
      // ignore
    }
  }

  /** BCP-47 locale tag matching the active language, for Intl/toLocaleString calls. */
  locale(): string {
    return this.lang() === 'uk' ? 'uk-UA' : 'en-US';
  }

  t(key: string, params?: Record<string, string | number>): string {
    const dict = DICTIONARIES[this.lang()];
    let value: unknown = dict;
    for (const part of key.split('.')) {
      if (value == null || typeof value !== 'object') {
        value = undefined;
        break;
      }
      value = (value as Record<string, unknown>)[part];
    }
    let text = typeof value === 'string' ? value : key;
    if (params) {
      for (const [k, v] of Object.entries(params)) {
        text = text.replace(`{${k}}`, String(v));
      }
    }
    return text;
  }
}
