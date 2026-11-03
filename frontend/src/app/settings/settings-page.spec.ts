import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';

import { ApiService } from '../api.service';
import { I18nService } from '../i18n/i18n.service';
import { PathInfo, Settings } from '../models';
import { SettingsPage } from './settings-page';

const SETTINGS: Settings = {
  minecraftPath: '/mc',
  gameVersion: '1.21',
  loader: 'fabric',
  provider: 'MODRINTH',
  curseforgeApiKey: null,
  modsDir: 'mods',
  resourcepacksDir: 'resourcepacks',
  shaderpacksDir: 'shaderpacks',
  datapacksDir: 'datapacks',
  savesDir: 'saves',
};

const PATHS: PathInfo[] = [
  { key: 'root', label: 'Minecraft folder', path: '/mc', exists: true },
  { key: 'mods', label: 'Mods', path: '/mc/mods', exists: false },
];

const failure = (message: string) => throwError(() => new HttpErrorResponse({ status: 400, error: { message } }));

describe('SettingsPage', () => {
  let api: {
    settings: ReturnType<typeof vi.fn>;
    paths: ReturnType<typeof vi.fn>;
    saveSettings: ReturnType<typeof vi.fn>;
  };
  let i18n: I18nService;

  async function settle(fixture: ComponentFixture<SettingsPage>): Promise<void> {
    for (let i = 0; i < 3; i++) {
      await new Promise((resolve) => setTimeout(resolve));
      await fixture.whenStable();
    }
  }

  async function create(): Promise<ComponentFixture<SettingsPage>> {
    const fixture = TestBed.createComponent(SettingsPage);
    await settle(fixture);
    return fixture;
  }

  const text = (f: ComponentFixture<SettingsPage>): string => f.nativeElement.textContent;

  async function submit(f: ComponentFixture<SettingsPage>): Promise<void> {
    f.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    await settle(f);
  }

  beforeEach(() => {
    api = {
      settings: vi.fn(() => of(SETTINGS)),
      paths: vi.fn(() => of(PATHS)),
      saveSettings: vi.fn((s: Settings) => of(s)),
    };
    TestBed.configureTestingModule({ providers: [{ provide: ApiService, useValue: api }] });
    i18n = TestBed.inject(I18nService);
    i18n.lang.set('en');
  });

  it('shows a loading message until the settings arrive', async () => {
    const pending = new Subject<Settings>();
    api.settings.mockReturnValue(pending);
    const fixture = await create();

    expect(text(fixture)).toContain(i18n.t('settings.loading'));
    expect(fixture.nativeElement.querySelector('form')).toBeNull();

    pending.next(SETTINGS);
    pending.complete();
    await settle(fixture);

    expect(text(fixture)).not.toContain(i18n.t('settings.loading'));
    expect(fixture.nativeElement.querySelector('form')).not.toBeNull();
  });

  it('fills the form, showing a missing API key as empty', async () => {
    const fixture = await create();

    expect(fixture.componentInstance.form.getRawValue()).toEqual({ ...SETTINGS, curseforgeApiKey: '' });
    const version: HTMLInputElement = fixture.nativeElement.querySelector('input[formControlName=gameVersion]');
    expect(version.value).toBe('1.21');
  });

  it('keeps a saved API key', async () => {
    api.settings.mockReturnValue(of({ ...SETTINGS, curseforgeApiKey: 'secret' }));
    const fixture = await create();

    expect(fixture.componentInstance.form.getRawValue().curseforgeApiKey).toBe('secret');
  });

  it('shows the error when loading fails', async () => {
    api.settings.mockReturnValue(failure('Cannot read'));
    const fixture = await create();

    expect(fixture.nativeElement.querySelector('.banner.error').textContent).toBe('Cannot read');
    expect(fixture.componentInstance.loading()).toBe(false);
  });

  it('lists resolved folders and whether they exist', async () => {
    const fixture = await create();
    const items: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('.resolved li'));

    expect(items).toHaveLength(2);
    expect(items[0].textContent).toContain('/mc');
    expect(items[0].querySelector('.mark')?.classList).toContain('ok');
    expect((items[0].querySelector('.mark') as HTMLElement).title).toBe(i18n.t('settings.folderExistsTitle'));
    expect(items[1].querySelector('.mark')?.classList).toContain('bad');
    expect((items[1].querySelector('.mark') as HTMLElement).title).toBe(i18n.t('settings.folderMissingTitle'));
  });

  it('hides the resolved folders when there are none', async () => {
    api.paths.mockReturnValue(of([]));
    const fixture = await create();

    expect(fixture.nativeElement.querySelector('.resolved')).toBeNull();
  });

  describe('API key warning', () => {
    const warning = (f: ComponentFixture<SettingsPage>) => f.nativeElement.querySelector('.banner.warn');

    it('only appears for CurseForge without a key', async () => {
      const fixture = await create();
      const { form } = fixture.componentInstance;
      expect(warning(fixture)).toBeNull();

      form.controls.provider.setValue('CURSEFORGE');
      await fixture.whenStable();
      expect(warning(fixture).textContent).toBe(i18n.t('settings.needsKeyWarning'));

      form.controls.curseforgeApiKey.setValue('   ');
      await fixture.whenStable();
      expect(warning(fixture)).not.toBeNull();

      form.controls.curseforgeApiKey.setValue('key');
      await fixture.whenStable();
      expect(warning(fixture)).toBeNull();
    });

    it('copes with a form value without the key', async () => {
      const fixture = await create();
      const { form } = fixture.componentInstance;

      form.controls.provider.setValue('CURSEFORGE');
      form.controls.curseforgeApiKey.disable();
      await fixture.whenStable();

      expect(fixture.componentInstance.needsKey()).toBe(true);
    });
  });

  describe('save', () => {
    it('sends the form and refreshes the folders', async () => {
      const fixture = await create();
      const { form } = fixture.componentInstance;
      form.controls.gameVersion.setValue('1.20');
      form.controls.curseforgeApiKey.setValue('key');
      api.paths.mockClear();

      await submit(fixture);

      expect(api.saveSettings).toHaveBeenCalledWith({ ...SETTINGS, gameVersion: '1.20', curseforgeApiKey: 'key' });
      expect(api.paths).toHaveBeenCalledTimes(1);
      expect(fixture.nativeElement.querySelector('.banner.ok').textContent).toBe(i18n.t('settings.savedBanner'));
    });

    it('sends an empty API key as null', async () => {
      const fixture = await create();

      await submit(fixture);

      expect(api.saveSettings).toHaveBeenCalledWith(SETTINGS);
    });

    it('shows what the server actually saved', async () => {
      api.saveSettings.mockReturnValue(of({ ...SETTINGS, loader: 'normalized' }));
      const fixture = await create();

      await submit(fixture);

      expect(fixture.componentInstance.form.getRawValue().loader).toBe('normalized');
    });

    it('disables the button while saving', async () => {
      const pending = new Subject<Settings>();
      api.saveSettings.mockReturnValue(pending);
      const fixture = await create();

      await submit(fixture);
      const button: HTMLButtonElement = fixture.nativeElement.querySelector('button[type=submit]');
      expect(button.disabled).toBe(true);
      expect(button.textContent).toContain(i18n.t('settings.saving'));

      pending.next(SETTINGS);
      pending.complete();
      await settle(fixture);
      expect(button.disabled).toBe(false);
      expect(button.textContent).toContain(i18n.t('settings.saveBtn'));
    });

    it('shows the error when saving fails', async () => {
      api.saveSettings.mockReturnValue(failure('Field is required'));
      const fixture = await create();

      await submit(fixture);

      expect(fixture.nativeElement.querySelector('.banner.error').textContent).toBe('Field is required');
      expect(fixture.nativeElement.querySelector('.banner.ok')).toBeNull();
      expect(fixture.componentInstance.saving()).toBe(false);
    });

    it('clears an earlier error and success message when saving again', async () => {
      api.saveSettings.mockReturnValueOnce(failure('Nope'));
      const fixture = await create();

      await submit(fixture);
      expect(fixture.componentInstance.error()).toBe('Nope');

      await submit(fixture);
      expect(fixture.componentInstance.error()).toBeNull();
      expect(fixture.componentInstance.saved()).toBe(true);
    });
  });
});
