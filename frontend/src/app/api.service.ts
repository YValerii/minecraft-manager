import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { I18nService } from './i18n/i18n.service';
import {
  ContentList,
  ContentType,
  InstallResult,
  LauncherStatus,
  PathInfo,
  Settings,
  UpdatesResult,
  WorldDetails,
  WorldEdit,
  WorldSummary,
} from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  // ---- settings
  settings(): Observable<Settings> {
    return this.http.get<Settings>('/api/settings');
  }

  saveSettings(settings: Settings): Observable<Settings> {
    return this.http.put<Settings>('/api/settings', settings);
  }

  paths(): Observable<PathInfo[]> {
    return this.http.get<PathInfo[]>('/api/settings/paths');
  }

  // ---- Minecraft Launcher
  launcherStatus(): Observable<LauncherStatus> {
    return this.http.get<LauncherStatus>('/api/launcher');
  }

  launchMinecraft(): Observable<void> {
    return this.http.post<void>('/api/launcher/launch', {});
  }

  // ---- mods / resource packs / datapacks / shaders
  list(type: ContentType, refresh = false): Observable<ContentList> {
    return this.http.get<ContentList>(`/api/content/${type}`, { params: { refresh } });
  }

  updates(type: ContentType, refresh = false): Observable<UpdatesResult> {
    return this.http.get<UpdatesResult>(`/api/content/${type}/updates`, { params: { refresh } });
  }

  install(type: ContentType, id: string): Observable<InstallResult> {
    return this.http.post<InstallResult>(`/api/content/${type}/install`, { id });
  }

  installAll(type: ContentType): Observable<InstallResult[]> {
    return this.http.post<InstallResult[]>(`/api/content/${type}/install-all`, {});
  }

  localIconUrl(type: ContentType, id: string): string {
    return `/api/content/${type}/icon?id=${encodeURIComponent(id)}`;
  }

  // ---- worlds
  worlds(): Observable<WorldSummary[]> {
    return this.http.get<WorldSummary[]>('/api/worlds');
  }

  world(folder: string): Observable<WorldDetails> {
    return this.http.get<WorldDetails>(`/api/worlds/${encodeURIComponent(folder)}`);
  }

  updateWorld(folder: string, edit: WorldEdit): Observable<WorldDetails> {
    return this.http.patch<WorldDetails>(`/api/worlds/${encodeURIComponent(folder)}`, edit);
  }

  resetWorldIcon(folder: string): Observable<WorldDetails> {
    return this.http.delete<WorldDetails>(`/api/worlds/${encodeURIComponent(folder)}/icon`);
  }

  worldRaw(folder: string): Observable<unknown> {
    return this.http.get<unknown>(`/api/worlds/${encodeURIComponent(folder)}/raw`);
  }

  worldIconUrl(folder: string): string {
    return `/api/worlds/${encodeURIComponent(folder)}/icon`;
  }
}

/** Extracts a readable message from the backend response ({"message": "..."}) or from a network error. */
export function errorMessage(e: unknown, i18n: I18nService): string {
  if (e instanceof HttpErrorResponse) {
    const body = e.error as { message?: string } | string | null;
    if (body && typeof body === 'object' && body.message) {
      return body.message;
    }
    if (e.status === 0) {
      return i18n.t('errors.noConnection');
    }
    return i18n.t('errors.httpError', { status: e.status, statusText: e.statusText || i18n.t('errors.unknownError') });
  }
  return e instanceof Error ? e.message : String(e);
}
