/**
 * Gor3a | جرعة — Official Mobile App Landing Page Controller (landing.js)
 * Lightweight, zero-dependency, 60fps interactive animations & bilingual support
 */

document.addEventListener("DOMContentLoaded", () => {
  // 1. Navbar scroll elevation & mobile menu
  const navbar = document.getElementById("landingNavbar");
  const mobileMenuBtn = document.getElementById("mobileMenuBtn");
  const navLinksList = document.getElementById("navLinksList");

  window.addEventListener("scroll", () => {
    if (window.scrollY > 24) {
      navbar.classList.add("scrolled");
    } else {
      navbar.classList.remove("scrolled");
    }
  }, { passive: true });

  if (mobileMenuBtn && navLinksList) {
    mobileMenuBtn.addEventListener("click", () => {
      navLinksList.classList.toggle("mobile-open");
    });
    navLinksList.querySelectorAll("a").forEach((link) => {
      link.addEventListener("click", () => navLinksList.classList.remove("mobile-open"));
    });
  }

  // 2. Dark / Light Theme Toggle (matching Mobile App moon/sun toggle)
  const themeToggleBtn = document.getElementById("themeToggleBtn");
  const themeIconMoon = document.getElementById("themeIconMoon");
  const themeIconSun = document.getElementById("themeIconSun");

  const savedTheme = localStorage.getItem("gor3a_landing_theme") || "light";
  applyTheme(savedTheme);

  if (themeToggleBtn) {
    themeToggleBtn.addEventListener("click", () => {
      const current = document.documentElement.getAttribute("data-theme") || "light";
      const next = current === "dark" ? "light" : "dark";
      applyTheme(next);
      localStorage.setItem("gor3a_landing_theme", next);
    });
  }

  function applyTheme(theme) {
    document.documentElement.setAttribute("data-theme", theme);
    if (themeIconMoon && themeIconSun) {
      if (theme === "dark") {
        themeIconMoon.style.display = "none";
        themeIconSun.style.display = "block";
      } else {
        themeIconMoon.style.display = "block";
        themeIconSun.style.display = "none";
      }
    }
  }

  // 3. Bilingual Switcher (AR / EN) matching Gor3a Mobile App
  const langToggleBtn = document.getElementById("langToggleBtn");
  const langToggleLabel = document.getElementById("langToggleLabel");
  let currentLang = "ar";

  if (langToggleBtn) {
    langToggleBtn.addEventListener("click", () => {
      currentLang = currentLang === "ar" ? "en" : "ar";
      document.documentElement.lang = currentLang;
      document.body.dir = currentLang === "ar" ? "rtl" : "ltr";
      langToggleLabel.textContent = currentLang === "ar" ? "English" : "العربية";

      document.querySelectorAll("[data-ar][data-en]").forEach((el) => {
        const val = currentLang === "ar" ? el.getAttribute("data-ar") : el.getAttribute("data-en");
        if (val) {
          if (el.tagName === "INPUT") {
            el.placeholder = val;
          } else {
            el.innerHTML = val;
          }
        }
      });
    });
  }

  // 4. Hero Phone Screen Switcher & 3D Parallax Tilt
  const heroPrimaryImg = document.getElementById("heroPrimaryImg");
  const heroScreenPills = document.querySelectorAll(".hero-screen-pill");
  const heroStage = document.getElementById("heroVisualStage");
  const phonesComposition = document.getElementById("phonesComposition");

  heroScreenPills.forEach((pill) => {
    pill.addEventListener("click", () => {
      heroScreenPills.forEach((p) => p.classList.remove("active"));
      pill.classList.add("active");
      const targetSrc = pill.getAttribute("data-screen");
      if (heroPrimaryImg && targetSrc) {
        heroPrimaryImg.style.opacity = "0.25";
        heroPrimaryImg.style.transform = "scale(0.97)";
        setTimeout(() => {
          heroPrimaryImg.src = targetSrc;
          heroPrimaryImg.style.opacity = "1";
          heroPrimaryImg.style.transform = "scale(1)";
        }, 180);
      }
    });
  });

  // Subtle 3D Mouse Tilt on Hero Stage (Desktop only)
  const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (heroStage && phonesComposition && !prefersReducedMotion && window.innerWidth > 960) {
    heroStage.addEventListener("mousemove", (e) => {
      const rect = heroStage.getBoundingClientRect();
      const x = (e.clientX - rect.left) / rect.width - 0.5;
      const y = (e.clientY - rect.top) / rect.height - 0.5;
      phonesComposition.style.transform = `rotateY(${x * 10}deg) rotateX(${-y * 10}deg)`;
    });

    heroStage.addEventListener("mouseleave", () => {
      phonesComposition.style.transform = "rotateY(0deg) rotateX(0deg)";
    });
  }

  // 5. Scroll Reveal Observer & Animated Number Counters
  const revealElements = document.querySelectorAll(".reveal-on-scroll");
  const revealObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add("is-visible");
          revealObserver.unobserve(entry.target);
        }
      });
    },
    { threshold: 0.14, rootMargin: "0px 0px -40px 0px" }
  );

  revealElements.forEach((el) => revealObserver.observe(el));

  // Animated Counters in Stats Ribbon
  const counterElements = document.querySelectorAll("[data-count-target]");
  const counterObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          animateCounter(entry.target);
          counterObserver.unobserve(entry.target);
        }
      });
    },
    { threshold: 0.4 }
  );

  counterElements.forEach((el) => counterObserver.observe(el));

  function animateCounter(el) {
    const target = parseFloat(el.getAttribute("data-count-target"));
    const prefix = el.getAttribute("data-count-prefix") || "";
    const suffix = el.getAttribute("data-count-suffix") || "";
    const decimals = parseInt(el.getAttribute("data-count-decimals") || "0", 10);
    const duration = 1600;
    const startTime = performance.now();

    function step(now) {
      const progress = Math.min((now - startTime) / duration, 1);
      const eased = 1 - Math.pow(1 - progress, 3); // cubic ease-out
      const current = target * eased;

      let formatted;
      if (decimals > 0) {
        formatted = current.toFixed(decimals);
      } else {
        formatted = Math.round(current).toLocaleString("en-US");
      }

      el.textContent = `${prefix}${formatted}${suffix}`;
      if (progress < 1) {
        requestAnimationFrame(step);
      }
    }

    requestAnimationFrame(step);
  }

  // 6. Interactive "How It Works" Step-by-Step Screen Switcher
  const stepCards = document.querySelectorAll(".step-card");
  const howScreenImgs = document.querySelectorAll(".how-screen-img");
  const howScreenCaption = document.getElementById("howScreenCaption");
  let activeStepIndex = 0;
  let stepInterval = null;
  let isHowPaused = false;

  function activateStep(index) {
    activeStepIndex = index;
    stepCards.forEach((card, idx) => {
      card.classList.toggle("active", idx === index);
    });
    howScreenImgs.forEach((img, idx) => {
      img.classList.toggle("active", idx === index);
    });
    if (howScreenCaption && stepCards[index]) {
      const capAr = stepCards[index].getAttribute("data-caption-ar");
      const capEn = stepCards[index].getAttribute("data-caption-en");
      howScreenCaption.textContent = currentLang === "ar" ? capAr : capEn;
    }
  }

  function startStepAutoCycle() {
    if (stepInterval) clearInterval(stepInterval);
    stepInterval = setInterval(() => {
      if (!isHowPaused && stepCards.length > 0) {
        const nextIdx = (activeStepIndex + 1) % stepCards.length;
        activateStep(nextIdx);
      }
    }, 5000);
  }

  stepCards.forEach((card, index) => {
    card.addEventListener("click", () => {
      activateStep(index);
      startStepAutoCycle();
    });
    card.addEventListener("mouseenter", () => { isHowPaused = true; });
    card.addEventListener("mouseleave", () => { isHowPaused = false; });
  });

  startStepAutoCycle();

  // 7. FAQ Accordion
  const faqItems = document.querySelectorAll(".faq-item");
  faqItems.forEach((item) => {
    const btn = item.querySelector(".faq-question");
    const ans = item.querySelector(".faq-answer");
    if (btn && ans) {
      btn.addEventListener("click", () => {
        const isOpen = item.classList.contains("open");
        faqItems.forEach((other) => {
          other.classList.remove("open");
          const otherAns = other.querySelector(".faq-answer");
          if (otherAns) otherAns.style.maxHeight = null;
        });
        if (!isOpen) {
          item.classList.add("open");
          ans.style.maxHeight = ans.scrollHeight + "px";
        }
      });
    }
  });

  // Open first FAQ item by default
  if (faqItems.length > 0) {
    const firstAns = faqItems[0].querySelector(".faq-answer");
    faqItems[0].classList.add("open");
    if (firstAns) firstAns.style.maxHeight = firstAns.scrollHeight + "px";
  }

  // 8. Modals: Store Download / QR Modal & Screen Lightbox Modal
  const downloadModal = document.getElementById("downloadAppModal");
  const closeDownloadModalBtn = document.getElementById("closeDownloadModalBtn");
  const modalStoreBadgeTitle = document.getElementById("modalStoreBadgeTitle");

  document.querySelectorAll("[data-open-download-modal]").forEach((btn) => {
    btn.addEventListener("click", (e) => {
      e.preventDefault();
      const storeType = btn.getAttribute("data-open-download-modal");
      if (modalStoreBadgeTitle) {
        if (storeType === "google-play") {
          modalStoreBadgeTitle.textContent = currentLang === "ar"
            ? "تحميل تطبيق جرعة (Gor3a) للأندرويد — Google Play"
            : "Download Gor3a for Android — Google Play";
        } else if (storeType === "app-store") {
          modalStoreBadgeTitle.textContent = currentLang === "ar"
            ? "تحميل تطبيق جرعة (Gor3a) للآيفون — App Store"
            : "Download Gor3a for iOS — App Store";
        } else {
          modalStoreBadgeTitle.textContent = currentLang === "ar"
            ? "حمّل تطبيق جرعة (Gor3a) على هاتفك الآن"
            : "Download Gor3a Mobile App Now";
        }
      }
      if (downloadModal) {
        downloadModal.classList.add("open");
        document.body.style.overflow = "hidden";
      }
    });
  });

  if (closeDownloadModalBtn && downloadModal) {
    closeDownloadModalBtn.addEventListener("click", () => {
      downloadModal.classList.remove("open");
      document.body.style.overflow = "";
    });
    downloadModal.addEventListener("click", (e) => {
      if (e.target === downloadModal) {
        downloadModal.classList.remove("open");
        document.body.style.overflow = "";
      }
    });
  }

  // Screen Lightbox Preview Modal
  const screenLightboxModal = document.getElementById("screenLightboxModal");
  const closeLightboxBtn = document.getElementById("closeLightboxBtn");
  const lightboxImg = document.getElementById("lightboxScreenImg");
  const lightboxTitle = document.getElementById("lightboxScreenTitle");

  document.querySelectorAll(".screen-showcase-card").forEach((card) => {
    card.addEventListener("click", () => {
      const img = card.querySelector("img");
      const titleEl = card.querySelector(".screen-card-title");
      if (img && lightboxImg && screenLightboxModal) {
        lightboxImg.src = img.src;
        if (lightboxTitle && titleEl) {
          lightboxTitle.textContent = titleEl.textContent;
        }
        screenLightboxModal.classList.add("open");
        document.body.style.overflow = "hidden";
      }
    });
  });

  if (closeLightboxBtn && screenLightboxModal) {
    closeLightboxBtn.addEventListener("click", () => {
      screenLightboxModal.classList.remove("open");
      document.body.style.overflow = "";
    });
    screenLightboxModal.addEventListener("click", (e) => {
      if (e.target === screenLightboxModal) {
        screenLightboxModal.classList.remove("open");
        document.body.style.overflow = "";
      }
    });
  }

  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") {
      if (downloadModal) downloadModal.classList.remove("open");
      if (screenLightboxModal) screenLightboxModal.classList.remove("open");
      document.body.style.overflow = "";
    }
  });
});
