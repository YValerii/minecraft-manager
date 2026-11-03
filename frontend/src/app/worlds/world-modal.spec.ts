import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';

import { ApiService } from '../api.service';
import { I18nService } from '../i18n/i18n.service';
import { WorldDetails } from '../models';
import { WorldModal } from './world-modal';

function details(overrides: Partial<WorldDetails> = {}): WorldDetails {
  return {
    summary: {
      folder: 'w',
      name: 'My World',
      versionName: '1.21',
      lastPlayed: null,
      gameMode: null,
      hardcore: false,
      hasIcon: true,
    },
    path: '/saves/w',
    sizeBytes: 2048,
    fileCount: 3,
    levelDatModified: null,
    dataVersion: null,
    versionId: null,
    snapshot: null,
    difficulty: null,
    allowCommands: null,
    seed: null,
    spawn: null,
    gameTimeTicks: null,
    dayTimeTicks: null,
    daysPlayed: null,
    raining: null,
    thundering: null,
    borderSize: null,
    serverBrands: [],
    wasModded: null,
    enabledDatapacks: [],
    disabledDatapacks: [],
    enabledFeatures: [],
    player: null,
    playerCount: 0,
    dimensions: [],
    gameRules: null,
    ...overrides,
  };
}

const failure = (message: string) => throwError(() => new HttpErrorResponse({ status: 422, error: { message } }));

describe('WorldModal', () => {
  let api: {
    world: ReturnType<typeof vi.fn>;
    worldRaw: ReturnType<typeof vi.fn>;
    worldIconUrl: ReturnType<typeof vi.fn>;
  };
  let i18n: I18nService;

  async function settle(fixture: ComponentFixture<WorldModal>): Promise<void> {
    for (let i = 0; i < 3; i++) {
      await new Promise((resolve) => setTimeout(resolve));
      await fixture.whenStable();
    }
  }

  async function create(folder = 'w'): Promise<ComponentFixture<WorldModal>> {
    const fixture = TestBed.createComponent(WorldModal);
    fixture.componentRef.setInput('folder', folder);
    await settle(fixture);
    return fixture;
  }

  /** The label -> value rows of a section, or undefined when the section isn't shown. */
  function rows(fixture: ComponentFixture<WorldModal>, sectionKey: string): Record<string, string> | undefined {
    const title = i18n.t(`worldModal.sections.${sectionKey}`);
    const section = fixture.componentInstance.sections().find((s) => s.title === title);
    return section && Object.fromEntries(section.rows.map((r) => [r.label, r.value]));
  }

  const row = (key: string) => i18n.t(`worldModal.rows.${key}`);

  async function toggleRaw(fixture: ComponentFixture<WorldModal>, open: boolean): Promise<void> {
    const raw: HTMLDetailsElement = fixture.nativeElement.querySelector('details.raw');
    raw.open = open;
    raw.dispatchEvent(new Event('toggle'));
    await settle(fixture);
  }

  beforeEach(() => {
    api = {
      world: vi.fn(() => of(details())),
      worldRaw: vi.fn(() => of({ Data: { a: 1 } })),
      worldIconUrl: vi.fn((folder: string) => `/icon/${folder}`),
    };
    TestBed.configureTestingModule({ providers: [{ provide: ApiService, useValue: api }] });
    i18n = TestBed.inject(I18nService);
    i18n.lang.set('en');
  });

  describe('loading', () => {
    it('shows the folder and a message until the details arrive', async () => {
      const pending = new Subject<WorldDetails>();
      api.world.mockReturnValue(pending);
      const fixture = await create('folder-name');

      expect(fixture.nativeElement.querySelector('h2').textContent).toBe('folder-name');
      expect(fixture.nativeElement.textContent).toContain(i18n.t('worldModal.loadingLevelDat'));

      pending.next(details());
      pending.complete();
      await settle(fixture);

      expect(fixture.nativeElement.querySelector('h2').textContent).toBe('My World');
      expect(fixture.nativeElement.textContent).not.toContain(i18n.t('worldModal.loadingLevelDat'));
    });

    it('shows the error when loading fails', async () => {
      api.world.mockReturnValue(failure('Could not read level.dat'));
      const fixture = await create();

      expect(fixture.nativeElement.querySelector('.banner.error').textContent).toBe('Could not read level.dat');
      expect(fixture.nativeElement.querySelector('details.raw')).toBeNull();
    });

    it('loads again when the folder changes, starting clean', async () => {
      const fixture = await create('a');
      fixture.nativeElement.querySelector('img').dispatchEvent(new Event('error'));
      await toggleRaw(fixture, true);
      expect(fixture.componentInstance.raw()).not.toBeNull();

      fixture.componentRef.setInput('folder', 'b');
      await settle(fixture);

      expect(api.world).toHaveBeenLastCalledWith('b');
      expect(fixture.componentInstance.raw()).toBeNull();
      expect(fixture.componentInstance.iconBroken()).toBe(false);
      expect(fixture.nativeElement.querySelector('img').getAttribute('src')).toBe('/icon/b');
    });

    it('ignores a late answer for a folder no longer shown', async () => {
      const first = new Subject<WorldDetails>();
      api.world.mockReturnValueOnce(first);
      const fixture = await create('a');
      const pendingSecond = new Subject<WorldDetails>();
      api.world.mockReturnValueOnce(pendingSecond);
      fixture.componentRef.setInput('folder', 'b');
      await settle(fixture);

      first.next(details());
      first.complete();
      await settle(fixture);

      expect(fixture.componentInstance.details()).toBeNull();
      expect(fixture.componentInstance.loading()).toBe(true);
    });

    it('ignores a late failure for a folder no longer shown', async () => {
      const first = new Subject<WorldDetails>();
      api.world.mockReturnValueOnce(first);
      const fixture = await create('a');
      fixture.componentRef.setInput('folder', 'b');
      await settle(fixture);

      first.error(new Error('late'));
      await settle(fixture);

      expect(fixture.componentInstance.error()).toBeNull();
    });
  });

  describe('header', () => {
    it('shows the version, or a fallback', async () => {
      const fixture = await create();
      expect(fixture.nativeElement.querySelector('.ver').textContent).toBe('1.21');

      api.world.mockReturnValue(of(details({ summary: { ...details().summary, versionName: null } })));
      fixture.componentRef.setInput('folder', 'other');
      await settle(fixture);
      expect(fixture.nativeElement.querySelector('.ver').textContent).toBe(i18n.t('worldModal.unknownVersion'));
    });

    it('marks hardcore worlds', async () => {
      api.world.mockReturnValue(of(details({ summary: { ...details().summary, hardcore: true } })));
      const fixture = await create();

      expect(fixture.nativeElement.querySelector('.titles .badge.danger').textContent).toBe('Hardcore');
    });

    it('replaces an icon that fails to load', async () => {
      const fixture = await create();

      fixture.nativeElement.querySelector('img').dispatchEvent(new Event('error'));
      await settle(fixture);

      expect(fixture.nativeElement.querySelector('img')).toBeNull();
    });
  });

  describe('sections', () => {
    it('is empty before the details arrive', async () => {
      api.world.mockReturnValue(failure('x'));
      const fixture = await create();

      expect(fixture.componentInstance.sections()).toEqual([]);
    });

    it('leaves out everything that is unknown', async () => {
      const fixture = await create();

      const titles = fixture.componentInstance.sections().map((s) => s.title);
      expect(titles).toContain(i18n.t('worldModal.sections.general'));
      expect(titles).not.toContain(i18n.t('worldModal.sections.player'));
      expect(titles).not.toContain(i18n.t('worldModal.sections.datapacksFeatures'));
      expect(rows(fixture, 'version')).toEqual({ [row('gameVersion')]: '1.21' });
    });

    it('renders the rows', async () => {
      const fixture = await create();
      const dts: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('dt'));

      expect(dts.map((d) => d.textContent)).toContain(row('path'));
      expect(fixture.nativeElement.querySelector('dd').textContent).toBe('My World');
    });

    it('shows general and version details', async () => {
      api.world.mockReturnValue(
        of(
          details({
            versionId: 3953,
            dataVersion: 3954,
            snapshot: true,
            serverBrands: ['fabric', 'vanilla'],
            wasModded: false,
            levelDatModified: 1000,
          }),
        ),
      );
      const fixture = await create();

      expect(rows(fixture, 'general')).toMatchObject({
        [row('folder')]: 'w',
        [row('path')]: '/saves/w',
        [row('fileCount')]: '3',
      });
      expect(rows(fixture, 'version')).toEqual({
        [row('gameVersion')]: '1.21',
        [row('versionId')]: '3953',
        [row('dataVersion')]: '3954',
        [row('snapshot')]: i18n.t('format.yes'),
        [row('createdWith')]: 'fabric, vanilla',
        [row('wasModded')]: i18n.t('format.no'),
      });
    });

    it('shows game details', async () => {
      api.world.mockReturnValue(
        of(
          details({
            summary: { ...details().summary, gameMode: 'creative' },
            difficulty: 'hard',
            allowCommands: true,
            seed: '42',
            spawn: { x: 1, y: 64, z: -3 },
            daysPlayed: 2,
            gameTimeTicks: 48000,
            borderSize: 1000,
            dimensions: ['overworld', 'mymod:skylands'],
            playerCount: 2,
          }),
        ),
      );
      const fixture = await create();

      expect(rows(fixture, 'game')).toEqual({
        [row('mode')]: i18n.t('format.gameMode.creative'),
        [row('difficulty')]: i18n.t('format.difficulty.hard'),
        [row('hardcore')]: i18n.t('format.no'),
        [row('commandsAllowed')]: i18n.t('format.yes'),
        [row('seed')]: '42',
        [row('spawnPoint')]: '1, 64, -3',
        [row('playTime')]: i18n.t('worldModal.playTimeFormat', { days: '2', ticks: '48,000' }),
        [row('worldBorderSize')]: i18n.t('worldModal.blocksFormat', { size: '1,000' }),
        [row('dimensions')]: `${i18n.t('format.dimension.overworld')}, mymod:skylands`,
        [row('playersInWorld')]: '2',
      });
    });

    it('treats missing ticks as zero', async () => {
      api.world.mockReturnValue(of(details({ daysPlayed: 1, gameTimeTicks: null })));
      const fixture = await create();

      expect(rows(fixture, 'game')?.[row('playTime')]).toBe(
        i18n.t('worldModal.playTimeFormat', { days: '1', ticks: '0' }),
      );
    });

    const weatherCases: [boolean | null, boolean | null, string][] = [
      [true, true, 'thunder'],
      [true, false, 'rain'],
      [false, true, 'thunder'],
      [false, false, 'clear'],
      [null, false, 'clear'],
      [true, null, 'rain'],
    ];
    for (const [raining, thundering, expected] of weatherCases) {
      it(`describes the weather as ${expected} (rain ${raining}, thunder ${thundering})`, async () => {
        api.world.mockReturnValue(of(details({ raining, thundering })));
        const fixture = await create();

        expect(rows(fixture, 'game')?.[row('weather')]).toBe(i18n.t(`worldModal.weather.${expected}`));
      });
    }

    it('shows the player, rounding the position', async () => {
      api.world.mockReturnValue(
        of(
          details({
            player: {
              dimension: 'minecraft:the_end',
              x: 1.4,
              y: 64.6,
              z: -2.5,
              health: 20,
              food: 18,
              xpLevel: 0,
              gameMode: 'survival',
            },
          }),
        ),
      );
      const fixture = await create();

      expect(rows(fixture, 'player')).toEqual({
        [row('dimension')]: i18n.t('format.dimension.the_end'),
        [row('position')]: '1, 65, -2',
        [row('health')]: '20',
        [row('food')]: '18',
        [row('xpLevel')]: '0',
        [row('playerMode')]: i18n.t('format.gameMode.survival'),
      });
    });

    const coordinates = [
      { x: null, y: 1, z: 1 },
      { x: 1, y: null, z: 1 },
      { x: 1, y: 1, z: null },
    ];
    for (const position of coordinates) {
      it(`leaves out the position when incomplete (${JSON.stringify(position)})`, async () => {
        const player = { dimension: 'x', health: null, food: null, xpLevel: null, gameMode: null, ...position };
        api.world.mockReturnValue(of(details({ player })));
        const fixture = await create();

        expect(rows(fixture, 'player')).toEqual({ [row('dimension')]: 'x' });
      });
    }

    it('shows datapacks and features', async () => {
      api.world.mockReturnValue(
        of(details({ enabledDatapacks: ['a', 'b'], disabledDatapacks: ['c'], enabledFeatures: ['d'] })),
      );
      const fixture = await create();

      expect(rows(fixture, 'datapacksFeatures')).toEqual({
        [row('enabledDatapacks')]: 'a, b',
        [row('disabledDatapacks')]: 'c',
        [row('enabledFeatures')]: 'd',
      });
    });
  });

  describe('game rules', () => {
    it('are sorted, with badges for true and false', async () => {
      api.world.mockReturnValue(of(details({ gameRules: { zeta: 'false', alpha: 'true', mid: 5 } })));
      const fixture = await create();

      expect(fixture.componentInstance.gameRules()).toEqual([
        { name: 'alpha', value: 'true' },
        { name: 'mid', value: '5' },
        { name: 'zeta', value: 'false' },
      ]);
      const badges: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('.rule .badge'));
      expect(badges[0].classList).toContain('ok');
      expect(badges[1].classList).not.toContain('ok');
      expect(badges[1].classList).not.toContain('danger');
      expect(badges[2].classList).toContain('danger');
      expect(fixture.nativeElement.textContent).toContain(i18n.t('worldModal.gameRulesHeading', { count: 3 }));
    });

    it('are left out when there are none or they are unusable', async () => {
      const fixture = await create();
      expect(fixture.componentInstance.gameRules()).toEqual([]);
      expect(fixture.nativeElement.querySelector('.rules')).toBeNull();

      api.world.mockReturnValue(of(details({ gameRules: 'text' as unknown as Record<string, unknown> })));
      fixture.componentRef.setInput('folder', 'other');
      await settle(fixture);
      expect(fixture.componentInstance.gameRules()).toEqual([]);
    });
  });

  describe('raw data', () => {
    it('only loads once opened', async () => {
      const fixture = await create();
      expect(api.worldRaw).not.toHaveBeenCalled();

      await toggleRaw(fixture, true);

      expect(api.worldRaw).toHaveBeenCalledWith('w');
      expect(fixture.nativeElement.querySelector('details.raw pre').textContent).toBe(
        JSON.stringify({ Data: { a: 1 } }, null, 2),
      );
    });

    it('does nothing when closing', async () => {
      const fixture = await create();

      await toggleRaw(fixture, false);

      expect(api.worldRaw).not.toHaveBeenCalled();
    });

    it('is only loaded once', async () => {
      const fixture = await create();

      await toggleRaw(fixture, true);
      await toggleRaw(fixture, true);

      expect(api.worldRaw).toHaveBeenCalledTimes(1);
    });

    it('shows progress and does not load twice at the same time', async () => {
      const pending = new Subject<unknown>();
      api.worldRaw.mockReturnValue(pending);
      const fixture = await create();

      await toggleRaw(fixture, true);
      expect(fixture.nativeElement.querySelector('details.raw').textContent).toContain(i18n.t('worldModal.loadingRaw'));
      await toggleRaw(fixture, true);
      expect(api.worldRaw).toHaveBeenCalledTimes(1);

      pending.next({});
      pending.complete();
      await settle(fixture);
      expect(fixture.componentInstance.rawLoading()).toBe(false);
    });

    it('shows the error when loading fails', async () => {
      api.worldRaw.mockReturnValue(failure('Broken'));
      const fixture = await create();

      await toggleRaw(fixture, true);

      expect(fixture.componentInstance.raw()).toBe(i18n.t('worldModal.rawError', { message: 'Broken' }));
      expect(fixture.componentInstance.rawLoading()).toBe(false);
    });
  });

  describe('closing', () => {
    let closed: ReturnType<typeof vi.fn<() => void>>;

    async function createWatched(): Promise<ComponentFixture<WorldModal>> {
      const fixture = await create();
      closed = vi.fn<() => void>();
      fixture.componentInstance.closed.subscribe(() => closed());
      return fixture;
    }

    it('closes with the button', async () => {
      const fixture = await createWatched();

      fixture.nativeElement.querySelector('button.close').click();

      expect(closed).toHaveBeenCalledTimes(1);
    });

    it('closes with Escape', async () => {
      await createWatched();

      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));

      expect(closed).toHaveBeenCalledTimes(1);
    });

    it('closes when clicking outside the dialog', async () => {
      const fixture = await createWatched();

      fixture.nativeElement.querySelector('.backdrop').click();

      expect(closed).toHaveBeenCalledTimes(1);
    });

    it('stays open when clicking inside the dialog', async () => {
      const fixture = await createWatched();

      fixture.nativeElement.querySelector('.dialog').click();

      expect(closed).not.toHaveBeenCalled();
    });
  });
});
