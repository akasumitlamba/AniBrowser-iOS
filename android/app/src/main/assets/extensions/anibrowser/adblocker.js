/* SPDX-License-Identifier: MPL-2.0 */
/**
 * AniBrowser Built-in Ad & Popup Script Interceptor
 * Runs at document_start in all frames.
 * Neutralizes popup traps, clickjack overlays, malicious window.open, and cosmetic ad banners.
 */
(function() {
  "use strict";

  // 1. Neutralize malicious window.open traps (popunders / unsolicited new windows)
  try {
    const origOpen = window.open;
    let lastUserActionTime = 0;
    ["click", "touchstart", "keydown"].forEach(evt => {
      window.addEventListener(evt, () => { lastUserActionTime = Date.now(); }, {capture: true, passive: true});
    });

    window.open = function(url, target, features) {
      const now = Date.now();
      const isRecentUserAction = (now - lastUserActionTime) < 1200;

      // Ad URL heuristics
      const isAdUrl = url && /popads|popcash|adsterra|propeller|exoclick|syndication|doubleclick|bet365|onclickads|trafficfactory|tsyndicate|adservice/i.test(url);
      if (isAdUrl) {
        return null;
      }

      // If window.open is called with no user gesture or opens blank/ad trap
      if (!isRecentUserAction && (!url || url === "about:blank" || url === "javascript:void(0)")) {
        return null;
      }

      try {
        return origOpen.apply(this, arguments);
      } catch (_) {
        return null;
      }
    };
  } catch (_) {}

  // 2. Clickjack Overlay Buster: Remove full-screen invisible click interceptors
  function defangOverlays() {
    try {
      const allDivs = document.querySelectorAll("div, a, span");
      const vw = window.innerWidth || document.documentElement.clientWidth;
      const vh = window.innerHeight || document.documentElement.clientHeight;

      for (let i = 0; i < allDivs.length; i++) {
        const el = allDivs[i];
        const style = window.getComputedStyle(el);
        if (style.position === "fixed" || style.position === "absolute") {
          const z = parseInt(style.zIndex, 10);
          if (z >= 9999) {
            const rect = el.getBoundingClientRect();
            // If element covers more than 70% of viewport and is mostly transparent
            if (rect.width >= vw * 0.7 && rect.height >= vh * 0.7) {
              const opacity = parseFloat(style.opacity);
              if (opacity <= 0.1 || style.backgroundColor.includes("rgba(0, 0, 0, 0)") || style.backgroundColor === "transparent") {
                el.remove();
              }
            }
          }
        }
      }
    } catch (_) {}
  }

  // 3. Inject high-priority cosmetic stylesheet to hide banner ads and popup containers
  function injectCosmeticFilter() {
    try {
      if (document.getElementById("anibrowser-cosmetic-adblock")) return;
      const style = document.createElement("style");
      style.id = "anibrowser-cosmetic-adblock";
      style.textContent = `
        /* Common video player ad containers & popups */
        .ad-container, .adsbygoogle, [id*="ad_container"], [id*="popunder"],
        [class*="popunder"], [class*="popup-ad"], [id*="banner-ad"],
        div[id^="ad-"], div[class^="ad-"], iframe[src*="doubleclick"],
        iframe[src*="adservice"], iframe[src*="exoclick"], iframe[src*="popcash"],
        .jw-ad-container, div[class*="video-ad"], [data-ad],
        #ad-overlay, .ad-overlay, .ad-banner, .bottom-ad-bar {
          display: none !important;
          visibility: hidden !important;
          height: 0 !important;
          width: 0 !important;
          opacity: 0 !important;
          pointer-events: none !important;
        }
      `;
      (document.head || document.documentElement).appendChild(style);
    } catch (_) {}
  }

  // Run immediately and after DOM is ready
  injectCosmeticFilter();
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", () => {
      injectCosmeticFilter();
      defangOverlays();
    });
  } else {
    defangOverlays();
  }

  // Periodic safety check for dynamically injected ad overlays
  let checks = 0;
  const timer = setInterval(() => {
    defangOverlays();
    injectCosmeticFilter();
    if (++checks > 8) clearInterval(timer);
  }, 1000);
})();
