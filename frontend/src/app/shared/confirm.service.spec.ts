import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';

import { I18nService } from '../i18n/i18n.service';
import { ConfirmService } from './confirm.service';

describe('ConfirmService', () => {
  const settle = () => new Promise((resolve) => setTimeout(resolve));
  const button = (selector: string) => document.querySelector<HTMLButtonElement>(selector);

  /** Wrapped, because awaiting a bare promise here would wait for the answer. */
  async function open(): Promise<{ result: Promise<boolean> }> {
    const result = TestBed.inject(ConfirmService).ask('Really?');
    await settle();
    TestBed.tick();
    await settle();
    return { result };
  }

  beforeEach(() => TestBed.inject(I18nService).lang.set('en'));

  afterEach(() => TestBed.inject(MatDialog).closeAll());

  it('shows the question with labelled buttons', async () => {
    const i18n = TestBed.inject(I18nService);

    await open();

    expect(document.querySelector('.message')?.textContent).toContain('Really?');
    expect(button('button.accept')?.textContent).toContain(i18n.t('common.confirm'));
    expect(button('button.decline')?.textContent).toContain(i18n.t('common.cancel'));
  });

  it('answers yes when accepted', async () => {
    const answer = await open();

    button('button.accept')?.click();

    await expect(answer.result).resolves.toBe(true);
  });

  it('answers no when declined', async () => {
    const answer = await open();

    button('button.decline')?.click();

    await expect(answer.result).resolves.toBe(false);
  });

  it('answers no when dismissed some other way', async () => {
    const answer = await open();

    TestBed.inject(MatDialog).closeAll();

    await expect(answer.result).resolves.toBe(false);
  });
});
