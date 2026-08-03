package com.kuyermqi.quotawidget.webview

import android.content.res.Configuration
import android.webkit.WebView

/**
 * Auth pages (e.g. OpenCode) use `position: absolute; inset: 0` + flex centering, and logos
 * gated by `prefers-color-scheme`. System WebView often sizes the document to content height
 * and may not expose a color-scheme preference, which clips/hides the logo and pins buttons
 * to the top. Force a full-viewport root and show the matching logo.
 */
internal fun WebView.injectFullViewportLayoutFix() {
    val night =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    val showMode = if (night) "dark" else "light"
    val hideMode = if (night) "light" else "dark"
    evaluateJavascript(
        """
        (function(){
          var id='qw-inapp-webview-layout';
          var s=document.getElementById(id);
          if(!s){
            s=document.createElement('style');
            s.id=id;
            (document.head||document.documentElement).appendChild(s);
          }
          s.textContent=[
            'html,body{height:100%!important;min-height:100%!important;width:100%!important;margin:0!important;}',
            '[data-component="root"]{position:fixed!important;inset:0!important;min-height:100%!important;}',
            '[data-component="logo"][data-mode="$showMode"]{display:block!important;}',
            '[data-component="logo"][data-mode="$hideMode"]{display:none!important;}'
          ].join('');
        })();
        """.trimIndent(),
        null,
    )
}
