<#macro page title rootPath pageHeading pageDescription>
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${title?html}</title>
  <link rel="stylesheet" href="${rootPath}assets/styles.css">
</head>
<body data-root-path="${rootPath}">
  <#if bannerText?has_content>
    <div
      class="site-banner"
      style="--banner-text-color-dark:${bannerTextColorDark?html};--banner-background-color-dark:${bannerBackgroundColorDark?html};--banner-text-color-light:${bannerTextColorLight?html};--banner-background-color-light:${bannerBackgroundColorLight?html};"
    >${bannerText?html}</div>
  </#if>
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
