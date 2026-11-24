import { createRouter } from '@tanstack/react-router';
import { queryClient } from './query-client';
import { routeTree } from './routeTree.gen';

const basepath = import.meta.env.BASE_URL.replace(/\/+$/, '') || '/';

export const router = createRouter({
  routeTree,
  basepath,
  context: {
    queryClient,
  },
  scrollRestoration: true,
  defaultPreload: 'intent',
  defaultPreloadStaleTime: 0,
});

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router;
  }
}
