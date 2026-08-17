<#macro page title rootPath pageHeading pageDescription>
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${title?html}</title>
  <link rel="icon" type="image/svg+xml" href="${rootPath}assets/logo.svg">
  <link rel="stylesheet" href="${rootPath}assets/styles.css">
</head>
<body data-root-path="${rootPath}">
  <header class="top-nav">
    <a class="brand" href="${rootPath}index.html">
      <img src="${rootPath}assets/logo.svg" alt="Artifact Site" width="28" height="28">
      <span>Artifact Registry</span>
    </a>
    <input id="site-search" class="search-input" type="search" placeholder="Search artifacts, tags, parser..." aria-label="Search artifacts">
  </header>
  <main class="container">
    <h1>${pageHeading?html}</h1>
    <#if pageDescription?has_content>
      <p class="lead">${pageDescription?html}</p>
    </#if>
    <#nested>
  </main>
  <script src="${rootPath}assets/app.js"></script>
</body>
</html>
</#macro>
