import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { I18nService, Lang } from './i18n/i18n.service';

interface Tab {
  link: string;
  labelKey: string;
  emoji: string;
}

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  readonly i18n = inject(I18nService);

  readonly tabs: Tab[] = [
    { link: '/c/mods', labelKey: 'app.tabs.mods', emoji: '🧩' },
    { link: '/c/resourcepacks', labelKey: 'app.tabs.resourcepacks', emoji: '🎨' },
    { link: '/c/datapacks', labelKey: 'app.tabs.datapacks', emoji: '📦' },
    { link: '/c/shaderpacks', labelKey: 'app.tabs.shaderpacks', emoji: '✨' },
    { link: '/worlds', labelKey: 'app.tabs.worlds', emoji: '🌍' },
  ];

  setLang(lang: Lang): void {
    this.i18n.setLang(lang);
  }
}
