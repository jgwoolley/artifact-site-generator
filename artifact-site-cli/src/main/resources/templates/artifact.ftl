<#import "layout.ftl" as layout>
<@layout.page title=title rootPath=rootPath pageHeading=pageHeading pageDescription=description>
  <p class="meta">${groupId?html}.${artifactId?html} • version ${version?html} • parser ${parserType?html}</p>

  <p>
    <a class="download-button" href="${downloadUrl?html}">Download</a>
  </p>

  <table>
    <tbody>
      <tr><th>Group Id</th><td>${groupId?html}</td></tr>
      <tr><th>Artifact Id</th><td>${artifactId?html}</td></tr>
      <tr><th>Version</th><td>${version?html}</td></tr>
      <tr><th>License</th><td>${license?html}</td></tr>
      <tr><th>Source Type</th><td>${sourceType?html}</td></tr>
      <tr><th>Source</th><td><a href="${downloadUrl?html}">${sourceValue?html}</a></td></tr>
      <tr><th>SCM</th><td><#if scmUrl?has_content><#if scmUrl?starts_with("http://") || scmUrl?starts_with("https://")><a href="${scmUrl?html}">${scmUrl?html}</a><#else>${scmUrl?html}</#if><#else></#if></td></tr>
      <tr><th>File Name</th><td>${fileName?html}</td></tr>
      <tr><th>SHA-256</th><td>${sha256?html}</td></tr>
      <tr><th>File Size</th><td><#if fileSizeHumanReadable?has_content>${fileSizeHumanReadable?html}<#if fileSizeBytes??> (${fileSizeBytes?c} bytes)</#if><#elseif fileSizeBytes??>${fileSizeBytes?c} bytes</#if></td></tr>
      <tr><th>Authors</th><td><#list authors as author>${author?html}<#if author_has_next>, </#if></#list></td></tr>
      <tr><th>Tags</th><td><#list tags as tag>${tag?html}<#if tag_has_next>, </#if></#list></td></tr>
    </tbody>
  </table>
</@layout.page>
