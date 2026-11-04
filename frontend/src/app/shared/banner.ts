import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MatIcon } from '@angular/material/icon';

import { AppIcon } from '../icons';

export type BannerKind = 'ok' | 'warn' | 'error';

const ICONS: Record<BannerKind, AppIcon> = { ok: 'check_circle', warn: 'warning', error: 'error' };

/** A message that belongs to the page and stays until it no longer applies. Passing messages use the snack bar. */
@Component({
  selector: 'app-banner',
  imports: [MatIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-icon [svgIcon]="icon()" aria-hidden="true" />
    <div class="text"><ng-content /></div>
  `,
  styles: `
    :host {
      display: flex;
      align-items: flex-start;
      gap: 12px;
      margin-bottom: 16px;
      padding: 12px 16px;
      border-radius: var(--mat-sys-corner-medium);
      background: var(--tint);
      color: var(--mat-sys-on-surface);

      --tint: var(--mat-sys-surface-container-high);
      --accent: var(--mat-sys-on-surface-variant);
    }
    :host(.ok) {
      --tint: color-mix(in srgb, var(--app-success) 14%, transparent);
      --accent: var(--app-success);
    }
    :host(.warn) {
      --tint: color-mix(in srgb, var(--app-warning) 16%, transparent);
      --accent: var(--app-warning);
    }
    :host(.error) {
      --tint: var(--mat-sys-error-container);
      --accent: var(--mat-sys-on-error-container);
      color: var(--mat-sys-on-error-container);
    }
    mat-icon {
      flex: none;
      color: var(--accent);
    }
    .text {
      align-self: center;
      min-width: 0;
      overflow-wrap: anywhere;
      white-space: pre-line;
    }
  `,
  host: {
    class: 'banner',
    '[class.ok]': "kind() === 'ok'",
    '[class.warn]': "kind() === 'warn'",
    '[class.error]': "kind() === 'error'",
    '[attr.role]': "kind() === 'error' ? 'alert' : 'status'",
  },
})
export class Banner {
  readonly kind = input.required<BannerKind>();
  readonly icon = computed(() => ICONS[this.kind()]);
}
