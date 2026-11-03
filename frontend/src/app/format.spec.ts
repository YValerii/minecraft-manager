import { TestBed } from '@angular/core/testing';

import {
  DIFFICULTY_LABELS,
  DIMENSION_LABELS,
  DateTimePipe,
  FileSizePipe,
  GAME_MODE_LABELS,
  TimeAgoPipe,
  dateTime,
  fileSize,
  label,
  timeAgo,
  yesNo,
} from './format';
import { I18nService } from './i18n/i18n.service';

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

describe('format', () => {
  let i18n: I18nService;

  beforeEach(() => {
    i18n = TestBed.inject(I18nService);
    i18n.lang.set('en');
  });

  describe('fileSize', () => {
    const unit = (u: string) => i18n.t(`format.units.${u}`);

    it('shows a dash when there is no value', () => {
      expect(fileSize(i18n, null)).toBe('—');
      expect(fileSize(i18n, undefined)).toBe('—');
    });

    it('shows bytes below one kilobyte', () => {
      expect(fileSize(i18n, 0)).toBe(`0 ${i18n.t('format.bytesUnit')}`);
      expect(fileSize(i18n, 1023)).toBe(`1023 ${i18n.t('format.bytesUnit')}`);
    });

    it('picks the unit and keeps one decimal below 100', () => {
      expect(fileSize(i18n, 1024)).toBe(`1.0 ${unit('kb')}`);
      expect(fileSize(i18n, 1536 * 1024)).toBe(`1.5 ${unit('mb')}`);
      expect(fileSize(i18n, 2 * 1024 ** 3)).toBe(`2.0 ${unit('gb')}`);
    });

    it('drops the decimal from 100 up', () => {
      expect(fileSize(i18n, 150 * 1024)).toBe(`150 ${unit('kb')}`);
    });

    it('stops at the largest unit', () => {
      expect(fileSize(i18n, 5000 * 1024 ** 4)).toBe(`5000 ${unit('tb')}`);
    });
  });

  describe('dateTime', () => {
    it('shows a dash when there is no value', () => {
      expect(dateTime(i18n, null)).toBe('—');
      expect(dateTime(i18n, undefined)).toBe('—');
      expect(dateTime(i18n, 0)).toBe('—');
    });

    it('formats in the language being used', () => {
      const ms = Date.UTC(2024, 0, 15, 12, 0);
      expect(dateTime(i18n, ms)).toBe(
        new Date(ms).toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'short' }),
      );
      i18n.lang.set('de');
      expect(dateTime(i18n, ms)).toBe(
        new Date(ms).toLocaleString('de-DE', { dateStyle: 'medium', timeStyle: 'short' }),
      );
    });
  });

  describe('timeAgo', () => {
    const NOW = Date.UTC(2024, 5, 15, 12, 0);

    beforeEach(() => {
      vi.useFakeTimers();
      vi.setSystemTime(NOW);
    });

    afterEach(() => vi.useRealTimers());

    it('says never without a value', () => {
      expect(timeAgo(i18n, null)).toBe(i18n.t('format.never'));
      expect(timeAgo(i18n, undefined)).toBe(i18n.t('format.never'));
      expect(timeAgo(i18n, 0)).toBe(i18n.t('format.never'));
    });

    it('says just now below a minute', () => {
      expect(timeAgo(i18n, NOW - MINUTE + 1)).toBe(i18n.t('format.justNow'));
    });

    it('counts minutes, hours and days', () => {
      expect(timeAgo(i18n, NOW - MINUTE)).toBe(i18n.t('format.minutesAgo', { n: 1 }));
      expect(timeAgo(i18n, NOW - 59 * MINUTE)).toBe(i18n.t('format.minutesAgo', { n: 59 }));
      expect(timeAgo(i18n, NOW - HOUR)).toBe(i18n.t('format.hoursAgo', { n: 1 }));
      expect(timeAgo(i18n, NOW - 23 * HOUR)).toBe(i18n.t('format.hoursAgo', { n: 23 }));
      expect(timeAgo(i18n, NOW - DAY)).toBe(i18n.t('format.daysAgo', { n: 1 }));
      expect(timeAgo(i18n, NOW - 29 * DAY)).toBe(i18n.t('format.daysAgo', { n: 29 }));
    });

    it('shows the date from 30 days on', () => {
      const ms = NOW - 30 * DAY;
      expect(timeAgo(i18n, ms)).toBe(new Date(ms).toLocaleDateString('en-US', { dateStyle: 'medium' }));
    });
  });

  describe('yesNo', () => {
    it('translates booleans and keeps missing values empty', () => {
      expect(yesNo(i18n, true)).toBe(i18n.t('format.yes'));
      expect(yesNo(i18n, false)).toBe(i18n.t('format.no'));
      expect(yesNo(i18n, null)).toBeNull();
      expect(yesNo(i18n, undefined)).toBeNull();
    });
  });

  describe('label', () => {
    it('translates known values', () => {
      expect(label(i18n, GAME_MODE_LABELS, 'creative')).toBe(i18n.t('format.gameMode.creative'));
      expect(label(i18n, DIFFICULTY_LABELS, 'hard')).toBe(i18n.t('format.difficulty.hard'));
      expect(label(i18n, DIMENSION_LABELS, 'minecraft:the_nether')).toBe(i18n.t('format.dimension.nether'));
    });

    it('keeps unknown values, such as custom dimensions', () => {
      expect(label(i18n, DIMENSION_LABELS, 'mymod:skylands')).toBe('mymod:skylands');
    });

    it('is null without a value', () => {
      expect(label(i18n, GAME_MODE_LABELS, null)).toBeNull();
      expect(label(i18n, GAME_MODE_LABELS, undefined)).toBeNull();
      expect(label(i18n, GAME_MODE_LABELS, '')).toBeNull();
    });

    it('only points at translations that exist', () => {
      const all = { ...GAME_MODE_LABELS, ...DIFFICULTY_LABELS, ...DIMENSION_LABELS };
      for (const path of Object.values(all)) {
        expect(i18n.t(path)).not.toBe(path);
      }
    });
  });

  describe('pipes', () => {
    it('format through the same functions', () => {
      TestBed.runInInjectionContext(() => {
        expect(new FileSizePipe().transform(1024)).toBe(fileSize(i18n, 1024));
        expect(new DateTimePipe().transform(1000)).toBe(dateTime(i18n, 1000));
        expect(new TimeAgoPipe().transform(null)).toBe(i18n.t('format.never'));
      });
    });
  });
});
