import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { ApiService, errorMessage } from '../api.service';
import { GAME_MODE_LABELS, TimeAgoPipe, label } from '../format';
import { I18nService } from '../i18n/i18n.service';
import { WorldSummary } from '../models';
import { WorldModal } from './world-modal';

@Component({
  selector: 'app-worlds-page',
  imports: [RouterLink, TimeAgoPipe, WorldModal],
  templateUrl: './worlds-page.html',
  styleUrl: './worlds-page.scss',
})
export class WorldsPage {
  readonly api = inject(ApiService);
  readonly i18n = inject(I18nService);

  readonly worlds = signal<WorldSummary[] | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly selected = signal<string | null>(null);
  private readonly brokenIcons = signal<ReadonlySet<string>>(new Set());

  constructor() {
    void this.load();
  }

  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      this.worlds.set(await firstValueFrom(this.api.worlds()));
    } catch (e) {
      this.error.set(errorMessage(e, this.i18n));
    } finally {
      this.loading.set(false);
    }
  }

  modeLabel(w: WorldSummary): string {
    return label(this.i18n, GAME_MODE_LABELS, w.gameMode) ?? '—';
  }

  showIcon(w: WorldSummary): boolean {
    return w.hasIcon && !this.brokenIcons().has(w.folder);
  }

  iconBroken(w: WorldSummary): void {
    this.brokenIcons.set(new Set(this.brokenIcons()).add(w.folder));
  }
}
