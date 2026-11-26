import { useQuery } from '@tanstack/react-query';
import { resolveFallbackVersion } from '@/lib/version';

export interface ReleaseInfo {
  version: string;
  releaseUrl: string;
  apkUrl: string | null;
}

const FALLBACK_VERSION = resolveFallbackVersion(
  typeof __APP_VERSION__ !== 'undefined' ? __APP_VERSION__ : undefined,
);

export const FALLBACK_RELEASE: ReleaseInfo = {
  version: FALLBACK_VERSION,
  releaseUrl: 'https://github.com/657kbps/quota-widget/releases/latest',
  apkUrl: null,
};

interface GitHubReleaseResponse {
  tag_name?: string;
  html_url?: string;
  assets?: Array<{
    name?: string;
    browser_download_url?: string;
  }>;
}

async function fetchLatestRelease(): Promise<ReleaseInfo> {
  const response = await fetch('https://api.github.com/repos/657kbps/quota-widget/releases/latest', {
    headers: {
      Accept: 'application/vnd.github+json',
    },
  });
  if (!response.ok) {
    return FALLBACK_RELEASE;
  }
  const data = (await response.json()) as GitHubReleaseResponse;
  const apkAsset = data.assets?.find((asset) => asset.name?.endsWith('.apk'));
  return {
    version: data.tag_name ?? FALLBACK_RELEASE.version,
    releaseUrl: data.html_url ?? FALLBACK_RELEASE.releaseUrl,
    apkUrl: apkAsset?.browser_download_url ?? null,
  };
}

export function useLatestRelease() {
  const query = useQuery({
    queryKey: ['latest-release'],
    queryFn: fetchLatestRelease,
    placeholderData: FALLBACK_RELEASE,
    staleTime: 1000 * 60 * 30,
    retry: false,
  });

  return {
    ...query,
    data: query.data ?? FALLBACK_RELEASE,
  };
}
