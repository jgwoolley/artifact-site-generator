<#macro page title rootPath pageHeading pageDescription pageIcon="" bodyClass="" hideHeading=false>
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${title?html}</title>
  <meta name="description" content="${pageDescription?html}">
  <#if pageKeywords?? && pageKeywords?has_content>
  <meta name="keywords" content="${pageKeywords?html}">
  </#if>
  <meta name="robots" content="index, follow">
  <#assign socialImage = pageIcon!"">
  <#if !socialImage?has_content><#assign socialImage = "/" + faviconPath></#if>
  <meta property="og:type" content="website">
  <meta property="og:title" content="${pageHeading?html}">
  <meta property="og:description" content="${pageDescription?html}">
  <meta property="og:image" content="${rootPath}${socialImage?remove_beginning('/')}">
  <meta name="twitter:card" content="summary">
  <meta name="twitter:title" content="${pageHeading?html}">
  <meta name="twitter:description" content="${pageDescription?html}">
  <meta name="twitter:image" content="${rootPath}${socialImage?remove_beginning('/')}">
  <link rel="icon" type="${faviconMimeType}" href="${rootPath}${faviconPath}">
  <link rel="apple-touch-icon" href="${rootPath}${faviconPath}">
  <link rel="manifest" href="${rootPath}manifest.webmanifest">
  <meta name="theme-color" content="#2563eb">
  <link rel="stylesheet" href="${rootPath}assets/styles.css">
</head>
<body class="page<#if bodyClass?has_content> ${bodyClass}</#if>" data-root-path="${rootPath}">
  <#if bannerText?has_content>
    <div
      class="site-banner"
      style="--banner-text-color-dark:${bannerTextColorDark?html};--banner-background-color-dark:${bannerBackgroundColorDark?html};--banner-text-color-light:${bannerTextColorLight?html};--banner-background-color-light:${bannerBackgroundColorLight?html};"
    >${bannerText?html}</div>
  </#if>
  <header class="top-nav">
    <a class="brand" href="${rootPath}index.html">
      <img src="${rootPath}${faviconPath}" alt="Artifact Site" width="28" height="28">
      <span>Artifact Registry</span>
    </a>
    <div class="search-wrap">
      <input
        id="site-search"
        class="search-input"
        type="search"
        placeholder="Search artifacts, tags, parser..."
        aria-label="Search artifacts"
        autocomplete="off"
        role="combobox"
        aria-expanded="false"
        aria-controls="search-results"
        aria-autocomplete="list">
      <div id="search-results" class="search-results" role="listbox" hidden></div>
    </div>
    <a class="nav-icon-link" href="${rootPath}build-info.html" aria-label="Build Info" title="Build Info">
      <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">
        <circle cx="12" cy="12" r="10"></circle>
        <line x1="12" y1="16" x2="12" y2="12"></line>
        <circle cx="12" cy="8" r="1" fill="currentColor" stroke="none"></circle>
      </svg>
    </a>
  </header>
  <main class="container">
    <#if !hideHeading>
      <div class="page-heading">
        <#if pageIcon?has_content><img class="page-icon" src="${rootPath}${pageIcon?remove_beginning('/')}" alt="" width="56" height="56"></#if>
        <h1>${pageHeading?html}</h1>
      </div>
      <#if pageDescription?has_content>
        <p class="lead">${pageDescription?html}</p>
      </#if>
    </#if>
    <#nested>
  </main>
  <script src="${rootPath}assets/app.js"></script>
</body>
</html>
</#macro>
