import { DOCUMENT, Injectable, effect, inject, signal } from '@angular/core';

import { cs } from './translations/cs';
import { da } from './translations/da';
import { de } from './translations/de';
import { en } from './translations/en';
import { es } from './translations/es';
import { et } from './translations/et';
import { fr } from './translations/fr';
import { hr } from './translations/hr';
import { it } from './translations/it';
import { lt } from './translations/lt';
import { lv } from './translations/lv';
import { nb } from './translations/nb';
import { nl } from './translations/nl';
import { pl } from './translations/pl';
import { pt } from './translations/pt';
import { ro } from './translations/ro';
import { sv } from './translations/sv';
import { tr } from './translations/tr';
import { uk } from './translations/uk';

/** Languages offered in the switcher; `name` is the language's own name, so it is never translated. */
export const LANGUAGES = [
  { code: 'cs', name: 'Čeština', locale: 'cs-CZ' },
  { code: 'da', name: 'Dansk', locale: 'da-DK' },
  { code: 'de', name: 'Deutsch', locale: 'de-DE' },
  { code: 'et', name: 'Eesti', locale: 'et-EE' },
  { code: 'en', name: 'English', locale: 'en-US' },
  { code: 'es', name: 'Español', locale: 'es-ES' },
  { code: 'fr', name: 'Français', locale: 'fr-FR' },
  { code: 'hr', name: 'Hrvatski', locale: 'hr-HR' },
  { code: 'it', name: 'Italiano', locale: 'it-IT' },
  { code: 'lv', name: 'Latviešu', locale: 'lv-LV' },
  { code: 'lt', name: 'Lietuvių', locale: 'lt-LT' },
  { code: 'nl', name: 'Nederlands', locale: 'nl-NL' },
  { code: 'nb', name: 'Norsk', locale: 'nb-NO' },
  { code: 'pl', name: 'Polski', locale: 'pl-PL' },
  { code: 'pt', name: 'Português', locale: 'pt-PT' },
  { code: 'ro', name: 'Română', locale: 'ro-RO' },
  { code: 'sv', name: 'Svenska', locale: 'sv-SE' },
  { code: 'tr', name: 'Türkçe', locale: 'tr-TR' },
  { code: 'uk', name: 'Українська', locale: 'uk-UA' },
] as const;

export type Lang = (typeof LANGUAGES)[number]['code'];

// Typed against English, so a dictionary with a missing key fails the build.
const DICTIONARIES: Record<Lang, typeof en> = {
  cs, da, de, en, es, et, fr, hr, it, lt, lv, nb, nl, pl, pt, ro, sv, tr, uk,
};
const STORAGE_KEY = 'mcmanager.lang';

function isLang(value: string | null | undefined): value is Lang {
  return LANGUAGES.some((l) => l.code === value);
}

function detectInitialLang(): Lang {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (isLang(saved)) {
      return saved;
    }
  } catch {
    // localStorage unavailable (privacy mode, SSR, etc.) — fall back to browser language.
  }
  const nav = typeof navigator !== 'undefined' ? navigator.language : '';
  const base = nav?.toLowerCase().split('-')[0];
  // Browsers report Norwegian as nb, nn or no.
  const candidate = base === 'no' || base === 'nn' ? 'nb' : base;
  return isLang(candidate) ? candidate : 'en';
}

@Injectable({ providedIn: 'root' })
export class I18nService {
  readonly languages = LANGUAGES;
  readonly lang = signal<Lang>(detectInitialLang());

  constructor() {
    const doc = inject(DOCUMENT);
    effect(() => {
      doc.documentElement.lang = this.lang();
    });
  }

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
    return LANGUAGES.find((l) => l.code === this.lang())!.locale;
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
