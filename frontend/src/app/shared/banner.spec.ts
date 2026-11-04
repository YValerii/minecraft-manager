import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { Banner } from './banner';

@Component({
  imports: [Banner],
  template: `<app-banner [kind]="kind()">Message</app-banner>`,
})
class Host {
  readonly kind = signal<'ok' | 'warn' | 'error'>('ok');
}

describe('Banner', () => {
  async function create(kind: 'ok' | 'warn' | 'error') {
    const fixture = TestBed.createComponent(Host);
    fixture.componentInstance.kind.set(kind);
    await fixture.whenStable();
    return fixture.nativeElement.querySelector('app-banner') as HTMLElement;
  }

  it('shows its message with an icon', async () => {
    const banner = await create('ok');

    expect(banner.textContent).toBe('Message');
    expect(banner.querySelector('mat-icon')).not.toBeNull();
  });

  it('announces errors right away and other messages politely', async () => {
    expect((await create('error')).getAttribute('role')).toBe('alert');
    expect((await create('warn')).getAttribute('role')).toBe('status');
    expect((await create('ok')).getAttribute('role')).toBe('status');
  });

  it('is styled by its kind', async () => {
    expect((await create('error')).classList).toContain('error');
    expect((await create('warn')).classList).toContain('warn');
    expect((await create('ok')).classList).toContain('ok');
  });
});
