import { MATERIAL_ANIMATIONS } from '@angular/material/core';

import { provideAppIcons } from './app/icons';

/** What every unit test needs from the real app configuration. */
export default [provideAppIcons(), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }];
