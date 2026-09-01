<#import "layout.ftl" as layout>
<@layout.page title=title rootPath=rootPath pageHeading=pageHeading pageDescription=description pageIcon=pageIcon!"">
  <div class="artifact-actions">
    <a class="download-button" href="${downloadUrl?html}">Download</a>
    <p class="meta">${groupId?html}.${artifactId?html} • version ${version?html} • parser ${parserType?html}</p>
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
