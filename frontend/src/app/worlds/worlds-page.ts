import { Component, inject, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatRipple } from '@angular/material/core';
import { MatDialog } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { ApiService, errorMessage } from '../api.service';
import { GAME_MODE_LABELS, TimeAgoPipe, label } from '../format';
import { I18nService } from '../i18n/i18n.service';
import { WorldSummary } from '../models';
import { Banner } from '../shared/banner';
import { WorldModal } from './world-modal';

@Component({
  selector: 'app-worlds-page',
  imports: [RouterLink, TimeAgoPipe, Banner, MatButton, MatIcon, MatProgressSpinner, MatRipple],
  templateUrl: './worlds-page.html',
  styleUrl: './worlds-page.scss',
})
export class WorldsPage {
  readonly api = inject(ApiService);
  readonly i18n = inject(I18nService);
  private readonly dialog = inject(MatDialog);

  readonly worlds = signal<WorldSummary[] | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
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

  /** The dialog decides for itself when it may close, since it can hold unsaved edits. */
  open(world: WorldSummary): void {
    const ref = this.dialog.open(WorldModal, {
      panelClass: 'world-dialog',
      width: 'min(960px, calc(100vw - 32px))',
      height: 'min(860px, calc(100dvh - 32px))',
      maxWidth: '100vw',
      disableClose: true,
      ariaLabelledBy: 'world-title',
    });
    ref.componentRef?.setInput('folder', world.folder);
    ref.componentInstance.changed.subscribe(() => void this.load());
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
