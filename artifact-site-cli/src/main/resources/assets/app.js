// Registers the site's service worker (see sw.js) for basic PWA offline support. Scoped to the
// site root (not just assets/) via data-root-path (set on <body> by layout.ftl), so it works the
// same whether the site is hosted at a domain root or a subpath (e.g. a GitHub Pages project site).
(() => {
  if (!('serviceWorker' in navigator)) {
    return;
  }
  const rootPath = document.body.dataset.rootPath || '';
  navigator.serviceWorker.register(`${rootPath}sw.js`, { scope: rootPath || './' }).catch(() => {
    // Best effort: the site still works fully online without a registered service worker.
  });
})();

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

// Renders UTC timestamps (see build-info.ftl's "Built At") in the viewer's local time zone.
// The manifest/UTC value is kept in the `datetime` attribute for accessibility and copy-paste.
(() => {
  document.querySelectorAll('time[data-utc-timestamp]').forEach((el) => {
    const raw = el.dataset.utcTimestamp;
    const parsed = raw ? new Date(raw) : null;
    if (!parsed || Number.isNaN(parsed.getTime())) {
      return;
    }
    el.dateTime = raw;
    el.textContent = parsed.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'long' });
  });
})();

// "How to Install" popup (see artifact.ftl) and copy-to-clipboard for its code blocks.
(() => {
  document.querySelectorAll('[data-dialog-open]').forEach((trigger) => {
    trigger.addEventListener('click', () => {
      const dialog = document.getElementById(trigger.dataset.dialogOpen);
      if (dialog && typeof dialog.showModal === 'function') {
        dialog.showModal();
      }
    });
  });

  document.querySelectorAll('dialog').forEach((dialog) => {
    dialog.querySelectorAll('[data-dialog-close]').forEach((closeButton) => {
      closeButton.addEventListener('click', () => dialog.close());
    });

    // A click landing on the <dialog> element itself (rather than its inner
    // .modal-content) is a click on the backdrop area, so close on it.
    dialog.addEventListener('click', (event) => {
      if (event.target === dialog) {
        dialog.close();
      }
    });
  });

  const fallbackCopy = (text) => {
    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    textarea.select();
    try {
      document.execCommand('copy');
    } finally {
      textarea.remove();
    }
  };

  document.querySelectorAll('.install-guide pre').forEach((pre) => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'copy-button';
    button.textContent = 'Copy';
    pre.appendChild(button);

    button.addEventListener('click', async () => {
      const code = pre.querySelector('code');
      const text = (code || pre).textContent;
      try {
        if (navigator.clipboard && window.isSecureContext) {
          await navigator.clipboard.writeText(text);
        } else {
          fallbackCopy(text);
        }
        button.textContent = 'Copied!';
      } catch (error) {
        button.textContent = 'Copy failed';
      }
      setTimeout(() => {
        button.textContent = 'Copy';
      }, 1500);
    });
  });
})();
