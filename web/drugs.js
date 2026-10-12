/**
 * EDA Drug Database Frontend Application (drugs.js)
 * 25,098 Official Egyptian Medicines Search, Live Market Pricing & Clinical Substitutes
 */

document.addEventListener("DOMContentLoaded", () => {
  // State
  const state = {
    query: "",
    form: "",
    route: "",
    priceMin: null,
    priceMax: null,
    sortBy: "relevance",
    page: 1,
    pageSize: 24,
    total: 0,
    totalPages: 1,
    isLoading: false,
    selectedDrugId: null,
    searchDebounceTimer: null,
  };

  // DOM Elements - Search & Filters
  const drugSearchInput = document.getElementById("drugSearchInput");
  const clearSearchBtn = document.getElementById("clearSearchBtn");
  const searchSubmitBtn = document.getElementById("searchSubmitBtn");
  const quickChipsList = document.getElementById("quickChipsList");
  const filterForm = document.getElementById("filterForm");
  const filterRoute = document.getElementById("filterRoute");
  const filterPrice = document.getElementById("filterPrice");
  const sortBySelect = document.getElementById("sortBy");
  const resetFiltersBtn = document.getElementById("resetFiltersBtn");
  const pageSizeSelect = document.getElementById("pageSizeSelect");

  // DOM Elements - Results & State Views
  const resultsHeading = document.getElementById("resultsHeading");
  const resultsCountBadge = document.getElementById("resultsCountBadge");
  const drugsLoadingState = document.getElementById("drugsLoadingState");
  const drugsEmptyState = document.getElementById("drugsEmptyState");
  const emptyStateMsg = document.getElementById("emptyStateMsg");
  const emptyResetBtn = document.getElementById("emptyResetBtn");
  const drugsGrid = document.getElementById("drugsGrid");
  const paginationContainer = document.getElementById("paginationContainer");
  const pageNumbersList = document.getElementById("pageNumbersList");
  const firstPageBtn = document.getElementById("firstPageBtn");
  const prevPageBtn = document.getElementById("prevPageBtn");
  const nextPageBtn = document.getElementById("nextPageBtn");
  const lastPageBtn = document.getElementById("lastPageBtn");
  const pageIndicator = document.getElementById("pageIndicator");

  // DOM Elements - Stats
  const statTotalDrugs = document.getElementById("statTotalDrugs");
  const statPricedDrugs = document.getElementById("statPricedDrugs");
  const statDosageForms = document.getElementById("statDosageForms");

  // DOM Elements - Modal
  const drugDetailModal = document.getElementById("drugDetailModal");
  const closeModalBtn = document.getElementById("closeModalBtn");
  const closeModalBottomBtn = document.getElementById("closeModalBottomBtn");
  const modalEdaId = document.getElementById("modalEdaId");
  const modalStatusBadge = document.getElementById("modalStatusBadge");
  const modalDosageBadge = document.getElementById("modalDosageBadge");
  const modalCommercialEn = document.getElementById("modalCommercialEn");
  const modalCommercialAr = document.getElementById("modalCommercialAr");
  const modalDrugVisual = document.getElementById("modalDrugVisual");
  const modalVisualSkeleton = document.getElementById("modalVisualSkeleton");
  const modalClinicalIndication = document.getElementById("modalClinicalIndication");
  const modalLikelyDiagnosis = document.getElementById("modalLikelyDiagnosis");
  const modalDetailedUsesContent = document.getElementById("modalDetailedUsesContent");
  const modalSourceLink = document.getElementById("modalSourceLink");
  const modalPrecautionsBrief = document.getElementById("modalPrecautionsBrief");
  const modalBasePrice = document.getElementById("modalBasePrice");
  const modalCurrentPrice = document.getElementById("modalCurrentPrice");
  const modalPriceRange = document.getElementById("modalPriceRange");
  const modalTierBadge = document.getElementById("modalTierBadge");
  const modalStripPrice = document.getElementById("modalStripPrice");
  const modalPackageDesc = document.getElementById("modalPackageDesc");
  const modalMarketNoteAr = document.getElementById("modalMarketNoteAr");
  const modalMarketNoteEn = document.getElementById("modalMarketNoteEn");
  const modalGenericName = document.getElementById("modalGenericName");
  const modalActiveIngredients = document.getElementById("modalActiveIngredients");
  const modalStrength = document.getElementById("modalStrength");
  const modalManufacturer = document.getElementById("modalManufacturer");
  const modalDrugClass = document.getElementById("modalDrugClass");
  const modalRoute = document.getElementById("modalRoute");
  const modalDosageForm = document.getElementById("modalDosageForm");
  const modalProvenance = document.getElementById("modalProvenance");
  const genericsCount = document.getElementById("genericsCount");
  const genericsGrid = document.getElementById("genericsGrid");
  const therapeuticCount = document.getElementById("therapeuticCount");
  const therapeuticGrid = document.getElementById("therapeuticGrid");
  const copyDrugDetailsBtn = document.getElementById("copyDrugDetailsBtn");
  const shareDrugLinkBtn = document.getElementById("shareDrugLinkBtn");
  const edaToast = document.getElementById("edaToast");

  // Initialize
  initApp();

  async function initApp() {
    setupEventListeners();
    fetchFacetsAndStats();
    parseUrlParams();
    fetchDrugs();
  }

  // 1. URL Query State Parsing
  function parseUrlParams() {
    const params = new URLSearchParams(window.location.search);
    if (params.has("q")) {
      state.query = params.get("q").trim();
      drugSearchInput.value = state.query;
      clearSearchBtn.classList.toggle("hidden", !state.query);
    }
    if (params.has("form")) {
      state.form = params.get("form");
      filterForm.value = state.form;
    }
    if (params.has("route")) {
      state.route = params.get("route");
      filterRoute.value = state.route;
    }
    if (params.has("price")) {
      const priceVal = params.get("price");
      filterPrice.value = priceVal;
      applyPriceBracket(priceVal);
    }
    if (params.has("sort")) {
      state.sortBy = params.get("sort");
      sortBySelect.value = state.sortBy;
    }
    if (params.has("page")) {
      const p = parseInt(params.get("page"), 10);
      if (!isNaN(p) && p > 0) state.page = p;
    }
    if (params.has("pageSize")) {
      const ps = parseInt(params.get("pageSize"), 10);
      if (!isNaN(ps)) {
        state.pageSize = ps;
        pageSizeSelect.value = String(ps);
      }
    }
    if (params.has("id")) {
      const targetId = params.get("id");
      openDrugDetails(targetId);
    }
  }

  function updateUrlParams() {
    const params = new URLSearchParams();
    if (state.query) params.set("q", state.query);
    if (state.form) params.set("form", state.form);
    if (state.route) params.set("route", state.route);
    if (filterPrice.value) params.set("price", filterPrice.value);
    if (state.sortBy !== "relevance") params.set("sort", state.sortBy);
    if (state.page > 1) params.set("page", state.page);
    if (state.pageSize !== 24) params.set("pageSize", state.pageSize);
    if (state.selectedDrugId) params.set("id", state.selectedDrugId);

    const newUrl = `${window.location.pathname}${params.toString() ? "?" + params.toString() : ""}`;
    window.history.replaceState({}, "", newUrl);
  }

  function applyPriceBracket(val) {
    if (!val) {
      state.priceMin = null;
      state.priceMax = null;
      return;
    }
    const [min, max] = val.split("-").map(Number);
    state.priceMin = !isNaN(min) ? min : null;
    state.priceMax = !isNaN(max) ? max : null;
  }

  // 2. Fetch Facets & Initial Stats
  async function fetchFacetsAndStats() {
    try {
      const res = await fetch("/api/drugs/facets");
      if (!res.ok) return;
      const data = await res.json();
      if (data.total_registered_drugs) {
        statTotalDrugs.textContent = data.total_registered_drugs.toLocaleString();
      }
      if (data.priced_drugs_count) {
        statPricedDrugs.textContent = data.priced_drugs_count.toLocaleString();
      }
      if (data.dosage_forms && data.dosage_forms.length > 0) {
        statDosageForms.textContent = `${data.dosage_forms.length} Forms`;
      }
    } catch (err) {
      console.warn("Could not fetch facets from /api/drugs/facets", err);
    }
  }

  // 3. Fetch Medicines List
  async function fetchDrugs() {
    state.isLoading = true;
    showLoading();

    try {
      const params = new URLSearchParams({
        mode: "catalog",
        page: state.page,
        page_size: state.pageSize,
        sort_by: state.sortBy,
      });

      if (state.query) params.set("query", state.query);
      if (state.form) params.set("form", state.form);
      if (state.route) params.set("route", state.route);
      if (state.priceMin !== null) params.set("price_min", state.priceMin);
      if (state.priceMax !== null) params.set("price_max", state.priceMax);

      const res = await fetch(`/api/drugs/search?${params.toString()}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();

      state.total = data.total || 0;
      state.totalPages = data.total_pages || 1;
      state.page = data.page || 1;

      renderResults(data.results || []);
      renderPagination();
      updateUrlParams();
    } catch (err) {
      console.error("Error fetching drug catalog:", err);
      showErrorState("Failed to retrieve Egyptian medicines from the server. Please check the backend connection.");
    } finally {
      state.isLoading = false;
      hideLoading();
    }
  }

  function showLoading() {
    drugsLoadingState.classList.remove("hidden");
    drugsEmptyState.classList.add("hidden");
    drugsGrid.classList.add("hidden");
    paginationContainer.classList.add("hidden");
  }

  function hideLoading() {
    drugsLoadingState.classList.add("hidden");
  }

  function showErrorState(msg) {
    drugsEmptyState.classList.remove("hidden");
    emptyStateMsg.textContent = msg;
    drugsGrid.classList.add("hidden");
    paginationContainer.classList.add("hidden");
    resultsCountBadge.textContent = "0 medicines";
  }

  // 4. Render Results Grid
  function renderResults(results) {
    if (!results || results.length === 0) {
      drugsGrid.innerHTML = "";
      drugsGrid.classList.add("hidden");
      drugsEmptyState.classList.remove("hidden");
      paginationContainer.classList.add("hidden");

      if (state.query) {
        resultsHeading.textContent = `Search results for "${state.query}"`;
        emptyStateMsg.textContent = `No Egyptian medicines found matching "${state.query}".`;
      } else {
        resultsHeading.textContent = "Official Egyptian Medicines";
        emptyStateMsg.textContent = "No medications match the selected filters.";
      }
      resultsCountBadge.textContent = "0 medicines";
      return;
    }

    drugsEmptyState.classList.add("hidden");
    drugsGrid.classList.remove("hidden");
    paginationContainer.classList.remove("hidden");

    if (state.query) {
      resultsHeading.textContent = `Search results for "${state.query}"`;
    } else {
      resultsHeading.textContent = "Official Egyptian Medicines";
    }

    const startIdx = (state.page - 1) * state.pageSize + 1;
    const endIdx = Math.min(state.total, startIdx + results.length - 1);
    resultsCountBadge.textContent = `Showing ${startIdx.toLocaleString()}–${endIdx.toLocaleString()} of ${state.total.toLocaleString()} medicines`;

    drugsGrid.innerHTML = results.map(renderDrugCard).join("");

    // Attach click events on cards
    drugsGrid.querySelectorAll(".drug-card").forEach((card) => {
      card.addEventListener("click", () => {
        const drugId = card.getAttribute("data-drug-id");
        openDrugDetails(drugId);
      });
    });
  }

  function renderDrugCard(item) {
    const drug = item.drug;
    const pricing = item.pricing || {};

    const basePriceStr = pricing.registered_price_egp != null
      ? `${pricing.registered_price_egp.toFixed(2)} EGP`
      : "Not Listed";

    const currentPriceStr = pricing.expected_price_current_egp != null
      ? `${pricing.expected_price_current_egp.toFixed(2)} EGP`
      : "Not Listed";

    const priceRangeStr = (pricing.expected_price_min_egp != null && pricing.expected_price_max_egp != null)
      ? `${pricing.expected_price_min_egp.toFixed(1)} - ${pricing.expected_price_max_egp.toFixed(1)} EGP`
      : "";

    const stripPriceHtml = pricing.expected_strip_price_egp != null
      ? `
        <div class="strip-price-row">
          <span>سعر الشريط المتوقع:</span>
          <span class="strip-price-badge">~${pricing.expected_strip_price_egp.toFixed(2)} EGP</span>
        </div>
      `
      : "";

    const tierBadge = pricing.adjustment_percentage_str && pricing.adjustment_percentage_str !== "غير متاح"
      ? `<span class="card-badge" style="background:#fef3c7; color:#92400e; border:1px solid #fde68a;">${pricing.adjustment_percentage_str}</span>`
      : "";

    return `
      <article class="drug-card" data-drug-id="${escapeHtml(drug.id)}" tabindex="0">
        <div class="drug-card-top">
          
          <div class="drug-card-badges">
            <span class="card-badge badge-eda">${escapeHtml(drug.id)}</span>
            <span class="card-badge badge-form">${escapeHtml(drug.dosage_form || "Form")}</span>
            ${drug.route ? `<span class="card-badge badge-route">${escapeHtml(drug.route)}</span>` : ""}
            ${tierBadge}
          </div>

          <div class="drug-card-names">
            <h3 class="drug-name-en">${escapeHtml(drug.full_commercial_name_en || drug.commercial_name_en)}</h3>
            ${drug.commercial_name_ar ? `<div class="drug-name-ar">${escapeHtml(drug.commercial_name_ar)}</div>` : ""}
          </div>

          <div class="drug-scientific-row">
            <span class="drug-generic-lbl">Active Generic (INN):</span>
            <span class="drug-generic-name">${escapeHtml(drug.generic_name || "Unspecified")}</span>
          </div>

          <div class="drug-mfg-row">
            <span>🏭</span>
            <span class="drug-mfg-name">${escapeHtml(drug.manufacturer || "Registered Egyptian Mfr")}</span>
          </div>

        </div>

        <div class="drug-card-bottom">
          
          <div class="drug-card-pricing">
            <div class="pricing-row-top">
              <span class="price-reg-lbl">Registered Base:</span>
              <span class="price-reg-val">${basePriceStr}</span>
            </div>

            <div class="price-current-row">
              <span class="price-current-lbl">السعر المتوقع حالياً:</span>
              <span class="price-current-val">${currentPriceStr}</span>
            </div>

            ${priceRangeStr ? `<div style="font-size:0.7rem; color:var(--text-muted); text-align:right; margin-bottom:0.25rem;">النطاق: ${priceRangeStr}</div>` : ""}

            ${stripPriceHtml}
          </div>

          <div class="drug-card-action">
            <button type="button" class="btn btn-secondary btn-sm view-details-btn">
              عرض التفاصيل والبدائل المصرية 🔍
            </button>
          </div>

        </div>
      </article>
    `;
  }

  // 5. Render Pagination
  function renderPagination() {
    if (state.totalPages <= 1) {
      paginationContainer.classList.add("hidden");
      return;
    }

    paginationContainer.classList.remove("hidden");
    firstPageBtn.disabled = state.page <= 1;
    prevPageBtn.disabled = state.page <= 1;
    nextPageBtn.disabled = state.page >= state.totalPages;
    lastPageBtn.disabled = state.page >= state.totalPages;
    pageIndicator.textContent = `Page ${state.page} of ${state.totalPages}`;

    // Compute visible page window (up to 7 page numbers)
    const pages = [];
    const maxVisible = 5;
    let start = Math.max(1, state.page - Math.floor(maxVisible / 2));
    let end = Math.min(state.totalPages, start + maxVisible - 1);
    if (end - start + 1 < maxVisible) {
      start = Math.max(1, end - maxVisible + 1);
    }

    for (let p = start; p <= end; p++) {
      pages.push(p);
    }

    pageNumbersList.innerHTML = pages
      .map(
        (p) => `
        <button type="button" class="page-num-btn ${p === state.page ? "active" : ""}" data-page="${p}">
          ${p}
        </button>
      `
      )
      .join("");

    pageNumbersList.querySelectorAll(".page-num-btn").forEach((btn) => {
      btn.addEventListener("click", () => {
        const p = parseInt(btn.getAttribute("data-page"), 10);
        if (p !== state.page) {
          state.page = p;
          fetchDrugs();
          window.scrollTo({ top: 300, behavior: "smooth" });
        }
      });
    });
  }

  // 6. Open Drug Details Modal
  async function openDrugDetails(drugId) {
    if (!drugId) return;
    state.selectedDrugId = drugId;
    updateUrlParams();

    // Show modal in loading state
    drugDetailModal.classList.remove("hidden");
    document.body.style.overflow = "hidden";

    modalCommercialEn.textContent = "Loading medicine details...";
    modalCommercialAr.textContent = "جاري التحميل...";
    modalEdaId.textContent = drugId;

    // 1. Immediately reset image to avoid showing cached image of previous medicine!
    if (modalDrugVisual) {
      modalDrugVisual.src = "";
      modalDrugVisual.classList.add("hidden");
      modalDrugVisual.style.display = "none";
      modalDrugVisual.style.opacity = "0";
    }
    if (modalVisualSkeleton) {
      modalVisualSkeleton.style.display = "flex";
      modalVisualSkeleton.style.opacity = "1";
    }

    // 2. Activate shimmer loading on text content
    if (modalClinicalIndication) {
      modalClinicalIndication.textContent = "جاري استرجاع بيانات التشخيص ودواعي الاستعمال...";
      modalClinicalIndication.classList.add("shimmer-active");
    }
    if (modalLikelyDiagnosis) {
      modalLikelyDiagnosis.textContent = "جاري التحليل الإكلينيكي...";
      modalLikelyDiagnosis.classList.add("shimmer-active");
    }
    if (modalDetailedUsesContent) {
      modalDetailedUsesContent.textContent = "جاري الاتصال بقاعدة بيانات موسوعة الأدوية...";
      modalDetailedUsesContent.classList.add("shimmer-active");
    }

    // 3. Initiate image load for THIS specific drugId
    if (modalDrugVisual) {
      const fallbackUrl = `/api/drugs/${encodeURIComponent(drugId)}/image?type=svg`;
      modalDrugVisual.onload = function() {
        if (modalVisualSkeleton) modalVisualSkeleton.style.display = "none";
        modalDrugVisual.classList.remove("hidden");
        modalDrugVisual.style.display = "block";
        modalDrugVisual.style.opacity = "1";
      };
      modalDrugVisual.onerror = function() {
        this.onerror = null;
        this.src = fallbackUrl;
      };
      modalDrugVisual.src = `/api/drugs/${encodeURIComponent(drugId)}/image`;
    }

    modalBasePrice.textContent = "-- EGP";
    modalCurrentPrice.textContent = "-- EGP";
    modalPriceRange.textContent = "";
    modalStripPrice.textContent = "-- EGP";
    modalGenericName.textContent = "--";
    modalActiveIngredients.innerHTML = `<span class="ingredient-chip">Loading...</span>`;
    genericsGrid.innerHTML = `<div style="padding:1rem; color:var(--text-muted);">Finding Egyptian market equivalents...</div>`;
    therapeuticGrid.innerHTML = `<div style="padding:1rem; color:var(--text-muted);">Finding therapeutic class substitutes...</div>`;

    try {
      // Fetch details and alternatives in parallel
      const [detailRes, altRes] = await Promise.all([
        fetch(`/api/drugs/${encodeURIComponent(drugId)}`),
        fetch(`/api/drugs/${encodeURIComponent(drugId)}/alternatives?limit=12`),
      ]);

      if (!detailRes.ok) throw new Error(`HTTP ${detailRes.status}`);
      const detailData = await detailRes.json();
      const altData = altRes.ok ? await altRes.json() : { exact_generics: [], therapeutic_alternatives: [] };

      renderModalDetails(detailData, altData);
    } catch (err) {
      console.error("Failed to load drug details for", drugId, err);
      modalCommercialEn.textContent = "Failed to load medicine details";
      modalCommercialAr.textContent = "تعذر تحميل بيانات المستحضر";
      if (modalClinicalIndication) {
        modalClinicalIndication.textContent = "مستحضر صيدلاني علاجي مسجل بهيئة الدواء المصرية.";
        modalClinicalIndication.classList.remove("shimmer-active");
      }
      if (modalLikelyDiagnosis) {
        modalLikelyDiagnosis.textContent = "يرجى مراجعة الصيدلي أو الطبيب المعالج.";
        modalLikelyDiagnosis.classList.remove("shimmer-active");
      }
      if (modalDetailedUsesContent) {
        modalDetailedUsesContent.textContent = "تعذر الاتصال بقاعدة البيانات الخارجية.";
        modalDetailedUsesContent.classList.remove("shimmer-active");
      }
    }
  }

  function renderModalDetails(detailData, altData) {
    const drug = detailData.drug;
    const pricing = detailData.pricing || {};
    const details = drug.indication_details || {};

    modalEdaId.textContent = drug.id;
    modalCommercialEn.textContent = drug.full_commercial_name_en || drug.commercial_name_en;
    modalCommercialAr.textContent = drug.commercial_name_ar || "";
    modalDosageBadge.textContent = drug.dosage_form || "Dosage Form";

    // Clinical Indications & Diagnosis (remove shimmer)
    if (modalClinicalIndication) {
      modalClinicalIndication.classList.remove("shimmer-active");
      modalClinicalIndication.textContent = drug.clinical_indication || details.what_it_does || details.what_it_treats || "مستحضر صيدلاني علاجي معتمد بهيئة الدواء المصرية.";
    }
    if (modalLikelyDiagnosis) {
      modalLikelyDiagnosis.classList.remove("shimmer-active");
      modalLikelyDiagnosis.textContent = drug.likely_diagnosis || details.likely_diagnosis || "استشارة الطبيب المعالج للتشخيص الإكلينيكي.";
    }

    // Detailed Condition Description & Uses (from Altibbi or Clinical Knowledge)
    if (modalDetailedUsesContent) {
      modalDetailedUsesContent.classList.remove("shimmer-active");
      const richUses = details.what_it_treats_detailed || details.what_it_treats || drug.clinical_indication;
      modalDetailedUsesContent.textContent = richUses || "مستحضر صيدلاني مخصص لعلاج ومكافحة الحالة المرضية المقررة بالروشتة الطبية.";
    }

    // Altibbi Medical Reference Link
    if (modalSourceLink) {
      const sourceUrl = details.source_url || (details.web_profile ? details.web_profile.source_url : null);
      if (sourceUrl) {
        modalSourceLink.href = sourceUrl;
        modalSourceLink.style.display = "inline-flex";
        modalSourceLink.textContent = `🌐 عرض صفحة ${drug.commercial_name_ar || drug.commercial_name_en} على موسوعة الطبي ↗`;
      } else {
        const queryTerm = drug.commercial_name_ar || drug.generic_name || drug.commercial_name_en;
        modalSourceLink.href = `https://altibbi.com/search?q=${encodeURIComponent(queryTerm)}`;
        modalSourceLink.style.display = "inline-flex";
        modalSourceLink.textContent = "🌐 البحث عن المستحضر في موسوعة الطبي (Altibbi) ↗";
      }
    }

    if (modalPrecautionsBrief) {
      modalPrecautionsBrief.textContent = details.precautions ? `إرشاد: ${details.precautions.split(".")[0]}` : "";
    }

    // Pricing
    modalBasePrice.textContent = pricing.registered_price_egp != null
      ? `${pricing.registered_price_egp.toFixed(2)} EGP`
      : "Not Listed (غير مسعر)";

    modalCurrentPrice.textContent = pricing.expected_price_current_egp != null
      ? `${pricing.expected_price_current_egp.toFixed(2)} EGP`
      : "Not Available";

    if (pricing.expected_price_min_egp != null && pricing.expected_price_max_egp != null) {
      modalPriceRange.textContent = `Range: ${pricing.expected_price_min_egp.toFixed(1)} to ${pricing.expected_price_max_egp.toFixed(1)} EGP`;
    } else {
      modalPriceRange.textContent = "";
    }

    modalTierBadge.textContent = pricing.pricing_tier
      ? `${pricing.pricing_tier} (${pricing.adjustment_percentage_str || ""})`
      : "EDA Regulatory Tier";

    if (pricing.expected_strip_price_egp != null) {
      modalStripPrice.textContent = `${pricing.expected_strip_price_egp.toFixed(2)} EGP`;
      modalPackageDesc.textContent = pricing.package_description
        ? `${pricing.package_description}`
        : "سعر الشريط";
    } else {
      modalStripPrice.textContent = "N/A";
      modalPackageDesc.textContent = pricing.unit_type || "عبوة واحدة";
    }

    modalMarketNoteAr.textContent = pricing.market_note_ar || "";
    modalMarketNoteEn.textContent = pricing.market_note_en || "";

    // Clinical Specs
    modalGenericName.textContent = drug.generic_name || "Unspecified";
    modalStrength.textContent = drug.strength || "Standard formulation";
    modalManufacturer.textContent = drug.manufacturer || "Registered Pharmaceutical Company";
    modalDrugClass.textContent = drug.drug_class || "Pharmacological Class Unspecified";
    modalRoute.textContent = drug.route || "Administration Route Unspecified";
    modalDosageForm.textContent = drug.dosage_form || "Standard Form";
    modalProvenance.textContent = `${drug.source || "Egyptian Drug Authority (EDA)"} • Verified ${drug.source_last_updated || "2026"}`;

    // Active Ingredients Chips
    const ingredients = drug.active_ingredients || [];
    if (ingredients.length > 0) {
      modalActiveIngredients.innerHTML = ingredients
        .map(
          (ing) => `
          <button type="button" class="ingredient-chip" data-ingredient="${escapeHtml(ing)}" title="Filter by ${escapeHtml(ing)}">
            ${escapeHtml(ing)}
          </button>
        `
        )
        .join("");

      modalActiveIngredients.querySelectorAll(".ingredient-chip").forEach((btn) => {
        btn.addEventListener("click", () => {
          const ing = btn.getAttribute("data-ingredient");
          closeModal();
          state.query = ing;
          drugSearchInput.value = ing;
          clearSearchBtn.classList.remove("hidden");
          state.page = 1;
          fetchDrugs();
        });
      });
    } else {
      modalActiveIngredients.innerHTML = `<span class="spec-value">${escapeHtml(drug.generic_name || "Standard Active Substance")}</span>`;
    }

    // Alternatives: Exact Generics
    const generics = altData.exact_generics || [];
    genericsCount.textContent = `${generics.length} ${generics.length === 1 ? "بديل متطابق" : "مثيل متطابق مسجل"}`;
    if (generics.length > 0) {
      genericsGrid.innerHTML = generics.map(renderAlternativeCard).join("");
      attachAltClickEvents(genericsGrid);
    } else {
      genericsGrid.innerHTML = `
        <div style="grid-column: 1 / -1; padding: 0.85rem; color: var(--text-muted); font-size: 0.85rem; background: var(--bg-page); border-radius: var(--radius-sm); font-family: 'Cairo', sans-serif;">
          لا توجد مستحضرات تجارية أخرى مسجلة بنفس التركيبة الكيميائية الفعالة تحديداً في قاعدة البيانات.
        </div>
      `;
    }

    // Alternatives: Therapeutic Class
    const therapeutics = altData.therapeutic_alternatives || [];
    therapeuticCount.textContent = `${therapeutics.length} بدائل فئة علاجية`;
    if (therapeutics.length > 0) {
      therapeuticGrid.innerHTML = therapeutics.map(renderAlternativeCard).join("");
      attachAltClickEvents(therapeuticGrid);
    } else {
      therapeuticGrid.innerHTML = `
        <div style="grid-column: 1 / -1; padding: 0.85rem; color: var(--text-muted); font-size: 0.85rem; background: var(--bg-page); border-radius: var(--radius-sm); font-family: 'Cairo', sans-serif;">
          لا توجد مستحضرات أخرى مسجلة في نفس المجموعة الدوائية.
        </div>
      `;
    }

    // Copy Drug Details Handler
    copyDrugDetailsBtn.onclick = () => {
      const summaryText = [
        `💊 Drug: ${drug.full_commercial_name_en || drug.commercial_name_en}`,
        drug.commercial_name_ar ? `الاسم التجاري: ${drug.commercial_name_ar}` : null,
        `Generic: ${drug.generic_name}`,
        `Dosage Form: ${drug.dosage_form} | Route: ${drug.route || "N/A"}`,
        `Manufacturer: ${drug.manufacturer || "N/A"}`,
        `Official Registered Price: ${pricing.registered_price_egp != null ? pricing.registered_price_egp + " EGP" : "N/A"}`,
        `Expected Current Egypt Price: ${pricing.expected_price_current_egp != null ? pricing.expected_price_current_egp + " EGP" : "N/A"}`,
        pricing.expected_strip_price_egp != null ? `Expected Strip Price: ${pricing.expected_strip_price_egp} EGP` : null,
        `EDA Code: ${drug.id}`,
        `Egyptian Drug Authority Database (25,098 Official Medicines)`,
      ]
        .filter(Boolean)
        .join("\n");

      navigator.clipboard.writeText(summaryText).then(() => {
        showToast("Medicine details copied to clipboard! 📋");
      });
    };

    // Share Link Handler
    shareDrugLinkBtn.onclick = () => {
      const shareUrl = `${window.location.origin}/drugs?id=${encodeURIComponent(drug.id)}`;
      navigator.clipboard.writeText(shareUrl).then(() => {
        showToast("Shareable link copied to clipboard! 🔗");
      });
    };
  }

  function renderAlternativeCard(item) {
    const d = item.drug;
    const p = item.pricing || {};

    const baseStr = p.registered_price_egp != null ? `${p.registered_price_egp.toFixed(1)} EGP` : "--";
    const currStr = p.expected_price_current_egp != null ? `${p.expected_price_current_egp.toFixed(1)} EGP` : "--";

    return `
      <div class="alt-mini-card" data-drug-id="${escapeHtml(d.id)}" tabindex="0" title="Click to view ${escapeHtml(d.commercial_name_en)}">
        <div>
          <div class="alt-name-en">${escapeHtml(d.commercial_name_en)}</div>
          ${d.commercial_name_ar ? `<div class="alt-name-ar">${escapeHtml(d.commercial_name_ar)}</div>` : ""}
          <div class="alt-mfg">${escapeHtml(d.manufacturer || "Egyptian Mfr")}</div>
        </div>
        <div class="alt-pricing-row">
          <span class="alt-base-price">${baseStr}</span>
          <span class="alt-curr-price">المتوقع: ${currStr}</span>
        </div>
      </div>
    `;
  }

  function attachAltClickEvents(container) {
    container.querySelectorAll(".alt-mini-card").forEach((card) => {
      card.addEventListener("click", () => {
        const altId = card.getAttribute("data-drug-id");
        openDrugDetails(altId);
      });
    });
  }

  function closeModal() {
    drugDetailModal.classList.add("hidden");
    document.body.style.overflow = "";
    state.selectedDrugId = null;
    updateUrlParams();
    if (modalDrugVisual) {
      modalDrugVisual.src = "";
      modalDrugVisual.classList.add("hidden");
      modalDrugVisual.style.display = "none";
    }
    if (modalVisualSkeleton) {
      modalVisualSkeleton.style.display = "flex";
    }
  }

  function showToast(msg) {
    edaToast.textContent = msg;
    edaToast.classList.remove("hidden");
    setTimeout(() => {
      edaToast.classList.add("hidden");
    }, 2800);
  }

  // 7. Event Listeners Setup
  function setupEventListeners() {
    // Search Input debounce
    drugSearchInput.addEventListener("input", (e) => {
      const val = e.target.value.trim();
      clearSearchBtn.classList.toggle("hidden", val.length === 0);

      clearTimeout(state.searchDebounceTimer);
      state.searchDebounceTimer = setTimeout(() => {
        state.query = val;
        state.page = 1;
        fetchDrugs();
      }, 350);
    });

    // Enter key in search
    drugSearchInput.addEventListener("keydown", (e) => {
      if (e.key === "Enter") {
        clearTimeout(state.searchDebounceTimer);
        state.query = drugSearchInput.value.trim();
        state.page = 1;
        fetchDrugs();
      }
    });

    // Search submit button
    searchSubmitBtn.addEventListener("click", () => {
      clearTimeout(state.searchDebounceTimer);
      state.query = drugSearchInput.value.trim();
      state.page = 1;
      fetchDrugs();
    });

    // Clear search button
    clearSearchBtn.addEventListener("click", () => {
      drugSearchInput.value = "";
      clearSearchBtn.classList.add("hidden");
      state.query = "";
      state.page = 1;
      fetchDrugs();
      drugSearchInput.focus();
    });

    // Quick chips
    quickChipsList.querySelectorAll(".quick-chip").forEach((chip) => {
      chip.addEventListener("click", () => {
        const q = chip.getAttribute("data-query");
        drugSearchInput.value = q;
        clearSearchBtn.classList.remove("hidden");
        state.query = q;
        state.page = 1;
        fetchDrugs();
      });
    });

    // Dosage Form filter
    filterForm.addEventListener("change", (e) => {
      state.form = e.target.value;
      state.page = 1;
      fetchDrugs();
    });

    // Route filter
    filterRoute.addEventListener("change", (e) => {
      state.route = e.target.value;
      state.page = 1;
      fetchDrugs();
    });

    // Price bracket filter
    filterPrice.addEventListener("change", (e) => {
      applyPriceBracket(e.target.value);
      state.page = 1;
      fetchDrugs();
    });

    // Sort order
    sortBySelect.addEventListener("change", (e) => {
      state.sortBy = e.target.value;
      state.page = 1;
      fetchDrugs();
    });

    // Page size
    pageSizeSelect.addEventListener("change", (e) => {
      state.pageSize = parseInt(e.target.value, 10) || 24;
      state.page = 1;
      fetchDrugs();
    });

    // Reset filters
    resetFiltersBtn.addEventListener("click", resetAllFilters);
    emptyResetBtn.addEventListener("click", resetAllFilters);

    // Pagination buttons
    firstPageBtn.addEventListener("click", () => {
      if (state.page > 1) {
        state.page = 1;
        fetchDrugs();
        window.scrollTo({ top: 300, behavior: "smooth" });
      }
    });

    prevPageBtn.addEventListener("click", () => {
      if (state.page > 1) {
        state.page -= 1;
        fetchDrugs();
        window.scrollTo({ top: 300, behavior: "smooth" });
      }
    });

    nextPageBtn.addEventListener("click", () => {
      if (state.page < state.totalPages) {
        state.page += 1;
        fetchDrugs();
        window.scrollTo({ top: 300, behavior: "smooth" });
      }
    });

    lastPageBtn.addEventListener("click", () => {
      if (state.page < state.totalPages) {
        state.page = state.totalPages;
        fetchDrugs();
        window.scrollTo({ top: 300, behavior: "smooth" });
      }
    });

    // Modal close events
    closeModalBtn.addEventListener("click", closeModal);
    closeModalBottomBtn.addEventListener("click", closeModal);
    drugDetailModal.addEventListener("click", (e) => {
      if (e.target === drugDetailModal) closeModal();
    });

    document.addEventListener("keydown", (e) => {
      if (e.key === "Escape" && !drugDetailModal.classList.contains("hidden")) {
        closeModal();
      }
    });
  }

  function resetAllFilters() {
    state.query = "";
    state.form = "";
    state.route = "";
    state.priceMin = null;
    state.priceMax = null;
    state.sortBy = "relevance";
    state.page = 1;

    drugSearchInput.value = "";
    clearSearchBtn.classList.add("hidden");
    filterForm.value = "";
    filterRoute.value = "";
    filterPrice.value = "";
    sortBySelect.value = "relevance";

    fetchDrugs();
  }

  function escapeHtml(str) {
    if (!str) return "";
    return String(str)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#039;");
  }
});
