import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { NEVER } from 'rxjs';

import { ApiService } from './api.service';
import { appConfig } from './app.config';
import { ContentPage } from './content/content-page';
import { SettingsPage } from './settings/settings-page';
import { WorldsPage } from './worlds/worlds-page';

/** Uses the real application config, so this also proves the routes and input binding are wired up. */
describe('routes', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...appConfig.providers,
        {
          provide: ApiService,
          useValue: { list: () => NEVER, settings: () => NEVER, paths: () => NEVER, worlds: () => NEVER },
        },
      ],
    });
  });

  it('opens mods by default', async () => {
    const harness = await RouterTestingHarness.create('/');

    expect(TestBed.inject(Router).url).toBe('/c/mods');
    expect(harness.routeDebugElement?.componentInstance).toBeInstanceOf(ContentPage);
  });

  it('sends unknown addresses to mods', async () => {
    await RouterTestingHarness.create('/does/not/exist');

    expect(TestBed.inject(Router).url).toBe('/c/mods');
  });

  it('passes the content type from the address to the page', async () => {
    const harness = await RouterTestingHarness.create('/c/shaderpacks');

    expect((harness.routeDebugElement?.componentInstance as ContentPage).type()).toBe('shaderpacks');
  });

  it('opens worlds and settings', async () => {
    const harness = await RouterTestingHarness.create('/worlds');
    expect(harness.routeDebugElement?.componentInstance).toBeInstanceOf(WorldsPage);

    await harness.navigateByUrl('/settings');
    expect(harness.routeDebugElement?.componentInstance).toBeInstanceOf(SettingsPage);
  });
});
