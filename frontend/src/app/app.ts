import { BreakpointObserver, MediaMatcher } from '@angular/cdk/layout';
import { Component, OnInit, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatToolbar } from '@angular/material/toolbar';
import { MatTooltip } from '@angular/material/tooltip';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';

import { ApiService, errorMessage } from './api.service';
import { I18nService, Lang } from './i18n/i18n.service';
import { AppIcon } from './icons';
import { ThemeService } from './theme.service';

interface Tab {
  link: string;
  labelKey: string;
  icon: AppIcon;
}

const MESSAGE_MS = 5000;
const COMPACT_QUERY = '(max-width: 900px)';

@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatButton,
    MatIcon,
    MatIconButton,
    MatListModule,
    MatMenuModule,
    MatProgressSpinner,
    MatSidenavModule,
    MatToolbar,
    MatTooltip,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App implements OnInit {
  private readonly api = inject(ApiService);
  private readonly snackBar = inject(MatSnackBar);
  readonly i18n = inject(I18nService);
  readonly theme = inject(ThemeService);

  readonly tabs: Tab[] = [
    { link: '/c/mods', labelKey: 'app.tabs.mods', icon: 'extension' },
    { link: '/c/resourcepacks', labelKey: 'app.tabs.resourcepacks', icon: 'palette' },
    { link: '/c/datapacks', labelKey: 'app.tabs.datapacks', icon: 'inventory_2' },
    { link: '/c/shaderpacks', labelKey: 'app.tabs.shaderpacks', icon: 'flare' },
    { link: '/worlds', labelKey: 'app.tabs.worlds', icon: 'public' },
  ];

  /** On narrow windows the navigation becomes a drawer that opens over the page. */
  readonly compact = toSignal(
    inject(BreakpointObserver)
      .observe(COMPACT_QUERY)
      .pipe(map((state) => state.matches)),
    // The observer answers asynchronously, and the drawer must not flash open on a narrow window.
    { initialValue: inject(MediaMatcher).matchMedia(COMPACT_QUERY).matches },
  );
  readonly navOpen = signal(false);

  readonly launcherAvailable = signal(false);
  readonly launching = signal(false);

  ngOnInit(): void {
    this.api.launcherStatus().subscribe({
      next: (status) => this.launcherAvailable.set(status.available),
      error: () => this.launcherAvailable.set(false),
    });
  }

  closeNav(): void {
    this.navOpen.set(false);
  }

  /** Keeps track of the drawer being closed by its backdrop or Escape; the wide layout is always open. */
  onNavChange(opened: boolean): void {
    if (this.compact()) this.navOpen.set(opened);
  }

  setLang(lang: Lang): void {
    this.i18n.setLang(lang);
  }

  launchMinecraft(): void {
    this.launching.set(true);
    this.api.launchMinecraft().subscribe({
      next: () => {
        this.launching.set(false);
        this.snackBar.open(this.i18n.t('app.launcher.started'), undefined, { duration: MESSAGE_MS });
      },
      error: (e) => {
        this.launching.set(false);
        this.snackBar.open(errorMessage(e, this.i18n), undefined, { duration: MESSAGE_MS, panelClass: 'snack-error' });
      },
    });
  }
}
