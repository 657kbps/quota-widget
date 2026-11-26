/** Placeholder shown when no release is known and the build defined no `__APP_VERSION__`. */
export const UNKNOWN_VERSION = 'v0.0.0';

/**
 * The Vite build injects `__APP_VERSION__` from `androidApp/build.gradle.kts`; any other
 * runtime (unit tests, plain `tsc`) must not invent a released version number.
 */
export function resolveFallbackVersion(appVersion: string | undefined): string {
  const trimmed = appVersion?.trim() ?? '';
  return trimmed.length > 0 ? trimmed : UNKNOWN_VERSION;
}

/**
 * Reads the shipped `versionName` from `androidApp/build.gradle.kts`. The declaration carries
 * other string literals (`findProperty("versionName")`), so the last quoted value wins.
 */
export function parseGradleVersionName(gradleContents: string): string | undefined {
  const declaration = gradleContents
    .split('\n')
    .find((line) => /^\s*versionName\s*=/.test(line));
  return declaration?.match(/"([^"]+)"/g)?.at(-1)?.replace(/^"|"$/g, '');
}
