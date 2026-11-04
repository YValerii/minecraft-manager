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
    updateWorld: ReturnType<typeof vi.fn>;
    resetWorldIcon: ReturnType<typeof vi.fn>;
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
      updateWorld: vi.fn(() => of(details())),
      resetWorldIcon: vi.fn(() => of(details())),
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

  describe('editing', () => {
    const q = (fixture: ComponentFixture<WorldModal>, selector: string) => fixture.nativeElement.querySelector(selector);

    function editable(overrides: Partial<WorldDetails> = {}): WorldDetails {
      return details({
        summary: { ...details().summary, gameMode: 'survival' },
        difficulty: 'hard',
        allowCommands: false,
        gameRules: { 'minecraft:keep_inventory': true, 'minecraft:respawn_radius': 10, custom: 'abc' },
        ...overrides,
      });
    }

    async function createEditing(world = editable()): Promise<ComponentFixture<WorldModal>> {
      api.world.mockReturnValue(of(world));
      const fixture = await create();
      q(fixture, 'button.edit-toggle').click();
      await settle(fixture);
      return fixture;
    }

    async function type(fixture: ComponentFixture<WorldModal>, selector: string, value: string): Promise<void> {
      const input = q(fixture, selector);
      input.value = value;
      input.dispatchEvent(new Event('input'));
      await settle(fixture);
    }

    async function choose(fixture: ComponentFixture<WorldModal>, selector: string, value: string): Promise<void> {
      const select = q(fixture, selector);
      select.value = value;
      select.dispatchEvent(new Event('change'));
      await settle(fixture);
    }

    async function tick(fixture: ComponentFixture<WorldModal>, selector: string): Promise<void> {
      q(fixture, selector).click();
      await settle(fixture);
    }

    afterEach(() => vi.unstubAllGlobals());

    it('is off by default, showing no form', async () => {
      api.world.mockReturnValue(of(editable()));
      const fixture = await create();

      expect(fixture.componentInstance.draft()).toBeNull();
      expect(q(fixture, 'button.edit-toggle').getAttribute('aria-pressed')).toBe('false');
      expect(q(fixture, 'button.edit-toggle').textContent).toContain(i18n.t('worldModal.edit.enable'));
      expect(q(fixture, '.editor')).toBeNull();
      expect(q(fixture, '.edit-actions')).toBeNull();
      expect(fixture.nativeElement.querySelector('.rule input')).toBeNull();
    });

    it('offers no switch until the world is loaded', async () => {
      api.world.mockReturnValue(failure('x'));
      const fixture = await create();

      expect(q(fixture, 'button.edit-toggle')).toBeNull();
    });

    it('shows the current values once switched on, with nothing to save yet', async () => {
      const fixture = await createEditing();

      expect(q(fixture, 'button.edit-toggle').getAttribute('aria-pressed')).toBe('true');
      expect(q(fixture, 'button.edit-toggle').textContent).toContain(i18n.t('worldModal.edit.disable'));
      expect(q(fixture, '.edit-name').value).toBe('My World');
      expect(q(fixture, '.edit-mode').value).toBe('survival');
      expect(q(fixture, '.edit-difficulty').value).toBe('hard');
      expect(q(fixture, '.edit-hardcore').checked).toBe(false);
      expect(q(fixture, '.edit-commands').checked).toBe(false);
      expect(fixture.componentInstance.changes()).toEqual({});
      expect(q(fixture, 'button.save').disabled).toBe(true);
    });

    it('uses a control that suits each game rule', async () => {
      const fixture = await createEditing();

      const input = (rule: string) => q(fixture, `.rule input[aria-label="${rule}"]`);
      expect(input('minecraft:keep_inventory').type).toBe('checkbox');
      expect(input('minecraft:keep_inventory').checked).toBe(true);
      expect(input('minecraft:respawn_radius').type).toBe('number');
      expect(input('minecraft:respawn_radius').value).toBe('10');
      expect(input('custom').type).toBe('text');
      expect(fixture.nativeElement.querySelector('.rule .badge')).toBeNull();
    });

    it('sends only what changed, then goes back to read-only with the new values', async () => {
      const updated = editable({ summary: { ...editable().summary, name: 'New' } });
      api.updateWorld.mockReturnValue(of(updated));
      const fixture = await createEditing();
      const changed = vi.fn();
      fixture.componentInstance.changed.subscribe(changed);

      await type(fixture, '.edit-name', '  New ');
      await choose(fixture, '.edit-mode', 'creative');
      await choose(fixture, '.edit-difficulty', 'easy');
      await tick(fixture, '.edit-hardcore');
      await tick(fixture, '.edit-commands');
      await tick(fixture, '.rule input[aria-label="minecraft:keep_inventory"]');
      await type(fixture, '.rule input[aria-label="minecraft:respawn_radius"]', '3');
      await tick(fixture, 'button.save');

      expect(api.updateWorld).toHaveBeenCalledWith('w', {
        name: 'New',
        gameMode: 'creative',
        difficulty: 'easy',
        hardcore: true,
        allowCommands: true,
        gameRules: { 'minecraft:keep_inventory': 'false', 'minecraft:respawn_radius': '3' },
      });
      expect(changed).toHaveBeenCalledTimes(1);
      expect(fixture.componentInstance.draft()).toBeNull();
      expect(q(fixture, 'h2').textContent).toBe('New');
      expect(q(fixture, '.banner.saved').textContent).toBe(i18n.t('worldModal.edit.saved'));
    });

    it('does not count a value changed back as a change', async () => {
      const fixture = await createEditing();

      await type(fixture, '.edit-name', 'Other');
      expect(fixture.componentInstance.dirty()).toBe(true);
      await type(fixture, '.edit-name', 'My World');

      expect(fixture.componentInstance.dirty()).toBe(false);
    });

    it('does not offer saving an empty name or a rule that is not a whole number', async () => {
      const fixture = await createEditing();

      await type(fixture, '.edit-name', '   ');
      expect(q(fixture, 'button.save').disabled).toBe(true);
      expect(q(fixture, '.edit-name').getAttribute('aria-invalid')).toBe('true');
      await fixture.componentInstance.save();
      expect(api.updateWorld).not.toHaveBeenCalled();

      await type(fixture, '.edit-name', 'New');
      expect(q(fixture, 'button.save').disabled).toBe(false);
      await type(fixture, '.rule input[aria-label="minecraft:respawn_radius"]', '');
      expect(q(fixture, 'button.save').disabled).toBe(true);
    });

    it('keeps the form and shows the error when saving fails, such as for an open world', async () => {
      api.updateWorld.mockReturnValue(failure('This world is open in Minecraft.'));
      const fixture = await createEditing();
      const changed = vi.fn();
      fixture.componentInstance.changed.subscribe(changed);

      await type(fixture, '.edit-name', 'New');
      await tick(fixture, 'button.save');

      expect(q(fixture, '.edit-actions .banner.error').textContent).toBe('This world is open in Minecraft.');
      expect(q(fixture, '.edit-name').value).toBe('New');
      expect(q(fixture, 'button.save').disabled).toBe(false);
      expect(changed).not.toHaveBeenCalled();
    });

    it('blocks the buttons while saving', async () => {
      const pending = new Subject<WorldDetails>();
      api.updateWorld.mockReturnValue(pending);
      const fixture = await createEditing();

      await type(fixture, '.edit-name', 'New');
      await tick(fixture, 'button.save');

      expect(q(fixture, 'button.save').disabled).toBe(true);
      expect(q(fixture, 'button.save').textContent).toContain(i18n.t('worldModal.edit.saving'));
      expect(q(fixture, 'button.cancel').disabled).toBe(true);
      expect(q(fixture, 'button.edit-toggle').disabled).toBe(true);

      pending.next(editable());
      pending.complete();
      await settle(fixture);
      expect(fixture.componentInstance.busy()).toBe(false);
    });

    it('reloads the raw data if it is open, and otherwise forgets it', async () => {
      const fixture = await createEditing();
      await toggleRaw(fixture, true);
      expect(api.worldRaw).toHaveBeenCalledTimes(1);

      await type(fixture, '.edit-name', 'New');
      await tick(fixture, 'button.save');
      expect(api.worldRaw).toHaveBeenCalledTimes(2);

      await toggleRaw(fixture, false);
      q(fixture, 'button.edit-toggle').click();
      await settle(fixture);
      await type(fixture, '.edit-name', 'Newer');
      await tick(fixture, 'button.save');
      expect(api.worldRaw).toHaveBeenCalledTimes(2);
      expect(fixture.componentInstance.raw()).toBeNull();
    });

    it('switches off straight away when nothing changed', async () => {
      const confirm = vi.fn(() => true);
      vi.stubGlobal('confirm', confirm);
      const fixture = await createEditing();

      await tick(fixture, 'button.edit-toggle');

      expect(confirm).not.toHaveBeenCalled();
      expect(fixture.componentInstance.draft()).toBeNull();
    });

    for (const button of ['button.edit-toggle', 'button.cancel']) {
      it(`asks before ${button} discards changes`, async () => {
        const confirm = vi.fn(() => false);
        vi.stubGlobal('confirm', confirm);
        const fixture = await createEditing();
        await type(fixture, '.edit-name', 'New');

        await tick(fixture, button);
        expect(confirm).toHaveBeenCalledWith(i18n.t('worldModal.edit.discardConfirm'));
        expect(fixture.componentInstance.draft()).not.toBeNull();

        confirm.mockReturnValue(true);
        await tick(fixture, button);
        expect(fixture.componentInstance.draft()).toBeNull();
        expect(api.updateWorld).not.toHaveBeenCalled();
      });
    }

    it('asks before closing discards changes', async () => {
      const confirm = vi.fn(() => false);
      vi.stubGlobal('confirm', confirm);
      const fixture = await createEditing();
      const closed = vi.fn();
      fixture.componentInstance.closed.subscribe(closed);
      await type(fixture, '.edit-name', 'New');

      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
      q(fixture, '.backdrop').click();
      expect(closed).not.toHaveBeenCalled();

      confirm.mockReturnValue(true);
      q(fixture, 'button.close').click();
      expect(closed).toHaveBeenCalledTimes(1);
    });

    it('starts over when the folder changes', async () => {
      const fixture = await createEditing();
      await type(fixture, '.edit-name', 'New');

      fixture.componentRef.setInput('folder', 'other');
      await settle(fixture);

      expect(fixture.componentInstance.draft()).toBeNull();
    });

    it('leaves unknown values alone, showing them as they are', async () => {
      const world = editable({ summary: { ...editable().summary, gameMode: 'custom' }, difficulty: null, allowCommands: null });
      const fixture = await createEditing(world);

      expect(q(fixture, '.edit-mode').value).toBe('custom');
      expect(q(fixture, '.edit-difficulty').value).toBe('');
      expect(q(fixture, '.edit-difficulty option').textContent).toBe('—');
      expect(q(fixture, '.edit-commands').checked).toBe(false);
      expect(fixture.componentInstance.changes()).toEqual({});
    });

    describe('resetting the icon', () => {
      const withoutIcon = () => editable({ summary: { ...editable().summary, hasIcon: false } });

      it('asks first', async () => {
        vi.stubGlobal('confirm', vi.fn(() => false));
        const fixture = await createEditing();

        await tick(fixture, 'button.reset-icon');

        expect(api.resetWorldIcon).not.toHaveBeenCalled();
      });

      it('removes the icon, even one the browser still has cached, and keeps unsaved edits', async () => {
        vi.stubGlobal('confirm', vi.fn(() => true));
        api.resetWorldIcon.mockReturnValue(of(withoutIcon()));
        const fixture = await createEditing();
        const changed = vi.fn();
        fixture.componentInstance.changed.subscribe(changed);
        await type(fixture, '.edit-name', 'New');

        await tick(fixture, 'button.reset-icon');

        expect(api.resetWorldIcon).toHaveBeenCalledWith('w');
        expect(changed).toHaveBeenCalledTimes(1);
        expect(q(fixture, 'img')).toBeNull();
        expect(q(fixture, 'button.reset-icon')).toBeNull();
        expect(q(fixture, '.edit-name').value).toBe('New');
      });

      it('shows the error when it fails', async () => {
        vi.stubGlobal('confirm', vi.fn(() => true));
        api.resetWorldIcon.mockReturnValue(failure('Open'));
        const fixture = await createEditing();

        await tick(fixture, 'button.reset-icon');

        expect(q(fixture, '.edit-actions .banner.error').textContent).toBe('Open');
      });

      it('is not offered without an icon', async () => {
        const fixture = await createEditing(withoutIcon());

        expect(q(fixture, 'button.reset-icon')).toBeNull();
      });
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
