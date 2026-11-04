import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatTooltip } from '@angular/material/tooltip';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

import { ApiService } from '../api.service';
import { I18nService } from '../i18n/i18n.service';
import { ContentItem, ContentList, ContentType, InstallResult, Settings, UpdateItem, UpdatesResult } from '../models';
import { ConfirmService } from '../shared/confirm.service';
import { ContentPage } from './content-page';

function item(id: string, overrides: Partial<ContentItem> = {}): ContentItem {
  return {
    id,
    fileName: `${id}.jar`,
    directory: false,
    size: 2048,
    modified: 1000,
    location: null,
    name: `Name ${id}`,
    description: null,
    authors: null,
    version: null,
    iconUrl: null,
    localIcon: false,
    pageUrl: null,
    source: 'MODRINTH',
    identified: true,
    ...overrides,
  };
}

function listOf(...items: ContentItem[]): ContentList {
  return { directory: '/mc/mods', directoryExists: true, provider: 'MODRINTH', items, warning: null };
}

function update(id: string, status: UpdateItem['status'], overrides: Partial<UpdateItem> = {}): UpdateItem {
  return { id, status, latestVersion: '2.0', latestFileName: 'new.jar', changelog: 'notes', ...overrides };
}

const updatesOf = (...items: UpdateItem[]): UpdatesResult => ({ items, warning: null });

const SETTINGS = { provider: 'MODRINTH', gameVersion: '1.21', loader: 'fabric' } as Settings;

const failure = (message: string) => throwError(() => new HttpErrorResponse({ status: 500, error: { message } }));

describe('ContentPage', () => {
  let api: {
    list: ReturnType<typeof vi.fn>;
    settings: ReturnType<typeof vi.fn>;
    updates: ReturnType<typeof vi.fn>;
    install: ReturnType<typeof vi.fn>;
    installAll: ReturnType<typeof vi.fn>;
    localIconUrl: ReturnType<typeof vi.fn>;
  };
  let i18n: I18nService;
  let confirm: ReturnType<typeof vi.fn>;

  async function settle(fixture: ComponentFixture<ContentPage>): Promise<void> {
    for (let i = 0; i < 4; i++) {
      await new Promise((resolve) => setTimeout(resolve));
      await fixture.whenStable();
    }
  }

  async function create(type: string = 'mods'): Promise<ComponentFixture<ContentPage>> {
    const fixture = TestBed.createComponent(ContentPage);
    fixture.componentRef.setInput('type', type);
    await settle(fixture);
    return fixture;
  }

  async function switchTo(fixture: ComponentFixture<ContentPage>, type: ContentType): Promise<void> {
    fixture.componentRef.setInput('type', type);
    await settle(fixture);
  }

  const text = (f: ComponentFixture<ContentPage>): string => f.nativeElement.textContent;
  const q = (f: ComponentFixture<ContentPage>, selector: string): HTMLElement => f.nativeElement.querySelector(selector);
  const names = (f: ComponentFixture<ContentPage>): (string | undefined)[] =>
    Array.from<HTMLElement>(f.nativeElement.querySelectorAll('article h3')).map((h) => h.textContent?.trim());
  const refreshButton = (f: ComponentFixture<ContentPage>) => q(f, '.actions .refresh') as HTMLButtonElement;

  function serve(list: ContentList, updates: UpdatesResult = updatesOf()): void {
    api.list.mockReturnValue(of(list));
    api.updates.mockReturnValue(of(updates));
  }

  beforeEach(() => {
    api = {
      list: vi.fn(() => of(listOf(item('a')))),
      settings: vi.fn(() => of(SETTINGS)),
      updates: vi.fn(() => of(updatesOf())),
      install: vi.fn(),
      installAll: vi.fn(),
      localIconUrl: vi.fn((type: string, id: string) => `/icon/${type}/${id}`),
    };
    confirm = vi.fn(async () => true);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: ApiService, useValue: api },
        { provide: ConfirmService, useValue: { ask: confirm } },
      ],
    });
    i18n = TestBed.inject(I18nService);
    i18n.lang.set('en');
  });

  describe('loading', () => {
    it('loads the files, settings and update status', async () => {
      const fixture = await create();

      expect(api.list).toHaveBeenCalledWith('mods', false);
      expect(api.settings).toHaveBeenCalled();
      expect(api.updates).toHaveBeenCalledWith('mods', false);
      expect(q(fixture, '.count').textContent).toBe('1');
      expect(q(fixture, '.path').textContent).toContain('/mc/mods');
      expect(names(fixture)).toEqual(['Name a']);
    });

    it('shows a message while loading', async () => {
      api.list.mockReturnValue(new Subject<ContentList>());
      const fixture = await create();

      expect(text(fixture)).toContain(i18n.t('content.loadingFiles'));
      expect(refreshButton(fixture).disabled).toBe(true);
      expect(refreshButton(fixture).textContent).toContain(i18n.t('content.checking'));
    });

    it('shows the error and skips the update check when loading fails', async () => {
      api.list.mockReturnValue(failure('Cannot list'));
      const fixture = await create();

      expect(q(fixture, '.banner.error').textContent).toBe('Cannot list');
      expect(api.updates).not.toHaveBeenCalled();
      expect(fixture.componentInstance.loading()).toBe(false);
    });

    it('explains a missing folder, linking to settings', async () => {
      serve({ ...listOf(), directoryExists: false });
      const fixture = await create();

      expect(q(fixture, '.path').classList).toContain('missing');
      expect(q(fixture, '.path').textContent).toContain(i18n.t('content.directoryMissing'));
      expect(q(fixture, '.path a').getAttribute('href')).toBe('/settings');
    });

    const types: ContentType[] = ['mods', 'resourcepacks', 'datapacks', 'shaderpacks'];
    for (const type of types) {
      it(`has a title and empty message for ${type}`, async () => {
        serve(listOf());
        const fixture = await create(type);

        expect(q(fixture, 'h1').textContent).toContain(i18n.t(`content.titles.${type}`));
        expect(q(fixture, '.empty').textContent).toContain(i18n.t(`content.empty.${type}`));
      });
    }

    it('treats an unknown type like mods', async () => {
      const fixture = await create('nope');

      expect(fixture.componentInstance.meta().titleKey).toBe('content.titles.mods');
    });
  });

  describe('settings chips', () => {
    it('shows source, version and loader for mods', async () => {
      const fixture = await create();
      const chips = q(fixture, '.chips');

      expect(chips.textContent).toContain(i18n.t('content.source', { provider: 'Modrinth' }));
      expect(chips.querySelector('.badge')?.classList).toContain('modrinth');
      expect(chips.textContent).toContain('Minecraft 1.21');
      expect(chips.textContent).toContain('fabric');
    });

    it('leaves out the loader for other types', async () => {
      const fixture = await create('resourcepacks');

      expect(q(fixture, '.chips').textContent).not.toContain('fabric');
    });

    it('names CurseForge', async () => {
      api.settings.mockReturnValue(of({ ...SETTINGS, provider: 'CURSEFORGE' }));
      const fixture = await create();

      expect(q(fixture, '.chips').textContent).toContain(i18n.t('content.source', { provider: 'CurseForge' }));
      expect(q(fixture, '.chips .badge').classList).toContain('curseforge');
    });
  });

  describe('warnings', () => {
    it('shows a warning from the file list first', async () => {
      serve({ ...listOf(item('a')), warning: 'list warning' }, { items: [], warning: 'update warning' });
      const fixture = await create();

      expect(q(fixture, '.banner.warn').textContent).toContain('list warning');
      expect(text(fixture)).not.toContain('update warning');
    });

    it('shows a warning from the update check', async () => {
      serve(listOf(item('a')), { items: [], warning: 'update warning' });
      const fixture = await create();

      expect(q(fixture, '.banner.warn').textContent).toContain('update warning');
    });

    it('shows a failed update check as a warning', async () => {
      api.updates.mockReturnValue(failure('Modrinth is down'));
      const fixture = await create();

      expect(q(fixture, '.banner.warn').textContent).toContain('Modrinth is down');
      expect(fixture.componentInstance.checking()).toBe(false);
    });
  });

  describe('search and filters', () => {
    const LIST = listOf(
      item('a', { name: 'Sodium', description: 'Makes it fast' }),
      item('b', { name: 'Iris', fileName: 'shaders-loader.jar', identified: false, source: 'LOCAL' }),
      item('c', { name: 'Lithium' }),
    );
    const buttons = (f: ComponentFixture<ContentPage>) =>
      Array.from<HTMLButtonElement>(f.nativeElement.querySelectorAll('.seg button'));

    async function searchFor(fixture: ComponentFixture<ContentPage>, value: string): Promise<void> {
      const input = q(fixture, 'input[type=search]') as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
      await settle(fixture);
    }

    beforeEach(() => serve(LIST, updatesOf(update('a', 'UPDATE_AVAILABLE'), update('c', 'UP_TO_DATE'))));

    it('shows everything by default', async () => {
      const fixture = await create();

      expect(names(fixture)).toEqual(['Sodium', 'Iris', 'Lithium']);
      expect(buttons(fixture)[0].getAttribute('aria-checked')).toBe('true');
    });

    it('searches names, ignoring case and spaces', async () => {
      const fixture = await create();

      await searchFor(fixture, '  SODIUM ');

      expect(names(fixture)).toEqual(['Sodium']);
    });

    it('searches file names and descriptions', async () => {
      const fixture = await create();

      await searchFor(fixture, 'shaders-loader');
      expect(names(fixture)).toEqual(['Iris']);

      await searchFor(fixture, 'fast');
      expect(names(fixture)).toEqual(['Sodium']);
    });

    it('says when nothing matches', async () => {
      const fixture = await create();

      await searchFor(fixture, 'no such mod');

      expect(q(fixture, '.empty').textContent).toContain(i18n.t('content.nothingFound'));
    });

    it('filters to files with updates', async () => {
      const fixture = await create();

      buttons(fixture)[1].click();
      await settle(fixture);

      expect(names(fixture)).toEqual(['Sodium']);
      expect(buttons(fixture)[1].getAttribute('aria-checked')).toBe('true');
      expect(buttons(fixture)[1].querySelector('b')?.textContent).toBe('1');
    });

    it('filters to unrecognized files', async () => {
      const fixture = await create();

      buttons(fixture)[2].click();
      await settle(fixture);

      expect(names(fixture)).toEqual(['Iris']);
      expect(buttons(fixture)[2].querySelector('b')?.textContent).toBe('1');
    });

    it('goes back to showing everything', async () => {
      const fixture = await create();
      buttons(fixture)[2].click();
      await settle(fixture);

      buttons(fixture)[0].click();
      await settle(fixture);

      expect(names(fixture)).toHaveLength(3);
    });

    it('combines the filter and search', async () => {
      const fixture = await create();
      buttons(fixture)[1].click();
      await settle(fixture);

      await searchFor(fixture, 'lithium');

      expect(names(fixture)).toEqual([]);
    });

    it('has no counters when there is nothing to count', async () => {
      serve(listOf(item('a')));
      const fixture = await create();

      expect(buttons(fixture)[1].querySelector('b')).toBeNull();
      expect(buttons(fixture)[2].querySelector('b')).toBeNull();
    });
  });

  describe('switching tabs', () => {
    it('resets the view and loads the new type', async () => {
      serve(listOf(item('a')), updatesOf(update('a', 'UPDATE_AVAILABLE')));
      const fixture = await create();
      const page = fixture.componentInstance;
      page.search.set('x');
      page.filter.set('updates');
      page.iconFailed(item('a', { iconUrl: 'http://i' }));
      serve(listOf(item('s')));

      await switchTo(fixture, 'shaderpacks');

      expect(api.list).toHaveBeenLastCalledWith('shaderpacks', false);
      expect(page.search()).toBe('');
      expect(page.filter()).toBe('all');
      expect(page.iconSrc(item('a', { iconUrl: 'http://i' }))).toBe('http://i');
      expect(page.updates()).toEqual({});
      expect(names(fixture)).toEqual(['Name s']);
    });

    it('ignores a late file list from the previous tab', async () => {
      const old = new Subject<ContentList>();
      api.list.mockReturnValueOnce(old);
      const fixture = await create();
      serve(listOf(item('new')));
      await switchTo(fixture, 'shaderpacks');

      old.next(listOf(item('old')));
      old.complete();
      await settle(fixture);

      expect(names(fixture)).toEqual(['Name new']);
      expect(api.updates).toHaveBeenCalledTimes(1);
    });

    it('ignores a late error from the previous tab', async () => {
      const old = new Subject<ContentList>();
      api.list.mockReturnValueOnce(old);
      const fixture = await create();
      await switchTo(fixture, 'shaderpacks');

      old.error(new Error('late'));
      await settle(fixture);

      expect(fixture.componentInstance.error()).toBeNull();
    });

    it('ignores a late update check from the previous tab', async () => {
      const old = new Subject<UpdatesResult>();
      api.updates.mockReturnValueOnce(old);
      const fixture = await create();
      await switchTo(fixture, 'shaderpacks');

      old.next(updatesOf(update('a', 'UPDATE_AVAILABLE')));
      old.complete();
      await settle(fixture);

      expect(fixture.componentInstance.updates()).toEqual({});
    });

    it('ignores a late failed update check from the previous tab', async () => {
      const old = new Subject<UpdatesResult>();
      api.updates.mockReturnValueOnce(old);
      const fixture = await create();
      await switchTo(fixture, 'shaderpacks');

      old.error(new Error('late'));
      await settle(fixture);

      expect(fixture.componentInstance.updatesWarning()).toBeNull();
    });

    // Known bug: the old check leaves `checking` for the new tab to clear, which it never does if
    // its list fails to load. The refresh button then spins forever. Remove `.fails` once fixed.
    it.fails('does not stay stuck checking when the new tab fails to load', async () => {
      api.updates.mockReturnValueOnce(new Subject<UpdatesResult>());
      const fixture = await create();
      api.list.mockReturnValue(failure('Cannot list'));

      await switchTo(fixture, 'shaderpacks');

      expect(fixture.componentInstance.checking()).toBe(false);
    });
  });

  describe('refresh', () => {
    it('asks the source again, keeping the list and any message on screen', async () => {
      const fixture = await create();
      const page = fixture.componentInstance;
      page.notice.set('kept');
      api.list.mockClear();
      const pending = new Subject<ContentList>();
      api.list.mockReturnValue(pending);

      refreshButton(fixture).click();
      await settle(fixture);

      expect(api.list).toHaveBeenCalledWith('mods', true);
      expect(names(fixture)).toEqual(['Name a']);
      expect(page.notice()).toBe('kept');

      pending.next(listOf(item('a')));
      pending.complete();
      await settle(fixture);

      expect(api.updates).toHaveBeenLastCalledWith('mods', true);
      expect(refreshButton(fixture).disabled).toBe(false);
      expect(refreshButton(fixture).textContent).toContain(i18n.t('content.checkUpdates'));
    });
  });

  describe('install', () => {
    const A = item('a');
    const OK: InstallResult = { id: 'a', ok: true, message: 'Updated to 2.0', newFileName: 'new.jar' };

    beforeEach(() => serve(listOf(A), updatesOf(update('a', 'UPDATE_AVAILABLE'))));

    it('starts from the update button and reloads afterwards', async () => {
      api.install.mockReturnValue(of(OK));
      const fixture = await create();
      api.list.mockClear();

      (q(fixture, '.side .update') as HTMLButtonElement).click();
      await settle(fixture);

      expect(api.install).toHaveBeenCalledWith('mods', 'a');
      expect(api.list).toHaveBeenCalledWith('mods', false);
    });

    it('marks the file as busy and blocks other actions meanwhile', async () => {
      const pending = new Subject<InstallResult>();
      api.install.mockReturnValue(pending);
      const fixture = await create();
      const page = fixture.componentInstance;

      void page.install(A);
      await settle(fixture);

      expect(page.busy().has('a')).toBe(true);
      expect(q(fixture, '.side').textContent).toContain(i18n.t('content.updating'));
      expect((q(fixture, '.actions .update-all') as HTMLButtonElement).disabled).toBe(true);

      pending.next(OK);
      pending.complete();
      await settle(fixture);

      expect(page.busy().size).toBe(0);
    });

    // Known bug: both messages are set, then load(type, false) clears them, so the user never
    // sees them. Remove `.fails` once fixed.
    it.fails('tells the user the update worked', async () => {
      api.install.mockReturnValue(of(OK));
      const fixture = await create();

      await fixture.componentInstance.install(A);

      expect(fixture.componentInstance.notice()).toBe(
        i18n.t('content.installNotice', { name: A.name, message: OK.message }),
      );
    });

    it.fails('keeps showing why an update failed', async () => {
      api.install.mockReturnValue(failure('Checksum mismatch'));
      const fixture = await create();

      await fixture.componentInstance.install(A);

      expect(fixture.componentInstance.error()).toBe(
        i18n.t('content.installError', { name: A.name, message: 'Checksum mismatch' }),
      );
    });

    it('reloads and frees the file even when the update fails', async () => {
      api.install.mockReturnValue(failure('Checksum mismatch'));
      const fixture = await create();
      api.list.mockClear();

      await fixture.componentInstance.install(A);

      expect(fixture.componentInstance.busy().size).toBe(0);
      expect(api.list).toHaveBeenCalledWith('mods', false);
    });

    it('sets the message before reloading', async () => {
      const page = (await create()).componentInstance;
      const seen: (string | null)[] = [];
      api.install.mockReturnValue(of(OK));
      api.list.mockImplementation(() => {
        seen.push(page.notice());
        return of(listOf(A));
      });
      const load = vi.spyOn(page, 'load').mockImplementation(async () => {
        seen.push(page.notice());
      });

      await page.install(A);

      expect(load).toHaveBeenCalled();
      expect(seen[0]).toBe(i18n.t('content.installNotice', { name: A.name, message: OK.message }));
    });

    it('sets the error before reloading', async () => {
      const page = (await create()).componentInstance;
      api.install.mockReturnValue(failure('Broken'));
      let seen: string | null = null;
      vi.spyOn(page, 'load').mockImplementation(async () => {
        seen = page.error();
      });

      await page.install(A);

      expect(seen).toBe(i18n.t('content.installError', { name: A.name, message: 'Broken' }));
    });
  });

  describe('update all', () => {
    const updateAll = (f: ComponentFixture<ContentPage>) => q(f, '.actions .update-all') as HTMLButtonElement;

    beforeEach(() => {
      serve(listOf(item('a'), item('b')), updatesOf(update('a', 'UPDATE_AVAILABLE'), update('b', 'UPDATE_AVAILABLE')));
    });

    it('is only offered when updates exist', async () => {
      serve(listOf(item('a')));
      const fixture = await create();

      expect(updateAll(fixture)).toBeNull();
    });

    it('does nothing when there are no updates', async () => {
      serve(listOf(item('a')));
      const fixture = await create();

      await fixture.componentInstance.installAll();

      expect(confirm).not.toHaveBeenCalled();
      expect(api.installAll).not.toHaveBeenCalled();
    });

    it('asks first and does nothing when declined', async () => {
      confirm.mockResolvedValue(false);
      const fixture = await create();

      updateAll(fixture).click();
      await settle(fixture);

      expect(confirm).toHaveBeenCalledWith(i18n.t('content.confirmUpdateAll', { count: 2 }));
      expect(api.installAll).not.toHaveBeenCalled();
    });

    it('shows progress and reloads when done', async () => {
      const pending = new Subject<InstallResult[]>();
      api.installAll.mockReturnValue(pending);
      const fixture = await create();

      updateAll(fixture).click();
      await settle(fixture);
      expect(updateAll(fixture).disabled).toBe(true);
      expect(updateAll(fixture).textContent).toContain(i18n.t('content.updating'));
      expect(refreshButton(fixture).disabled).toBe(true);

      api.list.mockClear();
      pending.next([]);
      pending.complete();
      await settle(fixture);

      expect(api.installAll).toHaveBeenCalledWith('mods');
      expect(fixture.componentInstance.installingAll()).toBe(false);
      expect(api.list).toHaveBeenCalledWith('mods', false);
    });

    it('counts successes and lists failures before reloading', async () => {
      const page = (await create()).componentInstance;
      api.installAll.mockReturnValue(
        of([
          { id: 'a', ok: true, message: 'Updated', newFileName: 'x' },
          { id: 'b', ok: false, message: 'Broken', newFileName: null },
          { id: 'c', ok: false, message: 'Gone', newFileName: null },
        ]),
      );
      const seen: (string | null)[] = [];
      vi.spyOn(page, 'load').mockImplementation(async () => {
        seen.push(page.notice(), page.error());
      });

      await page.installAll();

      expect(seen).toEqual([i18n.t('content.updatedCount', { ok: 1, total: 3 }), 'b: Broken\nc: Gone']);
    });

    it('has no error when everything worked', async () => {
      const page = (await create()).componentInstance;
      api.installAll.mockReturnValue(of([{ id: 'a', ok: true, message: 'Updated', newFileName: 'x' }]));
      let error: string | null = 'unset';
      vi.spyOn(page, 'load').mockImplementation(async () => {
        error = page.error();
      });

      await page.installAll();

      expect(error).toBeNull();
    });

    it('reports a request that failed entirely before reloading', async () => {
      const page = (await create()).componentInstance;
      api.installAll.mockReturnValue(failure('Server down'));
      let error: string | null = null;
      vi.spyOn(page, 'load').mockImplementation(async () => {
        error = page.error();
      });

      await page.installAll();

      expect(error).toBe('Server down');
      expect(page.installingAll()).toBe(false);
    });

    // Known bug: same as for a single update, the reload wipes the result. Remove `.fails` once fixed.
    it.fails('tells the user how many were updated', async () => {
      api.installAll.mockReturnValue(of([{ id: 'a', ok: true, message: 'Updated', newFileName: 'x' }]));
      const page = (await create()).componentInstance;

      await page.installAll();

      expect(page.notice()).toBe(i18n.t('content.updatedCount', { ok: 1, total: 1 }));
    });
  });

  describe('status badges', () => {
    const side = (f: ComponentFixture<ContentPage>) => q(f, '.side');
    const tooltip = (f: ComponentFixture<ContentPage>, selector: string): string =>
      f.debugElement.query(By.css(selector)).injector.get(MatTooltip).message;

    it('offers an update with its version and changelog', async () => {
      serve(listOf(item('a')), updatesOf(update('a', 'UPDATE_AVAILABLE')));
      const fixture = await create();

      expect(q(fixture, 'article').classList).toContain('has-update');
      const badge = side(fixture).querySelector('.badge.warn') as HTMLElement;
      expect(badge.textContent).toContain(i18n.t('content.updateAvailableArrow', { version: '2.0' }));
      expect(tooltip(fixture, '.side .badge.warn')).toBe('notes');
    });

    it('copes with an update without a changelog', async () => {
      serve(listOf(item('a')), updatesOf(update('a', 'UPDATE_AVAILABLE', { changelog: null })));
      const fixture = await create();

      expect(tooltip(fixture, '.side .badge.warn')).toBe('');
    });

    it('shows up to date', async () => {
      serve(listOf(item('a')), updatesOf(update('a', 'UP_TO_DATE')));
      const fixture = await create();

      expect(side(fixture).querySelector('.badge.ok')?.textContent).toBe(i18n.t('content.upToDate'));
      expect(q(fixture, 'article').classList).not.toContain('has-update');
    });

    it('shows when nothing fits the game version', async () => {
      serve(listOf(item('a')), updatesOf(update('a', 'NO_COMPATIBLE')));
      const fixture = await create();

      expect(side(fixture).textContent).toContain(i18n.t('content.noCompatible', { version: '1.21' }));
    });

    it('explains folders and unrecognized files with an unknown status', async () => {
      serve(
        listOf(item('dir', { directory: true }), item('jar', { identified: false }), item('known')),
        updatesOf(update('dir', 'UNKNOWN'), update('jar', 'UNKNOWN'), update('known', 'UNKNOWN')),
      );
      const fixture = await create();
      const sides = Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll('.side'));

      expect(sides[0].textContent).toContain(i18n.t('content.directoryBadge'));
      expect(sides[1].textContent).toContain(i18n.t('content.unrecognizedBadge'));
      expect(sides[2].querySelector('.badge.muted')).toBeNull();
    });

    it('shows progress for a recognized file while checking', async () => {
      api.updates.mockReturnValue(new Subject<UpdatesResult>());
      const fixture = await create();

      expect(side(fixture).textContent).toContain(i18n.t('content.checkingBadge'));
    });

    it('explains files without a status', async () => {
      serve(listOf(item('dir', { directory: true, identified: false }), item('jar', { identified: false }), item('known')));
      const fixture = await create();
      const sides = Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll('.side'));

      expect(sides[0].textContent).toContain(i18n.t('content.directoryBadge'));
      expect(sides[1].textContent).toContain(i18n.t('content.unrecognizedBadge'));
      expect(sides[2].querySelector('.badge.muted')).toBeNull();
    });

    it('statusOf finds the status of a file', async () => {
      const status = update('a', 'UP_TO_DATE');
      serve(listOf(item('a')), updatesOf(status));
      const page = (await create()).componentInstance;

      expect(page.statusOf('a')).toEqual(status);
      expect(page.statusOf('other')).toBeUndefined();
    });
  });

  describe('item details', () => {
    it('shows everything known about a file', async () => {
      serve(
        listOf(
          item('a', {
            pageUrl: 'http://page/',
            version: '1.2',
            location: 'My World',
            description: 'Does things',
            authors: 'Alice',
          }),
        ),
      );
      const fixture = await create();
      const article = q(fixture, 'article');

      expect(article.querySelector('h3 a')?.getAttribute('href')).toBe('http://page/');
      expect(article.querySelector('.version')?.textContent).toBe('1.2');
      expect(article.querySelector('.badge.info')?.textContent).toContain('My World');
      expect(article.querySelector('.desc')?.textContent).toBe('Does things');
      expect(article.querySelector('.meta')?.textContent).toContain('Alice');
      expect(article.querySelector('.meta')?.textContent).toContain('a.jar');
      expect(article.querySelector('.meta')?.textContent).toContain(`2.0 ${i18n.t('format.units.kb')}`);
    });

    it('leaves out what is unknown', async () => {
      const fixture = await create();
      const article = q(fixture, 'article');

      expect(article.querySelector('h3 a')).toBeNull();
      expect(article.querySelector('.version')).toBeNull();
      expect(article.querySelector('.desc')).toBeNull();
    });

    it('describes folders instead of giving a size', async () => {
      serve(listOf(item('a', { directory: true })));
      const fixture = await create();

      expect(q(fixture, '.meta').textContent).toContain(i18n.t('content.folderUnit'));
    });

    it('labels every source', async () => {
      const page = (await create()).componentInstance;

      expect(page.sourceLabel(item('a', { source: 'MODRINTH' }))).toBe(i18n.t('content.sourceModrinth'));
      expect(page.sourceLabel(item('a', { source: 'CURSEFORGE' }))).toBe(i18n.t('content.sourceCurseforge'));
      expect(page.sourceLabel(item('a', { source: 'LOCAL' }))).toBe(i18n.t('content.sourceLocal'));
    });

    it('colours the source badge', async () => {
      serve(listOf(item('a', { source: 'CURSEFORGE' })));
      const fixture = await create();

      expect(q(fixture, '.side .badge').classList).toContain('curseforge');
    });
  });

  describe('icons', () => {
    const BOTH = item('a', { iconUrl: 'http://remote', localIcon: true });

    it('tries the remote icon, then the one in the file, then a placeholder', async () => {
      const page = (await create()).componentInstance;

      expect(page.iconSrc(BOTH)).toBe('http://remote');

      page.iconFailed(BOTH);
      expect(page.iconSrc(BOTH)).toBe('/icon/mods/a');

      page.iconFailed(BOTH);
      expect(page.iconSrc(BOTH)).toBeNull();
    });

    it('has no icon when none is known', async () => {
      const page = (await create()).componentInstance;

      expect(page.iconSrc(item('a'))).toBeNull();
    });

    it('falls back when an image fails to load on the page', async () => {
      serve(listOf(BOTH));
      const fixture = await create();
      expect(q(fixture, 'article img').getAttribute('src')).toBe('http://remote');

      q(fixture, 'article img').dispatchEvent(new Event('error'));
      await settle(fixture);
      expect(q(fixture, 'article img').getAttribute('src')).toBe('/icon/mods/a');

      q(fixture, 'article img').dispatchEvent(new Event('error'));
      await settle(fixture);
      expect(q(fixture, 'article img')).toBeNull();
      expect(q(fixture, 'article .ph').textContent).toBe('N');
    });

    it('makes a placeholder from the first letter of the name', async () => {
      const page = (await create()).componentInstance;

      expect(page.initial(item('a', { name: '  sodium' }))).toBe('S');
      expect(page.initial(item('a', { name: '   ' }))).toBe('?');
    });
  });
});
