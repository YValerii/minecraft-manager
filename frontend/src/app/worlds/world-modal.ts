import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatOption } from '@angular/material/core';
import { MatDialogRef } from '@angular/material/dialog';
import { MatFormField, MatLabel, MatPrefix } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatSelect } from '@angular/material/select';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { MatTab, MatTabGroup } from '@angular/material/tabs';
import { MatTooltip } from '@angular/material/tooltip';
import { Observable, firstValueFrom, merge } from 'rxjs';
import { filter } from 'rxjs/operators';

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
import { WorldDetails, WorldEdit } from '../models';
import { Banner } from '../shared/banner';
import { ConfirmService } from '../shared/confirm.service';

interface Row {
  label: string;
  value: string;
}

interface Section {
  title: string;
  rows: Row[];
}

/** The values being edited, as the form shows them. */
interface Draft {
  name: string;
  gameMode: string;
  difficulty: string;
  hardcore: boolean;
  allowCommands: boolean;
  gameRules: Record<string, string>;
}

@Component({
  selector: 'app-world-modal',
  imports: [
    Banner,
    MatButton,
    MatCheckbox,
    MatFormField,
    MatIcon,
    MatIconButton,
    MatInput,
    MatLabel,
    MatOption,
    MatPrefix,
    MatProgressSpinner,
    MatSelect,
    MatSlideToggle,
    MatTab,
    MatTabGroup,
    MatTooltip,
  ],
  templateUrl: './world-modal.html',
  styleUrl: './world-modal.scss',
})
export class WorldModal {
  private readonly api = inject(ApiService);
  private readonly dialogRef = inject(MatDialogRef<WorldModal>);
  private readonly confirm = inject(ConfirmService);
  readonly i18n = inject(I18nService);

  readonly folder = input.required<string>();
  /** The world on disk changed, so anything showing it is out of date. */
  readonly changed = output<void>();

  readonly details = signal<WorldDetails | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly iconBroken = signal(false);

  readonly raw = signal<string | null>(null);
  readonly rawLoading = signal(false);
  private rawOpen = false;

  readonly tabIndex = signal(0);
  readonly ruleFilter = signal('');

  /** Editing is off until asked for, and goes back to off after saving: null means read-only. */
  readonly draft = signal<Draft | null>(null);
  readonly busy = signal(false);
  readonly editError = signal<string | null>(null);
  readonly saved = signal(false);

  private closing = false;

  readonly iconUrl = computed(() => this.api.worldIconUrl(this.folder()));
  // Also checks hasIcon, since the browser may have the icon cached for a while after it is reset.
  readonly showIcon = computed(() => !this.iconBroken() && (this.details()?.summary.hasIcon ?? true));

  readonly gameModeOptions = computed(() => this.options(GAME_MODE_LABELS, this.draft()?.gameMode));
  readonly difficultyOptions = computed(() => this.options(DIFFICULTY_LABELS, this.draft()?.difficulty));

  readonly gameRules = computed(() => {
    const rules = this.details()?.gameRules;
    if (!rules || typeof rules !== 'object') return [];
    return Object.entries(rules)
      .map(([name, value]) => ({ name, value: String(value) }))
      .sort((a, b) => a.name.localeCompare(b.name));
  });

  readonly visibleRules = computed(() => {
    const query = this.ruleFilter().trim().toLowerCase();
    return this.gameRules().filter((g) => g.name.toLowerCase().includes(query));
  });

  /** The raw data is the last tab, and the game rules tab only exists when there are rules. */
  readonly rawTabIndex = computed(() => (this.gameRules().length > 0 ? 2 : 1));

  /** Only what differs from the world as loaded. */
  readonly changes = computed<WorldEdit>(() => {
    const draft = this.draft();
    const d = this.details();
    if (!draft || !d) return {};
    const edit: WorldEdit = {};
    if (draft.name.trim() !== d.summary.name) edit.name = draft.name.trim();
    if (draft.gameMode !== (d.summary.gameMode ?? '')) edit.gameMode = draft.gameMode;
    if (draft.difficulty !== (d.difficulty ?? '')) edit.difficulty = draft.difficulty;
    if (draft.hardcore !== d.summary.hardcore) edit.hardcore = draft.hardcore;
    if (draft.allowCommands !== (d.allowCommands ?? false)) edit.allowCommands = draft.allowCommands;
    const rules = this.gameRules().filter((g) => draft.gameRules[g.name] !== g.value);
    if (rules.length > 0) {
      edit.gameRules = Object.fromEntries(rules.map((g) => [g.name, draft.gameRules[g.name]]));
    }
    return edit;
  });

  readonly dirty = computed(() => Object.keys(this.changes()).length > 0);

  readonly canSave = computed(() => {
    const draft = this.draft();
    if (!draft || !this.dirty() || this.busy() || !draft.name.trim()) return false;
    return this.gameRules().every((g) => this.ruleKind(g.value) !== 'number' || INTEGER.test(draft.gameRules[g.name]));
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

    // The dialog stays open on Escape and on clicks outside it until unsaved edits have been dealt with.
    merge(this.dialogRef.backdropClick(), this.dialogRef.keydownEvents().pipe(filter((e) => e.key === 'Escape')))
      .pipe(takeUntilDestroyed())
      .subscribe(() => void this.close());
  }

  private async load(folder: string): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    this.details.set(null);
    this.raw.set(null);
    this.rawOpen = false;
    this.tabIndex.set(0);
    this.ruleFilter.set('');
    this.iconBroken.set(false);
    this.draft.set(null);
    this.editError.set(null);
    this.saved.set(false);
    try {
      const d = await firstValueFrom(this.api.world(folder));
      if (this.folder() === folder) this.details.set(d);
    } catch (e) {
      if (this.folder() === folder) this.error.set(errorMessage(e, this.i18n));
    } finally {
      if (this.folder() === folder) this.loading.set(false);
    }
  }

  /** The full level.dat tree is only loaded once its tab is opened. */
  async onTab(index: number): Promise<void> {
    this.tabIndex.set(index);
    this.rawOpen = index === this.rawTabIndex();
    if (!this.rawOpen || this.raw() !== null || this.rawLoading()) return;
    await this.loadRaw();
  }

  private async loadRaw(): Promise<void> {
    this.rawLoading.set(true);
    try {
      const tree = await firstValueFrom(this.api.worldRaw(this.folder()));
      this.raw.set(JSON.stringify(tree, null, 2));
    } catch (e) {
      this.raw.set(this.i18n.t('worldModal.rawError', { message: errorMessage(e, this.i18n) }));
    } finally {
      this.rawLoading.set(false);
    }
  }

  // ---- editing

  /** `toggle` is the switch that was flipped, which has to flip back if the edits end up being kept. */
  async toggleEditing(toggle?: MatSlideToggle): Promise<void> {
    const d = this.details();
    if (this.draft()) {
      if (!(await this.confirmDiscard())) {
        if (toggle) toggle.checked = true;
        return;
      }
      this.draft.set(null);
    } else if (d) {
      this.draft.set({
        name: d.summary.name,
        gameMode: d.summary.gameMode ?? '',
        difficulty: d.difficulty ?? '',
        hardcore: d.summary.hardcore,
        allowCommands: d.allowCommands ?? false,
        gameRules: Object.fromEntries(this.gameRules().map((g) => [g.name, g.value])),
      });
    }
    this.editError.set(null);
    this.saved.set(false);
  }

  patchDraft(patch: Partial<Draft>): void {
    this.draft.update((draft) => draft && { ...draft, ...patch });
  }

  setRule(name: string, value: string): void {
    this.draft.update((draft) => draft && { ...draft, gameRules: { ...draft.gameRules, [name]: value } });
  }

  ruleKind(value: string): 'boolean' | 'number' | 'text' {
    if (value === 'true' || value === 'false') return 'boolean';
    return INTEGER.test(value) ? 'number' : 'text';
  }

  value(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  async save(): Promise<void> {
    if (!this.canSave()) return;
    if (await this.change(this.api.updateWorld(this.folder(), this.changes()))) {
      this.draft.set(null);
      this.saved.set(true);
    }
  }

  async resetIcon(): Promise<void> {
    if (this.busy() || !(await this.confirm.ask(this.i18n.t('worldModal.edit.resetIconConfirm')))) return;
    await this.change(this.api.resetWorldIcon(this.folder()));
  }

  /** Sends a change to the backend and shows the world as it now is. Unsaved edits stay in the form. */
  private async change(request: Observable<WorldDetails>): Promise<boolean> {
    this.busy.set(true);
    this.editError.set(null);
    try {
      this.details.set(await firstValueFrom(request));
      this.raw.set(null);
      if (this.rawOpen) void this.loadRaw();
      this.changed.emit();
      return true;
    } catch (e) {
      this.editError.set(errorMessage(e, this.i18n));
      return false;
    } finally {
      this.busy.set(false);
    }
  }

  private async confirmDiscard(): Promise<boolean> {
    return !this.dirty() || this.confirm.ask(this.i18n.t('worldModal.edit.discardConfirm'));
  }

  /** The known values, plus the current one if it is something else, so showing the form doesn't change it. */
  private options(labels: Record<string, string>, current: string | undefined): { value: string; label: string }[] {
    const known = Object.keys(labels).map((value) => ({ value, label: label(this.i18n, labels, value) ?? value }));
    return current === undefined || current in labels ? known : [{ value: current, label: current || '—' }, ...known];
  }

  async close(): Promise<void> {
    if (this.closing) return;
    this.closing = true;
    if (await this.confirmDiscard()) this.dialogRef.close();
    this.closing = false;
  }
}

const INTEGER = /^-?\d+$/;

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
