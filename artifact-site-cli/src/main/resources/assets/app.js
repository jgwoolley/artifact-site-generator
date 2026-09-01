(() => {
  const input = document.getElementById('site-search');
  const resultsPanel = document.getElementById('search-results');
  if (!input || !resultsPanel) {
    return;
  }

  const rootPath = document.body.dataset.rootPath || '';
  const MAX_RESULTS = 8;
  let indexEntries = [];
  let activeIndex = -1;

  const stripLeadingSlash = (value) => String(value || '').replace(/^\//, '');
  const entryHref = (entry) => rootPath + stripLeadingSlash(entry.url);

  const matchEntries = (query) => {
    const needle = query.trim().toLowerCase();
    if (!needle) {
      return [];
    }
    return indexEntries
      .filter((entry) => {
        const tags = (entry.tags || []).join(' ');
        const haystack = [
          entry.name,
          entry.description,
          entry.version,
          entry.parserType,
          entry.groupId,
          entry.artifactId,
          tags,
        ]
          .filter(Boolean)
          .join(' ')
          .toLowerCase();
        return haystack.includes(needle);
      })
      .slice(0, MAX_RESULTS);
  };

  const openResults = () => {
    resultsPanel.hidden = false;
    input.setAttribute('aria-expanded', 'true');
  };

  const closeResults = () => {
    resultsPanel.hidden = true;
    resultsPanel.innerHTML = '';
    input.setAttribute('aria-expanded', 'false');
    input.removeAttribute('aria-activedescendant');
    activeIndex = -1;
  };

  const setActive = (index) => {
    const options = Array.from(resultsPanel.querySelectorAll('.search-result'));
    options.forEach((option) => option.classList.remove('active'));

    if (index >= 0 && index < options.length) {
      options[index].classList.add('active');
      options[index].scrollIntoView({ block: 'nearest' });
      input.setAttribute('aria-activedescendant', options[index].id);
    } else {
      input.removeAttribute('aria-activedescendant');
    }
    activeIndex = index;
  };

  const renderResults = (query, matches) => {
    resultsPanel.innerHTML = '';
    activeIndex = -1;

    if (!matches.length) {
      const empty = document.createElement('div');
      empty.className = 'search-empty';
      empty.textContent = `No matches for "${query.trim()}".`;
      resultsPanel.appendChild(empty);
      openResults();
      return;
    }

    matches.forEach((entry, i) => {
      const link = document.createElement('a');
      link.className = 'search-result';
      link.href = entryHref(entry);
      link.id = `search-result-${i}`;
      link.setAttribute('role', 'option');

      if (entry.icon) {
        const icon = document.createElement('img');
        icon.className = 'search-result-icon';
        icon.src = rootPath + stripLeadingSlash(entry.icon);
        icon.alt = '';
        link.appendChild(icon);
      }

      const body = document.createElement('span');
      body.className = 'search-result-body';

      const name = document.createElement('span');
      name.className = 'search-result-name';
      name.textContent = entry.name || '';
      body.appendChild(name);

      const meta = document.createElement('span');
      meta.className = 'search-result-meta';
      meta.textContent = [
        entry.groupId && entry.artifactId ? `${entry.groupId}.${entry.artifactId}` : null,
        entry.version,
        entry.parserType,
      ]
        .filter(Boolean)
        .join(' • ');
      body.appendChild(meta);

      link.appendChild(body);
      resultsPanel.appendChild(link);
    });

    openResults();
  };

  const onQueryChanged = () => {
    const query = input.value;
    if (!query.trim()) {
      closeResults();
      return;
    }
    renderResults(query, matchEntries(query));
  };

  fetch(`${rootPath}search-index.json`)
    .then((response) => (response.ok ? response.json() : []))
    .then((data) => {
      indexEntries = Array.isArray(data) ? data : [];

      input.addEventListener('input', onQueryChanged);

      input.addEventListener('focus', () => {
        if (input.value.trim()) {
          renderResults(input.value, matchEntries(input.value));
        }
      });

      input.addEventListener('keydown', (event) => {
        const options = resultsPanel.querySelectorAll('.search-result');
        if (event.key === 'ArrowDown' && options.length) {
          event.preventDefault();
          setActive((activeIndex + 1) % options.length);
        } else if (event.key === 'ArrowUp' && options.length) {
          event.preventDefault();
          setActive((activeIndex - 1 + options.length) % options.length);
        } else if (event.key === 'Enter') {
          if (activeIndex >= 0 && options[activeIndex]) {
            event.preventDefault();
            options[activeIndex].click();
          }
        } else if (event.key === 'Escape') {
          closeResults();
          input.blur();
        }
      });

      document.addEventListener('click', (event) => {
        if (!event.target.closest('.search-wrap')) {
          closeResults();
        }
      });
    })
    .catch(() => {
      // Best effort: search stays inert if the index cannot be fetched.
    });
})();
