import { Component, HostListener, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ApiService, errorMessage } from '../api.service';
import {
  DIFFICULTY_LABELS,
  DIMENSION_LABELS,
  GAME_MODE_LABELS,
  dateTime,
  fileSize,
  label,
  yesNo,
} from '../format';
import { I18nService } from '../i18n/i18n.service';
import { WorldDetails } from '../models';

interface Row {
  label: string;
  value: string;
}

interface Section {
  title: string;
  rows: Row[];
}

@Component({
  selector: 'app-world-modal',
  templateUrl: './world-modal.html',
  styleUrl: './world-modal.scss',
})
export class WorldModal {
  private readonly api = inject(ApiService);
  readonly i18n = inject(I18nService);

  readonly folder = input.required<string>();
  readonly closed = output<void>();

  readonly details = signal<WorldDetails | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly iconBroken = signal(false);

  readonly raw = signal<string | null>(null);
  readonly rawLoading = signal(false);

  readonly iconUrl = computed(() => this.api.worldIconUrl(this.folder()));

  readonly gameRules = computed(() => {
    const rules = this.details()?.gameRules;
    if (!rules || typeof rules !== 'object') return [];
    return Object.entries(rules)
      .map(([name, value]) => ({ name, value: String(value) }))
      .sort((a, b) => a.name.localeCompare(b.name));
  });

  readonly sections = computed<Section[]>(() => {
    const d = this.details();
    if (!d) return [];
    const i18n = this.i18n;
    const spawn = d.spawn;
    const p = d.player;
    const time =
      d.daysPlayed !== null
        ? i18n.t('worldModal.playTimeFormat', {
            days: d.daysPlayed.toLocaleString(i18n.locale()),
            ticks: (d.gameTimeTicks ?? 0).toLocaleString(i18n.locale()),
          })
        : null;
    const weather =
      d.thundering === null && d.raining === null
        ? null
        : d.thundering
          ? i18n.t('worldModal.weather.thunder')
          : d.raining
            ? i18n.t('worldModal.weather.rain')
            : i18n.t('worldModal.weather.clear');

    return build([
      {
        title: i18n.t('worldModal.sections.general'),
        rows: [
          [i18n.t('worldModal.rows.name'), d.summary.name],
          [i18n.t('worldModal.rows.folder'), d.summary.folder],
          [i18n.t('worldModal.rows.path'), d.path],
          [i18n.t('worldModal.rows.sizeOnDisk'), fileSize(i18n, d.sizeBytes)],
          [i18n.t('worldModal.rows.fileCount'), d.fileCount.toLocaleString(i18n.locale())],
          [i18n.t('worldModal.rows.lastPlayed'), dateTime(i18n, d.summary.lastPlayed)],
          [i18n.t('worldModal.rows.levelDatModified'), dateTime(i18n, d.levelDatModified)],
        ],
      },
      {
        title: i18n.t('worldModal.sections.version'),
        rows: [
          [i18n.t('worldModal.rows.gameVersion'), d.summary.versionName],
          [i18n.t('worldModal.rows.versionId'), d.versionId],
          [i18n.t('worldModal.rows.dataVersion'), d.dataVersion],
          [i18n.t('worldModal.rows.snapshot'), yesNo(i18n, d.snapshot)],
          [i18n.t('worldModal.rows.createdWith'), d.serverBrands.length ? d.serverBrands.join(', ') : null],
          [i18n.t('worldModal.rows.wasModded'), yesNo(i18n, d.wasModded)],
        ],
      },
      {
        title: i18n.t('worldModal.sections.game'),
        rows: [
          [i18n.t('worldModal.rows.mode'), label(i18n, GAME_MODE_LABELS, d.summary.gameMode)],
          [i18n.t('worldModal.rows.difficulty'), label(i18n, DIFFICULTY_LABELS, d.difficulty)],
          [i18n.t('worldModal.rows.hardcore'), yesNo(i18n, d.summary.hardcore)],
          [i18n.t('worldModal.rows.commandsAllowed'), yesNo(i18n, d.allowCommands)],
          [i18n.t('worldModal.rows.seed'), d.seed],
          [i18n.t('worldModal.rows.spawnPoint'), spawn ? `${spawn.x}, ${spawn.y}, ${spawn.z}` : null],
          [i18n.t('worldModal.rows.playTime'), time],
          [i18n.t('worldModal.rows.weather'), weather],
          [
            i18n.t('worldModal.rows.worldBorderSize'),
            d.borderSize !== null
              ? i18n.t('worldModal.blocksFormat', { size: d.borderSize.toLocaleString(i18n.locale()) })
              : null,
          ],
          [
            i18n.t('worldModal.rows.dimensions'),
            d.dimensions.length ? d.dimensions.map((x) => label(i18n, DIMENSION_LABELS, x)).join(', ') : null,
          ],
          [i18n.t('worldModal.rows.playersInWorld'), d.playerCount > 0 ? d.playerCount : null],
        ],
      },
      {
        title: i18n.t('worldModal.sections.player'),
        rows: p
          ? [
              [i18n.t('worldModal.rows.dimension'), label(i18n, DIMENSION_LABELS, p.dimension)],
              [
                i18n.t('worldModal.rows.position'),
                p.x !== null && p.y !== null && p.z !== null
                  ? `${Math.round(p.x)}, ${Math.round(p.y)}, ${Math.round(p.z)}`
                  : null,
              ],
              [i18n.t('worldModal.rows.health'), p.health],
              [i18n.t('worldModal.rows.food'), p.food],
              [i18n.t('worldModal.rows.xpLevel'), p.xpLevel],
              [i18n.t('worldModal.rows.playerMode'), label(i18n, GAME_MODE_LABELS, p.gameMode)],
            ]
          : [],
      },
      {
        title: i18n.t('worldModal.sections.datapacksFeatures'),
        rows: [
          [i18n.t('worldModal.rows.enabledDatapacks'), d.enabledDatapacks.length ? d.enabledDatapacks.join(', ') : null],
          [i18n.t('worldModal.rows.disabledDatapacks'), d.disabledDatapacks.length ? d.disabledDatapacks.join(', ') : null],
          [i18n.t('worldModal.rows.enabledFeatures'), d.enabledFeatures.length ? d.enabledFeatures.join(', ') : null],
        ],
      },
    ]);
  });

  constructor() {
    effect(() => {
      const folder = this.folder();
      untracked(() => void this.load(folder));
    });
  }

  private async load(folder: string): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    this.details.set(null);
    this.raw.set(null);
    this.iconBroken.set(false);
    try {
      const d = await firstValueFrom(this.api.world(folder));
      if (this.folder() === folder) this.details.set(d);
    } catch (e) {
      if (this.folder() === folder) this.error.set(errorMessage(e, this.i18n));
    } finally {
      if (this.folder() === folder) this.loading.set(false);
    }
  }

  /** The full level.dat tree is only loaded once the section is expanded. */
  async onRawToggle(event: Event): Promise<void> {
    const open = (event.target as HTMLDetailsElement).open;
    if (!open || this.raw() !== null || this.rawLoading()) return;
    const folder = this.folder();
    this.rawLoading.set(true);
    try {
      const tree = await firstValueFrom(this.api.worldRaw(folder));
      this.raw.set(JSON.stringify(tree, null, 2));
    } catch (e) {
      this.raw.set(this.i18n.t('worldModal.rawError', { message: errorMessage(e, this.i18n) }));
    } finally {
      this.rawLoading.set(false);
    }
  }

  @HostListener('document:keydown.escape')
  close(): void {
    this.closed.emit();
  }

  onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) this.close();
  }
}

/** Drops empty values and sections with no data. */
function build(input: { title: string; rows: [string, string | number | null | undefined][] }[]): Section[] {
  return input
    .map((s) => ({
      title: s.title,
      rows: s.rows
        .filter(([, v]) => v !== null && v !== undefined && v !== '')
        .map(([l, v]) => ({ label: l, value: String(v) })),
    }))
    .filter((s) => s.rows.length > 0);
}
