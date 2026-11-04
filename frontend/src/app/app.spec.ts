import { HttpErrorResponse } from '@angular/common/http';
import { BreakpointObserver, BreakpointState, MediaMatcher } from '@angular/cdk/layout';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltip } from '@angular/material/tooltip';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { BehaviorSubject, of, Subject, throwError } from 'rxjs';

import { ApiService } from './api.service';
import { App } from './app';
import { I18nService } from './i18n/i18n.service';
import { LauncherStatus } from './models';
import { ThemeService } from './theme.service';

describe('App', () => {
  let api: { launcherStatus: ReturnType<typeof vi.fn>; launchMinecraft: ReturnType<typeof vi.fn> };
  let i18n: I18nService;
  let snackBar: { open: ReturnType<typeof vi.fn> };
  /** Whether the window counts as narrow. */
  let narrow: BehaviorSubject<BreakpointState>;

  async function settle(fixture: ComponentFixture<App>): Promise<void> {
    for (let i = 0; i < 3; i++) {
      await new Promise((resolve) => setTimeout(resolve));
      await fixture.whenStable();
    }
  }

  async function create(): Promise<ComponentFixture<App>> {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    return fixture;
  }

  const launchButton = (f: ComponentFixture<App>): HTMLButtonElement =>
    f.nativeElement.querySelector('button.launch');
  const themeButton = (f: ComponentFixture<App>): HTMLButtonElement =>
    f.nativeElement.querySelector('button.theme-toggle');
  const tooltip = (f: ComponentFixture<App>, selector: string): string =>
    f.debugElement.query(By.css(selector)).injector.get(MatTooltip).message;
  const launchTooltip = (f: ComponentFixture<App>): string => tooltip(f, 'span:has(> button.launch)');
  const drawerOpen = (f: ComponentFixture<App>): boolean =>
    f.nativeElement.querySelector('mat-sidenav').classList.contains('mat-drawer-opened');
  const themeIcon = (f: ComponentFixture<App>): string | null | undefined =>
    themeButton(f).querySelector('mat-icon')?.getAttribute('data-mat-icon-name');

  beforeEach(() => {
    api = {
      launcherStatus: vi.fn(() => of({ available: true })),
      launchMinecraft: vi.fn(() => of(undefined)),
    };
    snackBar = { open: vi.fn() };
    narrow = new BehaviorSubject<BreakpointState>({ matches: false, breakpoints: {} });
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: '**', children: [] }]),
        { provide: ApiService, useValue: api },
        { provide: MatSnackBar, useValue: snackBar },
        { provide: BreakpointObserver, useValue: { observe: () => narrow } },
        { provide: MediaMatcher, useValue: { matchMedia: () => ({ matches: narrow.value.matches }) } },
      ],
    });
    i18n = TestBed.inject(I18nService);
    i18n.lang.set('en');
  });

  it('shows a tab for every section', async () => {
    const fixture = await create();
    const tabs: HTMLAnchorElement[] = Array.from(fixture.nativeElement.querySelectorAll('nav a.tab'));

    expect(tabs.map((t) => t.getAttribute('href'))).toEqual([
      '/c/mods',
      '/c/resourcepacks',
      '/c/datapacks',
      '/c/shaderpacks',
      '/worlds',
    ]);
    expect(tabs[0].textContent).toContain(i18n.t('app.tabs.mods'));
  });

  describe('launcher button', () => {
    it('is enabled when the launcher is found', async () => {
      const fixture = await create();

      expect(launchButton(fixture).disabled).toBe(false);
      expect(launchTooltip(fixture)).toBe(i18n.t('app.launcher.title'));
    });

    it('is disabled and explains why when the launcher is missing', async () => {
      api.launcherStatus.mockReturnValue(of({ available: false }));
      const fixture = await create();

      expect(launchButton(fixture).disabled).toBe(true);
      expect(launchTooltip(fixture)).toBe(i18n.t('app.launcher.notFound'));
    });

    it('is disabled when the status check fails', async () => {
      api.launcherStatus.mockReturnValue(throwError(() => new Error('down')));
      const fixture = await create();

      expect(fixture.componentInstance.launcherAvailable()).toBe(false);
      expect(launchButton(fixture).disabled).toBe(true);
    });

    it('shows progress while starting, then a success message', async () => {
      const launch = new Subject<void>();
      api.launchMinecraft.mockReturnValue(launch);
      const fixture = await create();

      launchButton(fixture).click();
      await fixture.whenStable();
      expect(launchButton(fixture).disabled).toBe(true);
      expect(launchButton(fixture).querySelector('.spin')).not.toBeNull();

      launch.next();
      launch.complete();
      await fixture.whenStable();
      expect(launchButton(fixture).disabled).toBe(false);
      expect(snackBar.open).toHaveBeenCalledWith(i18n.t('app.launcher.started'), undefined, { duration: 5000 });
    });

    it('shows the error when starting fails', async () => {
      api.launchMinecraft.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 500, error: { message: 'Could not start' } })),
      );
      const fixture = await create();

      launchButton(fixture).click();
      await fixture.whenStable();

      expect(snackBar.open).toHaveBeenCalledWith('Could not start', undefined, {
        duration: 5000,
        panelClass: 'snack-error',
      });
      expect(fixture.componentInstance.launching()).toBe(false);
    });

    it('stays disabled and says nothing until the status arrives', async () => {
      api.launcherStatus.mockReturnValue(new Subject<LauncherStatus>());
      const fixture = await create();

      expect(snackBar.open).not.toHaveBeenCalled();
      expect(launchButton(fixture).disabled).toBe(true);
    });
  });

  it('changes language from the switcher', async () => {
    const fixture = await create();

    fixture.nativeElement.querySelector('button.lang-switch').click();
    await settle(fixture);
    const options = Array.from(document.querySelectorAll<HTMLButtonElement>('button.lang-option'));

    expect(options).toHaveLength(i18n.languages.length);
    expect(options.find((o) => o.lang === 'en')?.getAttribute('aria-checked')).toBe('true');
    options.find((o) => o.lang === 'de')?.click();
    await settle(fixture);

    expect(i18n.lang()).toBe('de');
    expect(fixture.nativeElement.querySelector('a.settings').textContent).toContain(i18n.t('app.settingsLink'));
  });

  it('toggles the theme and describes what the button will do', async () => {
    const theme = TestBed.inject(ThemeService);
    theme.theme.set('dark');
    const fixture = await create();

    expect(tooltip(fixture, 'button.theme-toggle')).toBe(i18n.t('app.theme.toLight'));
    expect(themeIcon(fixture)).toBe('light_mode');

    themeButton(fixture).click();
    await fixture.whenStable();

    expect(theme.theme()).toBe('light');
    expect(tooltip(fixture, 'button.theme-toggle')).toBe(i18n.t('app.theme.toDark'));
    expect(themeIcon(fixture)).toBe('dark_mode');
  });

  describe('navigation', () => {
    it('stays open on a wide window, with no button for it', async () => {
      const fixture = await create();
      await settle(fixture);

      expect(drawerOpen(fixture)).toBe(true);
      expect(fixture.nativeElement.querySelector('.menu-toggle')).toBeNull();
    });

    it('becomes a closed drawer on a narrow window', async () => {
      narrow.next({ matches: true, breakpoints: {} });
      const fixture = await create();
      await settle(fixture);

      expect(drawerOpen(fixture)).toBe(false);
      expect(fixture.nativeElement.querySelector('.menu-toggle')).not.toBeNull();
    });

    it('opens the drawer from the button and closes it after choosing a section', async () => {
      narrow.next({ matches: true, breakpoints: {} });
      const fixture = await create();
      await settle(fixture);

      fixture.nativeElement.querySelector('.menu-toggle').click();
      await settle(fixture);
      expect(drawerOpen(fixture)).toBe(true);

      fixture.nativeElement.querySelector('nav a.tab').click();
      await settle(fixture);
      expect(drawerOpen(fixture)).toBe(false);
    });

    it('can be opened again after the drawer closes itself', async () => {
      narrow.next({ matches: true, breakpoints: {} });
      const fixture = await create();
      await settle(fixture);
      fixture.nativeElement.querySelector('.menu-toggle').click();
      await settle(fixture);

      // What the backdrop and Escape do.
      fixture.nativeElement.querySelector('mat-sidenav-container .mat-drawer-backdrop').click();
      await settle(fixture);
      expect(drawerOpen(fixture)).toBe(false);

      fixture.nativeElement.querySelector('.menu-toggle').click();
      await settle(fixture);
      expect(drawerOpen(fixture)).toBe(true);
    });

    it('opens up again when the window becomes wide', async () => {
      narrow.next({ matches: true, breakpoints: {} });
      const fixture = await create();
      await settle(fixture);

      narrow.next({ matches: false, breakpoints: {} });
      await settle(fixture);

      expect(drawerOpen(fixture)).toBe(true);
    });
  });
});
