import { Routes } from '@angular/router';

import { ContentPage } from './content/content-page';
import { SettingsPage } from './settings/settings-page';
import { WorldsPage } from './worlds/worlds-page';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'c/mods' },
  // :type = mods | resourcepacks | datapacks | shaderpacks (passed into ContentPage via input)
  { path: 'c/:type', component: ContentPage },
  { path: 'worlds', component: WorldsPage },
  { path: 'settings', component: SettingsPage },
  { path: '**', redirectTo: 'c/mods' },
];
