import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { ApiService, errorMessage } from './api.service';
import { I18nService, Lang } from './i18n/i18n.service';
import { ThemeService } from './theme.service';

interface Tab {
  link: string;
  labelKey: string;
  emoji: string;
}

const TOAST_MS = 5000;

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App implements OnInit {
  private readonly api = inject(ApiService);
  readonly i18n = inject(I18nService);
  readonly theme = inject(ThemeService);

  readonly tabs: Tab[] = [
    { link: '/c/mods', labelKey: 'app.tabs.mods', emoji: '🧩' },
    { link: '/c/resourcepacks', labelKey: 'app.tabs.resourcepacks', emoji: '🎨' },
    { link: '/c/datapacks', labelKey: 'app.tabs.datapacks', emoji: '📦' },
    { link: '/c/shaderpacks', labelKey: 'app.tabs.shaderpacks', emoji: '✨' },
    { link: '/worlds', labelKey: 'app.tabs.worlds', emoji: '🌍' },
  ];

  readonly launcherAvailable = signal(false);
  readonly launching = signal(false);
  readonly toast = signal<{ kind: 'ok' | 'error'; text: string } | null>(null);
  private toastTimer?: ReturnType<typeof setTimeout>;

  ngOnInit(): void {
    this.api.launcherStatus().subscribe({
      next: (status) => this.launcherAvailable.set(status.available),
      error: () => this.launcherAvailable.set(false),
    });
  }

  setLang(lang: Lang): void {
    this.i18n.setLang(lang);
  }

  launchMinecraft(): void {
    this.launching.set(true);
    this.api.launchMinecraft().subscribe({
      next: () => {
        this.launching.set(false);
        this.showToast('ok', this.i18n.t('app.launcher.started'));
      },
      error: (e) => {
        this.launching.set(false);
        this.showToast('error', errorMessage(e, this.i18n));
      },
    });
  }

  private showToast(kind: 'ok' | 'error', text: string): void {
    clearTimeout(this.toastTimer);
    this.toast.set({ kind, text });
    this.toastTimer = setTimeout(() => this.toast.set(null), TOAST_MS);
  }
}
