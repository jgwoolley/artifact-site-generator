// Basic PWA offline support: network-first for navigation/assets, falling back to whatever was
// previously cached. Pages are cached lazily as they're visited, since this generated site's page
// list (one per parsed artifact/version) isn't known ahead of time by this generic script.
const CACHE_NAME = 'artifact-site-cache-v1';

self.addEventListener('install', () => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET' || new URL(request.url).origin !== self.location.origin) {
    return;
  }

  event.respondWith(
    caches.open(CACHE_NAME).then((cache) =>
      fetch(request)
        .then((response) => {
          if (response && response.ok) {
            cache.put(request, response.clone());
          }
          return response;
        })
        .catch(() => cache.match(request))
    )
  );
});
