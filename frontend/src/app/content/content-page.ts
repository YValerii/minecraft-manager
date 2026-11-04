import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { SlicePipe } from '@angular/common';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatButtonToggle, MatButtonToggleGroup } from '@angular/material/button-toggle';
import { MatCard } from '@angular/material/card';
import { MatFormField, MatPrefix, MatSuffix } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatTooltip } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { ApiService, errorMessage } from '../api.service';
import { DateTimePipe, FileSizePipe } from '../format';
import { I18nService } from '../i18n/i18n.service';
import { AppIcon } from '../icons';
import { ContentItem, ContentList, ContentType, Settings, UpdateItem } from '../models';
import { Banner } from '../shared/banner';
import { ConfirmService } from '../shared/confirm.service';

interface CategoryMeta {
  titleKey: string;
  icon: AppIcon;
  /** Whether compatibility depends on the mod loader (mods only). */
  usesLoader: boolean;
  emptyKey: string;
}

const CATEGORIES: Record<ContentType, CategoryMeta> = {
  mods: { titleKey: 'content.titles.mods', icon: 'extension', usesLoader: true, emptyKey: 'content.empty.mods' },
  resourcepacks: {
    titleKey: 'content.titles.resourcepacks',
    icon: 'palette',
    usesLoader: false,
    emptyKey: 'content.empty.resourcepacks',
  },
  datapacks: {
    titleKey: 'content.titles.datapacks',
    icon: 'inventory_2',
    usesLoader: false,
    emptyKey: 'content.empty.datapacks',
  },
  shaderpacks: {
    titleKey: 'content.titles.shaderpacks',
    icon: 'flare',
    usesLoader: false,
    emptyKey: 'content.empty.shaderpacks',
  },
};

type Filter = 'all' | 'updates' | 'unknown';

@Component({
  selector: 'app-content-page',
  imports: [
    RouterLink,
    FileSizePipe,
    DateTimePipe,
    SlicePipe,
    Banner,
    MatButton,
    MatButtonToggle,
    MatButtonToggleGroup,
    MatCard,
    MatFormField,
    MatIcon,
    MatIconButton,
    MatInput,
    MatPrefix,
    MatProgressBar,
    MatProgressSpinner,
    MatSuffix,
    MatTooltip,
  ],
  templateUrl: './content-page.html',
  styleUrl: './content-page.scss',
})
export class ContentPage {
  private readonly api = inject(ApiService);
  private readonly confirm = inject(ConfirmService);
  readonly i18n = inject(I18nService);

  /** Taken from the /c/:type route (withComponentInputBinding). */
  readonly type = input.required<ContentType>();

  readonly meta = computed<CategoryMeta>(() => CATEGORIES[this.type()] ?? CATEGORIES.mods);

  readonly list = signal<ContentList | null>(null);
  readonly settings = signal<Settings | null>(null);
  readonly updates = signal<Record<string, UpdateItem>>({});
  readonly updatesWarning = signal<string | null>(null);

  readonly loading = signal(false);
  readonly checking = signal(false);
  readonly installingAll = signal(false);
  readonly busy = signal<ReadonlySet<string>>(new Set());
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);

  readonly search = signal('');
  readonly filter = signal<Filter>('all');
  private readonly failedIcons = signal<ReadonlySet<string>>(new Set());

  readonly updateCount = computed(
    () => Object.values(this.updates()).filter((u) => u.status === 'UPDATE_AVAILABLE').length,
  );

  readonly unknownCount = computed(() => (this.list()?.items ?? []).filter((i) => !i.identified).length);

  readonly visible = computed(() => {
    const items = this.list()?.items ?? [];
    const q = this.search().trim().toLowerCase();
    const filter = this.filter();
    const updates = this.updates();
    return items.filter((item) => {
      if (filter === 'updates' && updates[item.id]?.status !== 'UPDATE_AVAILABLE') return false;
      if (filter === 'unknown' && item.identified) return false;
      if (!q) return true;
      return (
        item.name.toLowerCase().includes(q) ||
        item.fileName.toLowerCase().includes(q) ||
        (item.description ?? '').toLowerCase().includes(q)
      );
    });
  });

  constructor() {
    // Reload data whenever the tab changes (/c/mods -> /c/shaderpacks ...)
    effect(() => {
      const type = this.type();
      untracked(() => {
        this.search.set('');
        this.filter.set('all');
        this.failedIcons.set(new Set());
        void this.load(type, false);
      });
    });
  }

  async load(type: ContentType, refresh: boolean): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    if (!refresh) {
      this.list.set(null);
      this.updates.set({});
      this.updatesWarning.set(null);
      this.notice.set(null);
    }
    try {
      const [list, settings] = await Promise.all([
        firstValueFrom(this.api.list(type, refresh)),
        firstValueFrom(this.api.settings()),
      ]);
      if (this.type() !== type) return; // user already switched to another tab
      this.list.set(list);
      this.settings.set(settings);
    } catch (e) {
      if (this.type() === type) this.error.set(errorMessage(e, this.i18n));
      return;
    } finally {
      if (this.type() === type) this.loading.set(false);
    }
    await this.checkUpdates(type, refresh);
  }

  private async checkUpdates(type: ContentType, refresh: boolean): Promise<void> {
    this.checking.set(true);
    try {
      const res = await firstValueFrom(this.api.updates(type, refresh));
      if (this.type() !== type) return;
      const map: Record<string, UpdateItem> = {};
      for (const u of res.items) map[u.id] = u;
      this.updates.set(map);
      this.updatesWarning.set(res.warning);
    } catch (e) {
      if (this.type() === type) this.updatesWarning.set(errorMessage(e, this.i18n));
    } finally {
      if (this.type() === type) this.checking.set(false);
    }
  }

  refresh(): void {
    void this.load(this.type(), true);
  }

  async install(item: ContentItem): Promise<void> {
    const type = this.type();
    this.setBusy(item.id, true);
    this.error.set(null);
    this.notice.set(null);
    try {
      const res = await firstValueFrom(this.api.install(type, item.id));
      this.notice.set(this.i18n.t('content.installNotice', { name: item.name, message: res.message }));
    } catch (e) {
      this.error.set(this.i18n.t('content.installError', { name: item.name, message: errorMessage(e, this.i18n) }));
    } finally {
      this.setBusy(item.id, false);
    }
    await this.load(type, false);
  }

  async installAll(): Promise<void> {
    const count = this.updateCount();
    if (count === 0 || !(await this.confirm.ask(this.i18n.t('content.confirmUpdateAll', { count })))) {
      return;
    }
    const type = this.type();
    this.installingAll.set(true);
    this.error.set(null);
    this.notice.set(null);
    try {
      const results = await firstValueFrom(this.api.installAll(type));
      const ok = results.filter((r) => r.ok).length;
      const failed = results.filter((r) => !r.ok);
      this.notice.set(this.i18n.t('content.updatedCount', { ok, total: results.length }));
      if (failed.length > 0) {
        this.error.set(failed.map((f) => `${f.id}: ${f.message}`).join('\n'));
      }
    } catch (e) {
      this.error.set(errorMessage(e, this.i18n));
    } finally {
      this.installingAll.set(false);
    }
    await this.load(type, false);
  }

  private setBusy(id: string, on: boolean): void {
    const next = new Set(this.busy());
    if (on) next.add(id);
    else next.delete(id);
    this.busy.set(next);
  }

  statusOf(id: string): UpdateItem | undefined {
    return this.updates()[id];
  }

  // ---- icons: first the project icon from Modrinth/CurseForge, then the icon from the file itself, then a placeholder
  iconSrc(item: ContentItem): string | null {
    const failed = this.failedIcons();
    if (item.iconUrl && !failed.has('r:' + item.id)) return item.iconUrl;
    if (item.localIcon && !failed.has('l:' + item.id)) return this.api.localIconUrl(this.type(), item.id);
    return null;
  }

  iconFailed(item: ContentItem): void {
    const key = item.iconUrl && !this.failedIcons().has('r:' + item.id) ? 'r:' : 'l:';
    const next = new Set(this.failedIcons());
    next.add(key + item.id);
    this.failedIcons.set(next);
  }

  initial(item: ContentItem): string {
    return (item.name.trim()[0] ?? '?').toUpperCase();
  }

  sourceLabel(item: ContentItem): string {
    switch (item.source) {
      case 'MODRINTH':
        return this.i18n.t('content.sourceModrinth');
      case 'CURSEFORGE':
        return this.i18n.t('content.sourceCurseforge');
      default:
        return this.i18n.t('content.sourceLocal');
    }
  }

  onSearch(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
  }
}
