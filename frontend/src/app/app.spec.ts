import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

import { ApiService } from './api.service';
import { App } from './app';
import { I18nService } from './i18n/i18n.service';
import { LauncherStatus } from './models';
import { ThemeService } from './theme.service';

describe('App', () => {
  let api: { launcherStatus: ReturnType<typeof vi.fn>; launchMinecraft: ReturnType<typeof vi.fn> };
  let i18n: I18nService;

  async function create(): Promise<ComponentFixture<App>> {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    return fixture;
  }

  const launchButton = (f: ComponentFixture<App>): HTMLButtonElement =>
    f.nativeElement.querySelector('button.launch');
  const themeButton = (f: ComponentFixture<App>): HTMLButtonElement =>
    f.nativeElement.querySelector('button.theme-toggle');

  beforeEach(() => {
    api = {
      launcherStatus: vi.fn(() => of({ available: true })),
      launchMinecraft: vi.fn(() => of(undefined)),
    };
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: ApiService, useValue: api }],
    });
    i18n = TestBed.inject(I18nService);
    i18n.lang.set('en');
  });

  afterEach(() => vi.useRealTimers());

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
      expect(launchButton(fixture).title).toBe(i18n.t('app.launcher.title'));
    });

    it('is disabled and explains why when the launcher is missing', async () => {
      api.launcherStatus.mockReturnValue(of({ available: false }));
      const fixture = await create();

      expect(launchButton(fixture).disabled).toBe(true);
      expect(launchButton(fixture).title).toBe(i18n.t('app.launcher.notFound'));
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
      const toast: HTMLElement = fixture.nativeElement.querySelector('.toast');
      expect(toast.textContent).toBe(i18n.t('app.launcher.started'));
      expect(toast.classList).toContain('ok');
    });

    it('shows the error when starting fails', async () => {
      api.launchMinecraft.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 500, error: { message: 'Could not start' } })),
      );
      const fixture = await create();

      launchButton(fixture).click();
      await fixture.whenStable();

      const toast: HTMLElement = fixture.nativeElement.querySelector('.toast');
      expect(toast.textContent).toBe('Could not start');
      expect(toast.classList).toContain('error');
      expect(fixture.componentInstance.launching()).toBe(false);
    });

    it('hides the message after a few seconds, counting from the latest one', async () => {
      const fixture = await create();
      const app = fixture.componentInstance;
      vi.useFakeTimers();

      app.launchMinecraft();
      vi.advanceTimersByTime(4000);
      app.launchMinecraft();
      vi.advanceTimersByTime(4000);
      expect(app.toast()).not.toBeNull();

      vi.advanceTimersByTime(1000);
      expect(app.toast()).toBeNull();
    });
  });

  it('changes language from the switcher', async () => {
    const fixture = await create();
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('select.lang-switch');

    expect(select.options).toHaveLength(i18n.languages.length);
    select.value = 'de';
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();

    expect(i18n.lang()).toBe('de');
    expect(fixture.nativeElement.querySelector('a.settings').textContent).toContain(i18n.t('app.settingsLink'));
  });

  it('toggles the theme and describes what the button will do', async () => {
    const theme = TestBed.inject(ThemeService);
    theme.theme.set('dark');
    const fixture = await create();

    expect(themeButton(fixture).title).toBe(i18n.t('app.theme.toLight'));
    expect(themeButton(fixture).textContent).toContain('☀️');

    themeButton(fixture).click();
    await fixture.whenStable();

    expect(theme.theme()).toBe('light');
    expect(themeButton(fixture).title).toBe(i18n.t('app.theme.toDark'));
    expect(themeButton(fixture).textContent).toContain('🌙');
  });

  it('has no message until there is something to say', async () => {
    const status = new Subject<LauncherStatus>();
    api.launcherStatus.mockReturnValue(status);
    const fixture = await create();

    expect(fixture.nativeElement.querySelector('.toast')).toBeNull();
    expect(launchButton(fixture).disabled).toBe(true);
  });
});
