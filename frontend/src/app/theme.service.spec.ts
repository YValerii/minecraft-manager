import { TestBed } from '@angular/core/testing';

import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  const root = document.documentElement;
  let setItem: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    setItem = vi.fn();
    vi.stubGlobal('localStorage', { setItem });
    delete root.dataset['theme'];
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    delete root.dataset['theme'];
  });

  it('starts with the theme chosen by index.html', () => {
    root.dataset['theme'] = 'light';
    expect(TestBed.inject(ThemeService).theme()).toBe('light');
  });

  it('is dark by default', () => {
    expect(TestBed.inject(ThemeService).theme()).toBe('dark');
  });

  it('toggles, updating the page and remembering the choice', () => {
    const service = TestBed.inject(ThemeService);

    service.toggle();
    expect(service.theme()).toBe('light');
    expect(root.dataset['theme']).toBe('light');
    expect(setItem).toHaveBeenLastCalledWith('mcmanager.theme', 'light');

    service.toggle();
    expect(service.theme()).toBe('dark');
    expect(root.dataset['theme']).toBe('dark');
    expect(setItem).toHaveBeenLastCalledWith('mcmanager.theme', 'dark');
  });

  it('still toggles when storage is unavailable', () => {
    setItem.mockImplementation(() => {
      throw new Error('denied');
    });
    const service = TestBed.inject(ThemeService);

    service.toggle();

    expect(service.theme()).toBe('light');
  });
});
