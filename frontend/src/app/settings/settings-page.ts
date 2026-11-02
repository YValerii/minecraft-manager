import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { firstValueFrom } from 'rxjs';

import { ApiService, errorMessage } from '../api.service';
import { I18nService } from '../i18n/i18n.service';
import { PathInfo, Settings } from '../models';

@Component({
  selector: 'app-settings-page',
  imports: [ReactiveFormsModule],
  templateUrl: './settings-page.html',
  styleUrl: './settings-page.scss',
})
export class SettingsPage {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder).nonNullable;
  readonly i18n = inject(I18nService);

  readonly form = this.fb.group({
    minecraftPath: '',
    gameVersion: '',
    loader: '',
    provider: 'MODRINTH' as 'MODRINTH' | 'CURSEFORGE',
    curseforgeApiKey: '',
    modsDir: '',
    resourcepacksDir: '',
    shaderpacksDir: '',
    datapacksDir: '',
    savesDir: '',
  });

  readonly paths = signal<PathInfo[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly saved = signal(false);

  private readonly formValue = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });

  readonly needsKey = computed(() => {
    const v = this.formValue();
    return v.provider === 'CURSEFORGE' && !(v.curseforgeApiKey ?? '').trim();
  });

  constructor() {
    void this.load();
  }

  private async load(): Promise<void> {
    try {
      const [settings, paths] = await Promise.all([firstValueFrom(this.api.settings()), firstValueFrom(this.api.paths())]);
      this.apply(settings);
      this.paths.set(paths);
    } catch (e) {
      this.error.set(errorMessage(e, this.i18n));
    } finally {
      this.loading.set(false);
    }
  }

  private apply(s: Settings): void {
    this.form.reset({ ...s, curseforgeApiKey: s.curseforgeApiKey ?? '' });
  }

  async save(): Promise<void> {
    this.saving.set(true);
    this.saved.set(false);
    this.error.set(null);
    try {
      const v = this.form.getRawValue();
      const saved = await firstValueFrom(this.api.saveSettings({ ...v, curseforgeApiKey: v.curseforgeApiKey || null }));
      this.apply(saved);
      this.paths.set(await firstValueFrom(this.api.paths()));
      this.saved.set(true);
    } catch (e) {
      this.error.set(errorMessage(e, this.i18n));
    } finally {
      this.saving.set(false);
    }
  }
}
