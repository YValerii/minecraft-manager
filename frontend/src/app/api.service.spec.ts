import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ApiService, errorMessage } from './api.service';
import { I18nService } from './i18n/i18n.service';
import { Settings } from './models';

describe('ApiService', () => {
  let api: ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function expectCall(method: string, url: string, body?: unknown): void {
    const req = http.expectOne((r) => r.urlWithParams === url);
    expect(req.request.method).toBe(method);
    if (body !== undefined) {
      expect(req.request.body).toEqual(body);
    }
    req.flush({});
  }

  it('reads settings and paths', () => {
    api.settings().subscribe();
    expectCall('GET', '/api/settings');
    api.paths().subscribe();
    expectCall('GET', '/api/settings/paths');
  });

  it('saves settings', () => {
    const settings = { gameVersion: '1.21' } as Settings;
    api.saveSettings(settings).subscribe();
    expectCall('PUT', '/api/settings', settings);
  });

  it('talks to the launcher', () => {
    api.launcherStatus().subscribe();
    expectCall('GET', '/api/launcher');
    api.launchMinecraft().subscribe();
    expectCall('POST', '/api/launcher/launch', {});
  });

  it('lists content, not refreshing by default', () => {
    api.list('mods').subscribe();
    expectCall('GET', '/api/content/mods?refresh=false');
    api.list('datapacks', true).subscribe();
    expectCall('GET', '/api/content/datapacks?refresh=true');
  });

  it('checks for updates, not refreshing by default', () => {
    api.updates('shaderpacks').subscribe();
    expectCall('GET', '/api/content/shaderpacks/updates?refresh=false');
    api.updates('mods', true).subscribe();
    expectCall('GET', '/api/content/mods/updates?refresh=true');
  });

  it('installs one or all files', () => {
    api.install('mods', 'mods/a.jar').subscribe();
    expectCall('POST', '/api/content/mods/install', { id: 'mods/a.jar' });
    api.installAll('resourcepacks').subscribe();
    expectCall('POST', '/api/content/resourcepacks/install-all', {});
  });

  it('reads worlds, encoding folder names', () => {
    api.worlds().subscribe();
    expectCall('GET', '/api/worlds');
    api.world('My World').subscribe();
    expectCall('GET', '/api/worlds/My%20World');
    api.worldRaw('a/b').subscribe();
    expectCall('GET', '/api/worlds/a%2Fb/raw');
  });

  it('builds icon URLs', () => {
    expect(api.localIconUrl('mods', 'mods/a b.jar')).toBe('/api/content/mods/icon?id=mods%2Fa%20b.jar');
    expect(api.worldIconUrl('My World')).toBe('/api/worlds/My%20World/icon');
  });
});

describe('errorMessage', () => {
  let i18n: I18nService;

  beforeEach(() => {
    i18n = TestBed.inject(I18nService);
    i18n.lang.set('en');
  });

  it('prefers the message sent by the backend', () => {
    const e = new HttpErrorResponse({ status: 400, error: { message: 'Bad version' } });
    expect(errorMessage(e, i18n)).toBe('Bad version');
  });

  it('reports a lost connection', () => {
    expect(errorMessage(new HttpErrorResponse({ status: 0 }), i18n)).toBe(i18n.t('errors.noConnection'));
  });

  it('falls back to the status when the body has no message', () => {
    for (const error of [null, 'plain text', {}]) {
      const e = new HttpErrorResponse({ status: 500, statusText: 'Server Error', error });
      expect(errorMessage(e, i18n)).toBe(i18n.t('errors.httpError', { status: 500, statusText: 'Server Error' }));
    }
  });

  it('copes with an empty status text', () => {
    const e = Object.assign(new HttpErrorResponse({ status: 500 }), { statusText: '' });
    expect(errorMessage(e, i18n)).toBe(
      i18n.t('errors.httpError', { status: 500, statusText: i18n.t('errors.unknownError') }),
    );
  });

  it('handles errors that did not come from HTTP', () => {
    expect(errorMessage(new Error('boom'), i18n)).toBe('boom');
    expect(errorMessage('just text', i18n)).toBe('just text');
  });
});
