import { TestBed } from '@angular/core/testing';

import { I18nService, LANGUAGES } from './i18n.service';
import { en } from './translations/en';

/** Every translated string in English, as dotted keys. */
function leafKeys(node: object, prefix = ''): string[] {
  return Object.entries(node).flatMap(([k, v]) =>
    typeof v === 'string' ? [prefix + k] : leafKeys(v as object, `${prefix}${k}.`),
  );
}

function placeholders(text: string): string[] {
  return [...new Set(text.match(/\{\w+\}/g) ?? [])].sort();
}

describe('I18nService', () => {
  let saved: Record<string, string>;
  let storage: { getItem: ReturnType<typeof vi.fn>; setItem: ReturnType<typeof vi.fn> };

  function browserLanguage(language: string | undefined): void {
    vi.stubGlobal('navigator', { language });
  }

  beforeEach(() => {
    saved = {};
    storage = {
      getItem: vi.fn((k: string) => saved[k] ?? null),
      setItem: vi.fn((k: string, v: string) => {
        saved[k] = v;
      }),
    };
    vi.stubGlobal('localStorage', storage);
    browserLanguage('en-US');
  });

  afterEach(() => vi.unstubAllGlobals());

  describe('initial language', () => {
    it('uses the saved language first', () => {
      saved['mcmanager.lang'] = 'de';
      browserLanguage('fr-FR');
      expect(TestBed.inject(I18nService).lang()).toBe('de');
    });

    it('ignores a saved value that is not a language', () => {
      saved['mcmanager.lang'] = 'klingon';
      browserLanguage('fr-FR');
      expect(TestBed.inject(I18nService).lang()).toBe('fr');
    });

    it('falls back to the browser when storage is unavailable', () => {
      storage.getItem.mockImplementation(() => {
        throw new Error('denied');
      });
      browserLanguage('pl');
      expect(TestBed.inject(I18nService).lang()).toBe('pl');
    });

    it('matches on the base language, ignoring case', () => {
      browserLanguage('UK-ua');
      expect(TestBed.inject(I18nService).lang()).toBe('uk');
    });

    for (const code of ['no', 'nn', 'nb-NO']) {
      it(`treats ${code} as Norwegian`, () => {
        browserLanguage(code);
        expect(TestBed.inject(I18nService).lang()).toBe('nb');
      });
    }

    it('uses English for an unsupported language', () => {
      browserLanguage('ja-JP');
      expect(TestBed.inject(I18nService).lang()).toBe('en');
    });

    it('uses English when the browser reports no language', () => {
      browserLanguage(undefined);
      expect(TestBed.inject(I18nService).lang()).toBe('en');
    });

    it('uses English when there is no browser', () => {
      vi.stubGlobal('navigator', undefined);
      expect(TestBed.inject(I18nService).lang()).toBe('en');
    });
  });

  describe('setLang', () => {
    it('changes the language, saves it and updates the page', () => {
      const service = TestBed.inject(I18nService);

      service.setLang('it');
      TestBed.tick();

      expect(service.lang()).toBe('it');
      expect(saved['mcmanager.lang']).toBe('it');
      expect(document.documentElement.lang).toBe('it');
    });

    it('still changes the language when storage is unavailable', () => {
      storage.setItem.mockImplementation(() => {
        throw new Error('denied');
      });
      const service = TestBed.inject(I18nService);

      service.setLang('it');

      expect(service.lang()).toBe('it');
    });
  });

  it('gives the locale of the active language', () => {
    const service = TestBed.inject(I18nService);
    service.setLang('pt');
    expect(service.locale()).toBe('pt-PT');
  });

  describe('t', () => {
    let service: I18nService;

    beforeEach(() => {
      service = TestBed.inject(I18nService);
      service.setLang('en');
    });

    it('finds nested strings', () => {
      expect(service.t('langSwitcher.ariaLabel')).toBe(en.langSwitcher.ariaLabel);
    });

    it('returns the key when there is no such string', () => {
      expect(service.t('nope')).toBe('nope');
      expect(service.t('app.nope')).toBe('app.nope');
      expect(service.t('nope.deeper.still')).toBe('nope.deeper.still');
      expect(service.t('langSwitcher.ariaLabel.tooDeep')).toBe('langSwitcher.ariaLabel.tooDeep');
    });

    it('returns the key when it names a group of strings', () => {
      expect(service.t('app')).toBe('app');
    });

    it('fills in placeholders', () => {
      expect(service.t('format.minutesAgo', { n: 5 })).toBe(en.format.minutesAgo.replace('{n}', '5'));
    });

    it('fills in a placeholder at the end of an unknown key too', () => {
      expect(service.t('hello {who} and {other}', { who: 'you' })).toBe('hello you and {other}');
    });

    // Known bugs: these state the correct behaviour. Remove `.fails` when the code is fixed.
    it.fails('fills in every occurrence of a placeholder', () => {
      expect(service.t('{x} and {x}', { x: 'a' })).toBe('a and a');
    });

    it.fails('inserts values literally, even ones containing $', () => {
      expect(service.t('name: {x}', { x: '$&' })).toBe('name: $&');
    });
  });

  describe('dictionaries', () => {
    const keys = leafKeys(en);

    it('has English strings to compare against', () => {
      expect(keys.length).toBeGreaterThan(50);
    });

    for (const { code } of LANGUAGES) {
      it(`${code} has every string, not blank, with the same placeholders`, () => {
        const service = TestBed.inject(I18nService);
        service.setLang('en');
        const english = keys.map((k) => service.t(k));
        service.setLang(code);

        keys.forEach((key, i) => {
          const text = service.t(key);
          expect(text, `${code}: ${key} is missing`).not.toBe(key);
          expect(text.trim(), `${code}: ${key} is blank`).not.toBe('');
          expect(placeholders(text), `${code}: ${key} placeholders`).toEqual(placeholders(english[i]));
        });
      });
    }
  });
});
