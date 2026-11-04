export type ContentType = 'mods' | 'resourcepacks' | 'datapacks' | 'shaderpacks';

export interface Settings {
  minecraftPath: string;
  gameVersion: string;
  loader: string;
  provider: 'MODRINTH' | 'CURSEFORGE';
  curseforgeApiKey: string | null;
  modsDir: string;
  resourcepacksDir: string;
  shaderpacksDir: string;
  datapacksDir: string;
  savesDir: string;
}

export interface PathInfo {
  key: string;
  label: string;
  path: string;
  exists: boolean;
}

export interface ContentItem {
  id: string;
  fileName: string;
  directory: boolean;
  size: number;
  modified: number;
  location: string | null;
  name: string;
  description: string | null;
  authors: string | null;
  version: string | null;
  iconUrl: string | null;
  localIcon: boolean;
  pageUrl: string | null;
  source: 'MODRINTH' | 'CURSEFORGE' | 'LOCAL';
  identified: boolean;
}

export interface ContentList {
  directory: string;
  directoryExists: boolean;
  provider: string;
  items: ContentItem[];
  warning: string | null;
}

export type UpdateStatus = 'UP_TO_DATE' | 'UPDATE_AVAILABLE' | 'NO_COMPATIBLE' | 'UNKNOWN';

export interface UpdateItem {
  id: string;
  status: UpdateStatus;
  latestVersion: string | null;
  latestFileName: string | null;
  changelog: string | null;
}

export interface UpdatesResult {
  items: UpdateItem[];
  warning: string | null;
}

export interface InstallResult {
  id: string;
  ok: boolean;
  message: string;
  newFileName: string | null;
}

export interface WorldSummary {
  folder: string;
  name: string;
  versionName: string | null;
  lastPlayed: number | null;
  gameMode: string | null;
  hardcore: boolean;
  hasIcon: boolean;
}

export interface WorldDetails {
  summary: WorldSummary;
  path: string;
  sizeBytes: number;
  fileCount: number;
  levelDatModified: number | null;
  dataVersion: number | null;
  versionId: number | null;
  snapshot: boolean | null;
  difficulty: string | null;
  allowCommands: boolean | null;
  seed: string | null;
  spawn: { x: number | null; y: number | null; z: number | null } | null;
  gameTimeTicks: number | null;
  dayTimeTicks: number | null;
  daysPlayed: number | null;
  raining: boolean | null;
  thundering: boolean | null;
  borderSize: number | null;
  serverBrands: string[];
  wasModded: boolean | null;
  enabledDatapacks: string[];
  disabledDatapacks: string[];
  enabledFeatures: string[];
  player: {
    dimension: string | null;
    x: number | null;
    y: number | null;
    z: number | null;
    health: number | null;
    food: number | null;
    xpLevel: number | null;
    gameMode: string | null;
  } | null;
  playerCount: number;
  dimensions: string[];
  gameRules: Record<string, unknown> | null;
}

/** The changes to make to a world; whatever is left out stays as it is. */
export interface WorldEdit {
  name?: string;
  gameMode?: string;
  difficulty?: string;
  hardcore?: boolean;
  allowCommands?: boolean;
  gameRules?: Record<string, string>;
}

export interface LauncherStatus {
  available: boolean;
}
