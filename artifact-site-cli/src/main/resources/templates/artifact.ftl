<#import "layout.ftl" as layout>
<@layout.page title=title rootPath=rootPath pageHeading=pageHeading pageDescription=description pageIcon=pageIcon!"" bodyClass="artifact-detail-page" hideHeading=true>
  <div class="extension-header">
    <div class="page-heading">
      <#if pageIcon?has_content><img class="page-icon" src="${rootPath}${pageIcon?remove_beginning('/')}" alt="" width="96" height="96"></#if>
      <h1>${pageHeading?html}</h1>
    </div>
    <#if description?has_content>
      <p class="lead">${description?html}</p>
    </#if>

    <div class="extension-subheader">
      <div class="extension-meta">
        <#if authors?size gt 0>
          <p class="extension-publisher">By <#list authors as author>${author?html}<#if author_has_next>, </#if></#list></p>
        </#if>
        <div class="extension-badges">
          <span class="badge">${groupId?html}.${artifactId?html}</span>
          <span class="badge">v${version?html}</span>
          <span class="badge">${parserType?html}</span>
          <#if license?has_content><span class="badge">${license?html}</span></#if>
        </div>
        <#if tags?size gt 0>
          <div class="tags">
            <#list tags as tag><span class="tag">${tag?html}</span></#list>
          </div>
        </#if>
      </div>
      <div class="extension-actions">
        <a class="download-button" href="${downloadUrl?html}">Download<#if fileSizeHumanReadable?has_content> (${fileSizeHumanReadable?html})</#if></a>
        <#if scmUrl?has_content && (scmUrl?starts_with("http://") || scmUrl?starts_with("https://"))>
          <a class="icon-button" href="${scmUrl?html}" title="Repository" aria-label="Repository">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">
              <path d="M9 19c-5 1.5-5-2.5-7-3m14 6v-3.87a3.37 3.37 0 0 0-.94-2.61c3.14-.35 6.44-1.54 6.44-7A5.44 5.44 0 0 0 20 4.77 5.07 5.07 0 0 0 19.91 1S18.73.65 16 2.48a13.38 13.38 0 0 0-7 0C6.27.65 5.09 1 5.09 1A5.07 5.07 0 0 0 5 4.77a5.44 5.44 0 0 0-1.5 3.78c0 5.42 3.3 6.61 6.44 7A3.37 3.37 0 0 0 9 18.13V22"></path>
            </svg>
          </a>
        </#if>
      </div>
    </div>
  </div>

  <div class="extension-layout">
    <article class="extension-main">
      <#if readmeHtml?has_content>
        <div class="readme">${readmeHtml}</div>
      <#else>
        <p class="lead">No README was found for this artifact.</p>
      </#if>
    </article>

    <aside class="extension-sidebar">
      <section class="sidebar-card">
        <h2>Resources</h2>
        <ul class="resource-list">
          <#if scmUrl?has_content>
            <li>
              <#if scmUrl?starts_with("http://") || scmUrl?starts_with("https://")>
                <a href="${scmUrl?html}">Repository</a>
              <#else>
                <span>${scmUrl?html}</span>
              </#if>
            </li>
          </#if>
          <li><a href="${downloadUrl?html}">Download<#if fileSizeHumanReadable?has_content> (${fileSizeHumanReadable?html})</#if></a></li>
          <#if installGuideHtml?has_content>
            <li><button type="button" data-dialog-open="install-guide-dialog">How to Install</button></li>
          </#if>
        </ul>
      </section>

      <section class="sidebar-card">
        <h2>Details</h2>
        <dl class="detail-list">
          <dt>Group Id</dt><dd>${groupId?html}</dd>
          <dt>Artifact Id</dt><dd>${artifactId?html}</dd>
          <dt>Version</dt><dd>${version?html}</dd>
          <#if license?has_content><dt>License</dt><dd>${license?html}</dd></#if>
          <dt>Source Type</dt><dd>${sourceType?html}</dd>
          <#if fileName?has_content><dt>File Name</dt><dd>${fileName?html}</dd></#if>
          <#if fileSizeHumanReadable?has_content><dt>File Size</dt><dd>${fileSizeHumanReadable?html}</dd></#if>
          <#if authors?size gt 0><dt>Authors</dt><dd><#list authors as author>${author?html}<#if author_has_next>, </#if></#list></dd></#if>
          <#if sha256?has_content><dt>SHA-256</dt><dd class="hash">${sha256?html}</dd></#if>
        </dl>
      </section>

      <#if tags?size gt 0>
        <section class="sidebar-card">
          <h2>Tags</h2>
          <div class="tags">
            <#list tags as tag><span class="tag">${tag?html}</span></#list>
          </div>
        </section>
      </#if>
    </aside>
  </div>

  <#if installGuideHtml?has_content>
    <dialog id="install-guide-dialog" class="modal">
      <div class="modal-content">
        <div class="modal-header">
          <h2>How to Install</h2>
          <button type="button" class="modal-close" data-dialog-close aria-label="Close">&times;</button>
        </div>
        <div class="install-guide">${installGuideHtml}</div>
      </div>
    </dialog>
  </#if>
</@layout.page>
