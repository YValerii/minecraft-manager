import { Pipe, PipeTransform, inject } from '@angular/core';

import { I18nService } from './i18n/i18n.service';

export function fileSize(i18n: I18nService, bytes: number | null | undefined): string {
  if (bytes === null || bytes === undefined) {
    return '—';
  }
  if (bytes < 1024) {
    return `${bytes} ${i18n.t('format.bytesUnit')}`;
  }
  const unitKeys = ['kb', 'mb', 'gb', 'tb'];
  let v = bytes / 1024;
  let i = 0;
  while (v >= 1024 && i < unitKeys.length - 1) {
    v /= 1024;
    i++;
  }
  return `${v.toFixed(v >= 100 ? 0 : 1)} ${i18n.t(`format.units.${unitKeys[i]}`)}`;
}

export function dateTime(i18n: I18nService, ms: number | null | undefined): string {
  if (!ms) {
    return '—';
  }
  return new Date(ms).toLocaleString(i18n.locale(), { dateStyle: 'medium', timeStyle: 'short' });
}

export function timeAgo(i18n: I18nService, ms: number | null | undefined): string {
  if (!ms) {
    return i18n.t('format.never');
  }
  const diff = Date.now() - ms;
  const min = Math.floor(diff / 60000);
  if (min < 1) return i18n.t('format.justNow');
  if (min < 60) return i18n.t('format.minutesAgo', { n: min });
  const h = Math.floor(min / 60);
  if (h < 24) return i18n.t('format.hoursAgo', { n: h });
  const d = Math.floor(h / 24);
  if (d < 30) return i18n.t('format.daysAgo', { n: d });
  return new Date(ms).toLocaleDateString(i18n.locale(), { dateStyle: 'medium' });
}

export function yesNo(i18n: I18nService, v: boolean | null | undefined): string | null {
  return v === null || v === undefined ? null : v ? i18n.t('format.yes') : i18n.t('format.no');
}

export const GAME_MODE_LABELS: Record<string, string> = {
  survival: 'format.gameMode.survival',
  creative: 'format.gameMode.creative',
  adventure: 'format.gameMode.adventure',
  spectator: 'format.gameMode.spectator',
};

export const DIFFICULTY_LABELS: Record<string, string> = {
  peaceful: 'format.difficulty.peaceful',
  easy: 'format.difficulty.easy',
  normal: 'format.difficulty.normal',
  hard: 'format.difficulty.hard',
};

export const DIMENSION_LABELS: Record<string, string> = {
  overworld: 'format.dimension.overworld',
  nether: 'format.dimension.nether',
  the_end: 'format.dimension.the_end',
  'minecraft:overworld': 'format.dimension.overworld',
  'minecraft:the_nether': 'format.dimension.nether',
  'minecraft:the_end': 'format.dimension.the_end',
};

/** Resolves a raw value (e.g. "survival") through a key->i18n-path map; falls back to the raw value for unknown keys (custom dimensions etc). */
export function label(i18n: I18nService, map: Record<string, string>, key: string | null | undefined): string | null {
  if (!key) return null;
  const path = map[key];
  return path ? i18n.t(path) : key;
}

@Pipe({ name: 'fileSize', pure: false })
export class FileSizePipe implements PipeTransform {
  private readonly i18n = inject(I18nService);

  transform(value: number | null | undefined): string {
    return fileSize(this.i18n, value);
  }
}

@Pipe({ name: 'dateTime', pure: false })
export class DateTimePipe implements PipeTransform {
  private readonly i18n = inject(I18nService);

  transform(value: number | null | undefined): string {
    return dateTime(this.i18n, value);
  }
}

@Pipe({ name: 'timeAgo', pure: false })
export class TimeAgoPipe implements PipeTransform {
  private readonly i18n = inject(I18nService);

  transform(value: number | null | undefined): string {
    return timeAgo(this.i18n, value);
  }
}
