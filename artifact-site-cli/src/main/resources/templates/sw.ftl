// Basic PWA offline support. PRECACHE_URLS lists every file this build produced (see
// GenerateCommand#collectPrecacheUrls, which walks the output directory after every other file
// has been written), so install eagerly caches the whole site up front instead of relying on
// pages being visited first. Runtime fetches stay network-first, falling back to the cache, so
// content already installed keeps working offline while still preferring fresh responses online.
const CACHE_NAME = 'artifact-site-cache-${cacheVersion?js_string}';

const PRECACHE_URLS = [
<#list precacheUrls as url>
  '${url?js_string}'<#if url_has_next>,</#if>
</#list>
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(CACHE_NAME)
      .then((cache) =>
        Promise.all(
          PRECACHE_URLS.map((url) =>
            cache.add(new Request(new URL(url, self.location.href), { cache: 'reload' })).catch(() => {
              // Best effort: one file failing to precache (e.g. a large download) shouldn't block
              // the rest of the site from installing for offline use.
            })
          )
        )
      )
      .then(() => self.skipWaiting())
  );
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
