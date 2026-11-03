import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NEVER, of, Subject, throwError } from 'rxjs';

import { ApiService } from '../api.service';
import { I18nService } from '../i18n/i18n.service';
import { WorldSummary } from '../models';
import { WorldsPage } from './worlds-page';

const WORLD: WorldSummary = {
  folder: 'w',
  name: 'My World',
  versionName: '1.21',
  lastPlayed: null,
  gameMode: 'survival',
  hardcore: false,
  hasIcon: true,
};

describe('WorldsPage', () => {
  let api: {
    worlds: ReturnType<typeof vi.fn>;
    world: ReturnType<typeof vi.fn>;
    worldIconUrl: ReturnType<typeof vi.fn>;
  };
  let i18n: I18nService;

  async function settle(fixture: ComponentFixture<WorldsPage>): Promise<void> {
    for (let i = 0; i < 3; i++) {
      await new Promise((resolve) => setTimeout(resolve));
      await fixture.whenStable();
    }
  }

  async function create(): Promise<ComponentFixture<WorldsPage>> {
    const fixture = TestBed.createComponent(WorldsPage);
    await settle(fixture);
    return fixture;
  }

  const tile = (f: ComponentFixture<WorldsPage>): HTMLButtonElement => f.nativeElement.querySelector('.tile');
  const refreshButton = (f: ComponentFixture<WorldsPage>): HTMLButtonElement =>
    f.nativeElement.querySelector('.actions button');

  beforeEach(() => {
    api = {
      worlds: vi.fn(() => of([WORLD])),
      world: vi.fn(() => NEVER),
      worldIconUrl: vi.fn((folder: string) => `/icon/${folder}`),
    };
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ApiService, useValue: api }] });
    i18n = TestBed.inject(I18nService);
    i18n.lang.set('en');
  });

  it('shows a loading message, then the worlds', async () => {
    const pending = new Subject<WorldSummary[]>();
    api.worlds.mockReturnValue(pending);
    const fixture = await create();

    expect(fixture.nativeElement.textContent).toContain(i18n.t('worlds.loadingLevelData'));
    expect(refreshButton(fixture).disabled).toBe(true);
    expect(refreshButton(fixture).textContent).toContain(i18n.t('worlds.reading'));

    pending.next([WORLD]);
    pending.complete();
    await settle(fixture);

    expect(fixture.nativeElement.querySelector('.count').textContent).toBe('1');
    expect(tile(fixture).querySelector('h3')?.textContent).toBe('My World');
    expect(tile(fixture).querySelector('.ver')?.textContent).toBe('1.21');
    expect(tile(fixture).querySelector('.sub')?.textContent).toContain(i18n.t('format.gameMode.survival'));
    expect(tile(fixture).querySelector('.sub')?.textContent).toContain(i18n.t('format.never'));
    expect(refreshButton(fixture).disabled).toBe(false);
    expect(refreshButton(fixture).textContent).toContain(i18n.t('worlds.refreshBtn'));
  });

  it('explains when there are no worlds', async () => {
    api.worlds.mockReturnValue(of([]));
    const fixture = await create();

    expect(fixture.nativeElement.querySelector('.empty').textContent).toContain(i18n.t('worlds.emptyPrefix'));
    expect(fixture.nativeElement.querySelector('.empty a').getAttribute('href')).toBe('/settings');
  });

  it('copes with missing details', async () => {
    api.worlds.mockReturnValue(of([{ ...WORLD, versionName: null, gameMode: null }]));
    const fixture = await create();

    expect(tile(fixture).querySelector('.ver')?.textContent).toBe(i18n.t('worlds.unknownVersion'));
    expect(tile(fixture).querySelector('.sub')?.textContent).toContain('—');
  });

  it('marks hardcore worlds', async () => {
    const fixture = await create();
    expect(tile(fixture).querySelector('.flag')).toBeNull();

    api.worlds.mockReturnValue(of([{ ...WORLD, hardcore: true }]));
    refreshButton(fixture).click();
    await settle(fixture);

    expect(tile(fixture).querySelector('.flag')?.textContent).toBe('Hardcore');
  });

  it('shows the error when loading fails, and clears it on retry', async () => {
    api.worlds.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500, error: { message: 'Bad' } })));
    const fixture = await create();
    expect(fixture.nativeElement.querySelector('.banner.error').textContent).toBe('Bad');
    expect(fixture.componentInstance.loading()).toBe(false);

    refreshButton(fixture).click();
    await settle(fixture);

    expect(fixture.nativeElement.querySelector('.banner.error')).toBeNull();
    expect(tile(fixture)).not.toBeNull();
  });

  describe('icons', () => {
    it('shows the world icon', async () => {
      const fixture = await create();

      expect(tile(fixture).querySelector('img')?.getAttribute('src')).toBe('/icon/w');
    });

    it('shows a placeholder when there is no icon', async () => {
      api.worlds.mockReturnValue(of([{ ...WORLD, hasIcon: false }]));
      const fixture = await create();

      expect(tile(fixture).querySelector('img')).toBeNull();
      expect(tile(fixture).querySelector('.ph')).not.toBeNull();
    });

    it('shows a placeholder when the icon fails to load', async () => {
      const fixture = await create();

      tile(fixture).querySelector('img')?.dispatchEvent(new Event('error'));
      await settle(fixture);

      expect(tile(fixture).querySelector('img')).toBeNull();
      expect(tile(fixture).querySelector('.ph')).not.toBeNull();
    });
  });

  it('opens the details of a world and closes them again', async () => {
    const fixture = await create();
    expect(fixture.nativeElement.querySelector('app-world-modal')).toBeNull();

    tile(fixture).click();
    await settle(fixture);

    expect(fixture.nativeElement.querySelector('app-world-modal')).not.toBeNull();
    expect(api.world).toHaveBeenCalledWith('w');

    fixture.nativeElement.querySelector('app-world-modal button.close').click();
    await settle(fixture);

    expect(fixture.nativeElement.querySelector('app-world-modal')).toBeNull();
  });
});
