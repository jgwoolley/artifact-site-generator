(() => {
  const input = document.getElementById('site-search');
  const cardsContainer = document.querySelector('[data-search-target="cards"]');
  if (!input || !cardsContainer) {
    return;
  }

  const cards = Array.from(cardsContainer.querySelectorAll('[data-entry-url]'));
  const rootPath = document.body.dataset.rootPath || '';

  fetch(`${rootPath}search-index.json`)
    .then((response) => (response.ok ? response.json() : []))
    .then((indexEntries) => {
      const onSearch = () => {
        const query = input.value.trim().toLowerCase();
        if (!query) {
          cards.forEach((card) => {
            card.hidden = false;
          });
          return;
        }

        const allowedUrls = new Set(
          indexEntries
            .filter((entry) => {
              const tags = (entry.tags || []).join(' ').toLowerCase();
              const haystack = `${entry.name || ''} ${entry.description || ''} ${entry.version || ''} ${entry.parserType || ''} ${entry.groupId || ''} ${entry.artifactId || ''} ${tags}`.toLowerCase();
              return haystack.includes(query);
            })
            .map((entry) => entry.url)
        );

        cards.forEach((card) => {
          card.hidden = !allowedUrls.has(card.dataset.entryUrl);
        });
      };

      input.addEventListener('input', onSearch);
    })
    .catch(() => {
      // Best effort: leave cards unfiltered if search index cannot be fetched.
    });
})();
