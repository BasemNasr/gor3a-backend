/**
 * Gor3a Admin Dashboard Application (admin.js)
 * Clean Architecture Admin Logic: Staging, Diff Analysis, and Encrypted API Key Management
 */

document.addEventListener("DOMContentLoaded", () => {
  // DOM Elements - Auth & Header
  const adminLoginView = document.getElementById("adminLoginView");
  const adminDashboardView = document.getElementById("adminDashboardView");
  const adminHeaderActions = document.getElementById("adminHeaderActions");
  const adminUserEmail = document.getElementById("adminUserEmail");
  const adminLogoutBtn = document.getElementById("adminLogoutBtn");

  const adminLoginForm = document.getElementById("adminLoginForm");
  const loginEmail = document.getElementById("loginEmail");
  const loginPassword = document.getElementById("loginPassword");
  const loginSpinner = document.getElementById("loginSpinner");
  const loginBtnText = document.getElementById("loginBtnText");
  const loginErrorAlert = document.getElementById("loginErrorAlert");

  // DOM Elements - Stats
  const statTotalDrugsCount = document.getElementById("statTotalDrugsCount");
  const statApiKeyStatus = document.getElementById("statApiKeyStatus");
  const statApiKeyMasked = document.getElementById("statApiKeyMasked");

  // DOM Elements - Upload & Staging
  const adminDropzone = document.getElementById("adminDropzone");
  const drugFileInput = document.getElementById("drugFileInput");
  const fileSelectedBar = document.getElementById("fileSelectedBar");
  const selectedFileName = document.getElementById("selectedFileName");
  const selectedFileSize = document.getElementById("selectedFileSize");
  const startAnalysisBtn = document.getElementById("startAnalysisBtn");
  const analysisSpinner = document.getElementById("analysisSpinner");
  const analysisBtnText = document.getElementById("analysisBtnText");
  const uploadErrorAlert = document.getElementById("uploadErrorAlert");

  const stagedDiffContainer = document.getElementById("stagedDiffContainer");
  const stagedFileNameSubtitle = document.getElementById("stagedFileNameSubtitle");
  const cancelStagingBtn = document.getElementById("cancelStagingBtn");
  const confirmCommitBtn = document.getElementById("confirmCommitBtn");

  const diffNewCount = document.getElementById("diffNewCount");
  const diffUpdatedCount = document.getElementById("diffUpdatedCount");
  const diffDuplicateCount = document.getElementById("diffDuplicateCount");

  const cardFilterNew = document.getElementById("cardFilterNew");
  const cardFilterUpdated = document.getElementById("cardFilterUpdated");
  const cardFilterDuplicate = document.getElementById("cardFilterDuplicate");

  const pillViewNew = document.getElementById("pillViewNew");
  const pillViewUpdated = document.getElementById("pillViewUpdated");
  const pillViewDuplicate = document.getElementById("pillViewDuplicate");
  const pillCountNew = document.getElementById("pillCountNew");
  const pillCountUpdated = document.getElementById("pillCountUpdated");
  const pillCountDuplicate = document.getElementById("pillCountDuplicate");

  const stagedSearchInput = document.getElementById("stagedSearchInput");
  const stagedTableHead = document.getElementById("stagedTableHead");
  const stagedTableBody = document.getElementById("stagedTableBody");
  const paginationInfo = document.getElementById("paginationInfo");
  const prevPageBtn = document.getElementById("prevPageBtn");
  const nextPageBtn = document.getElementById("nextPageBtn");
  const pageIndicator = document.getElementById("pageIndicator");

  // Confirmation Modal
  const commitConfirmModal = document.getElementById("commitConfirmModal");
  const closeConfirmModalBtn = document.getElementById("closeConfirmModalBtn");
  const cancelModalBtn = document.getElementById("cancelModalBtn");
  const executeCommitBtn = document.getElementById("executeCommitBtn");
  const modalNewCount = document.getElementById("modalNewCount");
  const modalUpdatedCount = document.getElementById("modalUpdatedCount");
  const modalDuplicateCount = document.getElementById("modalDuplicateCount");
  const commitSpinner = document.getElementById("commitSpinner");
  const commitBtnText = document.getElementById("commitBtnText");

  // API Key Tab Elements
  const currentMaskedKeyDisplay = document.getElementById("currentMaskedKeyDisplay");
  const updateApiKeyForm = document.getElementById("updateApiKeyForm");
  const newApiKeyInput = document.getElementById("newApiKeyInput");
  const toggleKeyVisibilityBtn = document.getElementById("toggleKeyVisibilityBtn");
  const apiKeySpinner = document.getElementById("apiKeySpinner");
  const saveApiKeyBtnText = document.getElementById("saveApiKeyBtnText");
  const apiKeySuccessAlert = document.getElementById("apiKeySuccessAlert");
  const apiKeyErrorAlert = document.getElementById("apiKeyErrorAlert");

  const adminToast = document.getElementById("adminToast");

  // Application State
  let adminToken = localStorage.getItem("gor3a_admin_token");
  let selectedFile = null;
  let currentStagingId = null;
  let currentActiveTab = "new"; // 'new', 'updated', 'duplicate'
  let currentPage = 1;
  let currentTotalPages = 1;
  let searchDebounceTimer = null;
  let currentStagingSummary = null;

  // Initialize
  checkAuth();
  setupEventListeners();

  // =========================================================================
  // 1. Authentication
  // =========================================================================
  async function checkAuth() {
    if (!adminToken) {
      showLoginView();
      return;
    }

    try {
      const res = await fetch("/api/admin/me", {
        headers: { Authorization: `Bearer ${adminToken}` },
      });
      if (!res.ok) throw new Error("Invalid token");
      const data = await res.json();
      showDashboardView(data.email);
      loadDashboardData();
    } catch (_) {
      localStorage.removeItem("gor3a_admin_token");
      adminToken = null;
      showLoginView();
    }
  }

  function showLoginView() {
    adminLoginView.classList.remove("hidden");
    adminDashboardView.classList.add("hidden");
    adminHeaderActions.style.display = "none";
  }

  function showDashboardView(email) {
    adminLoginView.classList.add("hidden");
    adminDashboardView.classList.remove("hidden");
    adminHeaderActions.style.display = "flex";
    adminUserEmail.textContent = email || "basemads20@gmail.com";
  }

  adminLoginForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    loginErrorAlert.classList.add("hidden");
    loginSpinner.classList.remove("hidden");
    loginBtnText.textContent = "جاري التحقق...";

    try {
      const res = await fetch("/api/admin/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          email: loginEmail.value.trim(),
          password: loginPassword.value,
        }),
      });

      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.detail || "البريد الإلكتروني أو كلمة المرور غير صحيحة");
      }

      const data = await res.json();
      adminToken = data.token;
      localStorage.setItem("gor3a_admin_token", adminToken);
      showDashboardView(data.email);
      loadDashboardData();
    } catch (err) {
      loginErrorAlert.textContent = err.message;
      loginErrorAlert.classList.remove("hidden");
    } finally {
      loginSpinner.classList.add("hidden");
      loginBtnText.textContent = "تسجيل الدخول إلى لوحة التحكم 🔒";
    }
  });

  adminLogoutBtn.addEventListener("click", () => {
    localStorage.removeItem("gor3a_admin_token");
    adminToken = null;
    showLoginView();
  });

  // =========================================================================
  // 2. Load Settings & Stats
  // =========================================================================
  async function loadDashboardData() {
    try {
      const res = await fetch("/api/admin/settings", {
        headers: { Authorization: `Bearer ${adminToken}` },
      });
      if (!res.ok) return;
      const data = await res.json();

      statTotalDrugsCount.textContent = (data.total_drugs || 0).toLocaleString();
      if (data.gemini_configured) {
        statApiKeyStatus.textContent = "نشط ومشفر 🔒";
        statApiKeyStatus.style.color = "var(--primary)";
        statApiKeyMasked.textContent = data.gemini_masked_key || "AIzaSy••••••••••••3xZ";
        currentMaskedKeyDisplay.textContent = data.gemini_masked_key || "AIzaSy••••••••••••3xZ";
      } else {
        statApiKeyStatus.textContent = "غير مضبوط ⚠️";
        statApiKeyStatus.style.color = "var(--danger-solid)";
        statApiKeyMasked.textContent = "مطلوب إضافة المفتاح";
        currentMaskedKeyDisplay.textContent = "لم يتم حفظ أي مفتاح حتى الآن";
      }
    } catch (err) {
      console.warn("Failed to load settings:", err);
    }
    // Also load banners and users stats and data
    loadBannersData();
    loadUsersData();
  }

  // =========================================================================
  // 3. Tab Switching
  // =========================================================================
  document.querySelectorAll(".admin-tab-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      document.querySelectorAll(".admin-tab-btn").forEach((b) => b.classList.remove("active"));
      document.querySelectorAll(".admin-tab-content").forEach((c) => c.classList.add("hidden"));

      btn.classList.add("active");
      const targetId = btn.dataset.tab;
      const targetContent = document.getElementById(targetId);
      if (targetContent) targetContent.classList.remove("hidden");
      if (targetId === "tabBanners") {
        loadBannersData();
      }
      if (targetId === "tabUsers") {
        loadUsersData();
      }
    });
  });

  // =========================================================================
  // 4. Encrypted Gemini API Key Management
  // =========================================================================
  toggleKeyVisibilityBtn.addEventListener("click", () => {
    if (newApiKeyInput.type === "password") {
      newApiKeyInput.type = "text";
      toggleKeyVisibilityBtn.textContent = "🔒";
    } else {
      newApiKeyInput.type = "password";
      toggleKeyVisibilityBtn.textContent = "👁️";
    }
  });

  updateApiKeyForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    apiKeySuccessAlert.classList.add("hidden");
    apiKeyErrorAlert.classList.add("hidden");
    apiKeySpinner.classList.remove("hidden");
    saveApiKeyBtnText.textContent = "جاري التشفير والحفظ...";

    try {
      const res = await fetch("/api/admin/settings/api-key", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${adminToken}`,
        },
        body: JSON.stringify({ api_key: newApiKeyInput.value.trim() }),
      });

      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.detail || "فشل تحديث المفتاح");
      }

      const data = await res.json();
      currentMaskedKeyDisplay.textContent = data.masked_key;
      statApiKeyMasked.textContent = data.masked_key;
      statApiKeyStatus.textContent = "نشط ومشفر 🔒";
      statApiKeyStatus.style.color = "var(--primary)";

      newApiKeyInput.value = "";
      newApiKeyInput.type = "password";
      toggleKeyVisibilityBtn.textContent = "👁️";

      apiKeySuccessAlert.textContent = data.message;
      apiKeySuccessAlert.classList.remove("hidden");
      showToast("تم تحديث مفتاح Gemini API وتشفيره بنجاح ✓");
    } catch (err) {
      apiKeyErrorAlert.textContent = err.message;
      apiKeyErrorAlert.classList.remove("hidden");
    } finally {
      apiKeySpinner.classList.add("hidden");
      saveApiKeyBtnText.textContent = "💾 حفظ وتشفير المفتاح الجديد";
    }
  });

  // =========================================================================
  // 5. Drug File Upload & Staging Diff Analysis
  // =========================================================================
  function setupEventListeners() {
    // Drag & Drop
    ["dragenter", "dragover"].forEach((evt) => {
      adminDropzone.addEventListener(evt, (e) => {
        e.preventDefault();
        adminDropzone.classList.add("dragover");
      });
    });

    ["dragleave", "drop"].forEach((evt) => {
      adminDropzone.addEventListener(evt, (e) => {
        e.preventDefault();
        adminDropzone.classList.remove("dragover");
      });
    });

    adminDropzone.addEventListener("drop", (e) => {
      if (e.dataTransfer.files && e.dataTransfer.files[0]) {
        handleFilePick(e.dataTransfer.files[0]);
      }
    });

    drugFileInput.addEventListener("change", (e) => {
      if (e.target.files && e.target.files[0]) {
        handleFilePick(e.target.files[0]);
      }
    });

    startAnalysisBtn.addEventListener("click", runDiffAnalysis);
    cancelStagingBtn.addEventListener("click", resetStaging);

    // Staging filter tabs
    [cardFilterNew, pillViewNew].forEach(el => el.addEventListener("click", () => switchStagingTab("new")));
    [cardFilterUpdated, pillViewUpdated].forEach(el => el.addEventListener("click", () => switchStagingTab("updated")));
    [cardFilterDuplicate, pillViewDuplicate].forEach(el => el.addEventListener("click", () => switchStagingTab("duplicate")));

    // Search in staged items
    stagedSearchInput.addEventListener("input", () => {
      clearTimeout(searchDebounceTimer);
      searchDebounceTimer = setTimeout(() => {
        currentPage = 1;
        loadStagedItems();
      }, 300);
    });

    // Pagination
    prevPageBtn.addEventListener("click", () => {
      if (currentPage > 1) {
        currentPage--;
        loadStagedItems();
      }
    });

    nextPageBtn.addEventListener("click", () => {
      if (currentPage < currentTotalPages) {
        currentPage++;
        loadStagedItems();
      }
    });

    // Confirmation Modal
    confirmCommitBtn.addEventListener("click", () => {
      if (!currentStagingSummary) return;
      modalNewCount.textContent = (currentStagingSummary.new_count || 0).toLocaleString();
      modalUpdatedCount.textContent = (currentStagingSummary.updated_count || 0).toLocaleString();
      modalDuplicateCount.textContent = (currentStagingSummary.duplicate_count || 0).toLocaleString();
      commitConfirmModal.classList.remove("hidden");
    });

    closeConfirmModalBtn.addEventListener("click", () => commitConfirmModal.classList.add("hidden"));
    cancelModalBtn.addEventListener("click", () => commitConfirmModal.classList.add("hidden"));
    executeCommitBtn.addEventListener("click", executeCommitImport);

    // Backup & Export downloads
    const downloadCsvBtn = document.getElementById("downloadCsvBtn");
    const downloadJsonBtn = document.getElementById("downloadJsonBtn");
    if (downloadCsvBtn) {
      downloadCsvBtn.addEventListener("click", (e) => {
        e.preventDefault();
        downloadExport("csv");
      });
    }
    if (downloadJsonBtn) {
      downloadJsonBtn.addEventListener("click", (e) => {
        e.preventDefault();
        downloadExport("json");
      });
    }
  }

  async function downloadExport(format) {
    if (!adminToken) return;
    try {
      showToast(`جاري تصدير قاعدة الأدوية بتنسيق ${format.toUpperCase()}...`);
      const res = await fetch(`/api/admin/drugs/export?format=${format}&token=${encodeURIComponent(adminToken)}`, {
        headers: { Authorization: `Bearer ${adminToken}` },
      });
      if (!res.ok) throw new Error("فشل تصدير الملف من السيرفر");
      const blob = await res.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `gor3a_egyptian_drugs_${format}_export.${format}`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(url);
      showToast(`تم تحميل ملف ${format.toUpperCase()} بنجاح ✓`);
    } catch (err) {
      alert("حدث خطأ أثناء تحميل الملف: " + err.message);
    }
  }

  function handleFilePick(file) {
    uploadErrorAlert.classList.add("hidden");
    selectedFile = file;
    selectedFileName.textContent = file.name;
    selectedFileSize.textContent = `${(file.size / 1024).toFixed(1)} KB`;
    fileSelectedBar.classList.remove("hidden");
  }

  async function runDiffAnalysis() {
    if (!selectedFile) return;

    uploadErrorAlert.classList.add("hidden");
    analysisSpinner.classList.remove("hidden");
    analysisBtnText.textContent = "جاري فحص ومقارنة الملف مع 25k دواء...";
    startAnalysisBtn.disabled = true;

    const formData = new FormData();
    formData.append("file", selectedFile);

    try {
      const res = await fetch("/api/admin/drugs/stage-import", {
        method: "POST",
        headers: { Authorization: `Bearer ${adminToken}` },
        body: formData,
      });

      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.detail || "فشل تحليل الملف");
      }

      const data = await res.json();
      currentStagingId = data.staging_id;
      currentStagingSummary = data.summary;
      stagedFileNameSubtitle.textContent = `ملف: ${escapeHtml(data.filename)} (${data.summary.total_records.toLocaleString()} سجل)`;

      // Render ribbon counts
      diffNewCount.textContent = (data.summary.new_count || 0).toLocaleString();
      diffUpdatedCount.textContent = (data.summary.updated_count || 0).toLocaleString();
      diffDuplicateCount.textContent = (data.summary.duplicate_count || 0).toLocaleString();

      pillCountNew.textContent = (data.summary.new_count || 0).toLocaleString();
      pillCountUpdated.textContent = (data.summary.updated_count || 0).toLocaleString();
      pillCountDuplicate.textContent = (data.summary.duplicate_count || 0).toLocaleString();

      stagedDiffContainer.classList.remove("hidden");
      switchStagingTab(data.summary.new_count > 0 ? "new" : (data.summary.updated_count > 0 ? "updated" : "duplicate"));
      stagedDiffContainer.scrollIntoView({ behavior: "smooth" });
    } catch (err) {
      uploadErrorAlert.textContent = err.message;
      uploadErrorAlert.classList.remove("hidden");
    } finally {
      analysisSpinner.classList.add("hidden");
      analysisBtnText.textContent = "🔍 فحص وتحليل الفروقات (Analyze Diff)";
      startAnalysisBtn.disabled = false;
    }
  }

  function resetStaging() {
    currentStagingId = null;
    currentStagingSummary = null;
    stagedDiffContainer.classList.add("hidden");
    fileSelectedBar.classList.add("hidden");
    selectedFile = null;
    drugFileInput.value = "";
  }

  function switchStagingTab(type) {
    currentActiveTab = type;
    currentPage = 1;

    [cardFilterNew, cardFilterUpdated, cardFilterDuplicate].forEach(c => c.classList.remove("active"));
    [pillViewNew, pillViewUpdated, pillViewDuplicate].forEach(p => p.classList.remove("active"));

    if (type === "new") {
      cardFilterNew.classList.add("active");
      pillViewNew.classList.add("active");
    } else if (type === "updated") {
      cardFilterUpdated.classList.add("active");
      pillViewUpdated.classList.add("active");
    } else if (type === "duplicate") {
      cardFilterDuplicate.classList.add("active");
      pillViewDuplicate.classList.add("active");
    }

    loadStagedItems();
  }

  async function loadStagedItems() {
    if (!currentStagingId) return;

    stagedTableBody.innerHTML = `
      <tr>
        <td colspan="7" style="text-align: center; padding: 2rem;">
          <div class="spinner" style="margin: 0 auto;"></div>
          <p style="margin-top: 0.5rem; color: var(--text-muted);">جاري تحميل القائمة...</p>
        </td>
      </tr>
    `;

    const searchQuery = (stagedSearchInput.value || "").trim();
    let url = `/api/admin/drugs/staging/${encodeURIComponent(currentStagingId)}?type=${encodeURIComponent(currentActiveTab)}&page=${currentPage}&limit=50`;
    if (searchQuery) {
      url += `&search=${encodeURIComponent(searchQuery)}`;
    }

    try {
      const res = await fetch(url, {
        headers: { Authorization: `Bearer ${adminToken}` },
      });
      if (!res.ok) throw new Error("Could not fetch items");
      const data = await res.json();

      currentTotalPages = data.total_pages || 1;
      pageIndicator.textContent = `${data.page} / ${currentTotalPages}`;
      paginationInfo.textContent = `عرض ${(data.items || []).length} من إجمالي ${data.total.toLocaleString()} صنف`;

      prevPageBtn.disabled = data.page <= 1;
      nextPageBtn.disabled = data.page >= currentTotalPages;

      renderStagedTable(data.items || []);
    } catch (err) {
      stagedTableBody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--danger-solid);">خطأ: ${escapeHtml(err.message)}</td></tr>`;
    }
  }

  function renderStagedTable(items) {
    stagedTableBody.innerHTML = "";

    if (currentActiveTab === "new") {
      stagedTableHead.innerHTML = `
        <th>#</th>
        <th>الكود المقترح</th>
        <th>الاسم التجاري (Commercial)</th>
        <th>المادة الفعالة (Generic)</th>
        <th>الشكل والتركيز</th>
        <th>السعر المقترح</th>
        <th>الشركة المصنعة</th>
      `;

      if (items.length === 0) {
        stagedTableBody.innerHTML = `<tr><td colspan="7" style="text-align: center; padding: 2rem; color: var(--text-muted);">لا توجد أدوية جديدة مطابقة للبحث.</td></tr>`;
        return;
      }

      items.forEach((item, idx) => {
        const row = document.createElement("tr");
        const priceStr = item.price_egp != null ? `${item.price_egp.toFixed(2)} EGP` : "--";
        row.innerHTML = `
          <td>${(currentPage - 1) * 50 + idx + 1}</td>
          <td><span class="badge badge-primary font-mono">${escapeHtml(item.id)}</span></td>
          <td>
            <strong>${escapeHtml(item.commercial_name_en)}</strong>
            ${item.commercial_name_ar ? `<br><small class="text-muted">(${escapeHtml(item.commercial_name_ar)})</small>` : ""}
          </td>
          <td>${escapeHtml(item.generic_name || "Unspecified")}</td>
          <td>${escapeHtml(item.dosage_form || "")} ${item.strength ? `• ${escapeHtml(item.strength)}` : ""}</td>
          <td><strong style="color: #059669;">${escapeHtml(priceStr)}</strong></td>
          <td class="text-muted">${escapeHtml(item.manufacturer || "--")}</td>
        `;
        stagedTableBody.appendChild(row);
      });

    } else if (currentActiveTab === "updated") {
      stagedTableHead.innerHTML = `
        <th>#</th>
        <th>كود الدواء الحالي</th>
        <th>اسم الدواء المسجل</th>
        <th>التعديلات والفروقات المكتشفة</th>
        <th>الشكل الصيدلي</th>
      `;

      if (items.length === 0) {
        stagedTableBody.innerHTML = `<tr><td colspan="5" style="text-align: center; padding: 2rem; color: var(--text-muted);">لا توجد أدوية للتحديث مطابقة للبحث.</td></tr>`;
        return;
      }

      items.forEach((item, idx) => {
        const row = document.createElement("tr");
        const diffsHtml = (item.changes || []).map(ch => `
          <div class="diff-change-badge">
            <span>${escapeHtml(ch.label)}:</span>
            <span style="text-decoration: line-through; opacity: 0.7;">${escapeHtml(String(ch.old))}</span>
            <span class="diff-arrow">➔</span>
            <strong>${escapeHtml(String(ch.new))}</strong>
          </div>
        `).join("");

        row.innerHTML = `
          <td>${(currentPage - 1) * 50 + idx + 1}</td>
          <td><span class="badge badge-primary font-mono">${escapeHtml(item.existing_id)}</span></td>
          <td>
            <strong>${escapeHtml(item.commercial_name_en)}</strong>
            <br><small class="text-muted">${escapeHtml(item.full_commercial_name_en || "")}</small>
          </td>
          <td>${diffsHtml}</td>
          <td>${escapeHtml(item.dosage_form || "")}</td>
        `;
        stagedTableBody.appendChild(row);
      });

    } else if (currentActiveTab === "duplicate") {
      stagedTableHead.innerHTML = `
        <th>#</th>
        <th>كود الدواء بالقاعدة</th>
        <th>اسم الدواء</th>
        <th>الشكل والتركيز</th>
        <th>السعر الحالي</th>
        <th>حالة التطابق</th>
      `;

      if (items.length === 0) {
        stagedTableBody.innerHTML = `<tr><td colspan="6" style="text-align: center; padding: 2rem; color: var(--text-muted);">لا توجد أصناف متطابقة مطابقة للبحث.</td></tr>`;
        return;
      }

      items.forEach((item, idx) => {
        const row = document.createElement("tr");
        const priceStr = item.price_egp != null ? `${item.price_egp.toFixed(2)} EGP` : "--";
        row.innerHTML = `
          <td>${(currentPage - 1) * 50 + idx + 1}</td>
          <td><span class="badge badge-neutral font-mono">${escapeHtml(item.existing_id)}</span></td>
          <td><strong>${escapeHtml(item.commercial_name_en)}</strong></td>
          <td>${escapeHtml(item.dosage_form || "")} • ${escapeHtml(item.strength || "")}</td>
          <td>${escapeHtml(priceStr)}</td>
          <td><span class="badge badge-neutral">متطابق 100% (تم تخطيه لمنع التكرار)</span></td>
        `;
        stagedTableBody.appendChild(row);
      });
    }
  }

  // =========================================================================
  // 6. Confirm & Commit Import
  // =========================================================================
  async function executeCommitImport() {
    if (!currentStagingId) return;

    commitConfirmModal.classList.add("hidden");
    commitSpinner.classList.remove("hidden");
    commitBtnText.textContent = "جاري تطبيق التحديثات وإعادة الفهرسة...";
    confirmCommitBtn.disabled = true;

    try {
      const res = await fetch("/api/admin/drugs/confirm-import", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${adminToken}`,
        },
        body: JSON.stringify({
          staging_id: currentStagingId,
          import_new: true,
          apply_updates: true,
        }),
      });

      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.detail || "فشل تطبيق التحديث");
      }

      const data = await res.json();
      showToast(data.message || "تم تحديث وحفظ قاعدة الأدوية بنجاح ✓");
      statTotalDrugsCount.textContent = (data.total_drugs || 0).toLocaleString();

      alert(`✅ نجاح العملية:\n• أدوية جديدة أضيفت: ${data.added_count}\n• أدوية تم تحديث بياناتها: ${data.updated_count}\n• إجمالي الأدوية المسجلة بالقاعدة حالياً: ${data.total_drugs}`);
      resetStaging();
    } catch (err) {
      alert(`حدث خطأ أثناء تطبيق التحديث: ${err.message}`);
    } finally {
      commitSpinner.classList.add("hidden");
      commitBtnText.textContent = "✅ تأكيد وتطبيق التحديث (Confirm Changes)";
      confirmCommitBtn.disabled = false;
    }
  }

  // =========================================================================
  // 6. Banner & Slider Management Logic
  // =========================================================================
  let bannersList = [];
  let governoratesList = [];
  let currentBannerFilter = "all";
  let deletingBannerId = null;

  // DOM Elements - Banners
  const statActiveBannersCount = document.getElementById("statActiveBannersCount");
  const statBannersSummary = document.getElementById("statBannersSummary");
  const statBannerTotal = document.getElementById("statBannerTotal");
  const statBannerActive = document.getElementById("statBannerActive");
  const statBannerInactive = document.getElementById("statBannerInactive");
  const statBannerExpired = document.getElementById("statBannerExpired");

  const addNewBannerBtn = document.getElementById("addNewBannerBtn");
  const emptyAddBannerBtn = document.getElementById("emptyAddBannerBtn");
  const bannersTableBody = document.getElementById("bannersTableBody");
  const bannersEmptyState = document.getElementById("bannersEmptyState");
  const bannerFilterPills = document.querySelectorAll("[data-banner-filter]");

  // Modal Elements
  const bannerModal = document.getElementById("bannerModal");
  const bannerModalTitle = document.getElementById("bannerModalTitle");
  const closeBannerModalBtn = document.getElementById("closeBannerModalBtn");
  const cancelBannerModalBtn = document.getElementById("cancelBannerModalBtn");
  const bannerForm = document.getElementById("bannerForm");
  const bannerFormError = document.getElementById("bannerFormError");
  const saveBannerSubmitBtn = document.getElementById("saveBannerSubmitBtn");
  const saveBannerSpinner = document.getElementById("saveBannerSpinner");
  const saveBannerBtnText = document.getElementById("saveBannerBtnText");

  // Form Fields
  const bannerIdInput = document.getElementById("bannerId");
  const bannerTitleInput = document.getElementById("bannerTitleInput");
  const bannerSubtitleInput = document.getElementById("bannerSubtitleInput");
  const bannerImageUrlInput = document.getElementById("bannerImageUrlInput");
  const bannerImageFileInput = document.getElementById("bannerImageFileInput");
  const bannerImagePreview = document.getElementById("bannerImagePreview");
  const bannerImagePreviewPlaceholder = document.getElementById("bannerImagePreviewPlaceholder");

  const targetTypeAll = document.getElementById("targetTypeAll");
  const targetTypeSpecific = document.getElementById("targetTypeSpecific");
  const specificGovsSection = document.getElementById("specificGovsSection");
  const governoratesCheckboxes = document.getElementById("governoratesCheckboxes");
  const selectAllGovsBtn = document.getElementById("selectAllGovsBtn");
  const clearAllGovsBtn = document.getElementById("clearAllGovsBtn");

  const bannerStartDate = document.getElementById("bannerStartDate");
  const bannerEndDate = document.getElementById("bannerEndDate");
  const durationPresetBtns = document.querySelectorAll(".duration-preset-btn");

  const bannerActionType = document.getElementById("bannerActionType");
  const bannerActionTarget = document.getElementById("bannerActionTarget");
  const actionTargetLabel = document.getElementById("actionTargetLabel");
  const bannerDisplayOrder = document.getElementById("bannerDisplayOrder");
  const bannerIsActive = document.getElementById("bannerIsActive");

  // Delete Modal Elements
  const deleteBannerModal = document.getElementById("deleteBannerModal");
  const closeDeleteBannerModalBtn = document.getElementById("closeDeleteBannerModalBtn");
  const cancelDeleteBannerBtn = document.getElementById("cancelDeleteBannerBtn");
  const confirmDeleteBannerBtn = document.getElementById("confirmDeleteBannerBtn");
  const deleteBannerTitlePreview = document.getElementById("deleteBannerTitlePreview");

  // Date Helpers
  function getTodayIso() {
    const d = new Date();
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  function addDaysToDate(dateStr, days) {
    const d = new Date(dateStr || getTodayIso());
    d.setDate(d.getDate() + parseInt(days, 10));
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  // Local Storage & Serverless Sync Helpers
  function getDeletedBannerIds() {
    try {
      return JSON.parse(localStorage.getItem("gor3a_deleted_banner_ids") || "[]");
    } catch (_) {
      return [];
    }
  }

  function addDeletedBannerId(id) {
    const ids = getDeletedBannerIds();
    if (!ids.includes(id)) {
      ids.push(id);
      localStorage.setItem("gor3a_deleted_banner_ids", JSON.stringify(ids));
    }
  }

  function getLocalBanners() {
    try {
      const data = localStorage.getItem("gor3a_local_banners");
      return data ? JSON.parse(data) : null;
    } catch (_) {
      return null;
    }
  }

  function saveLocalBanners(list) {
    try {
      localStorage.setItem("gor3a_local_banners", JSON.stringify(list));
    } catch (_) {}
  }

  async function syncBannersToServer(list) {
    if (!adminToken) return;
    try {
      await fetch("/api/admin/banners/sync", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${adminToken}`,
        },
        body: JSON.stringify(list),
      });
    } catch (err) {
      console.warn("Sync banners failed:", err);
    }
  }

  function updateBannerStats() {
    const today = getTodayIso();
    let active = 0, inactive = 0, expired = 0;
    bannersList.forEach((b) => {
      if (!b.is_active) {
        inactive++;
      } else if (b.end_date && b.end_date.trim() && today > b.end_date.trim()) {
        expired++;
      } else {
        active++;
      }
    });

    const total = bannersList.length;
    if (statActiveBannersCount) statActiveBannersCount.textContent = active;
    if (statBannersSummary) statBannersSummary.textContent = `${active} إعلانات نشطة من إجمالي ${total}`;
    if (statBannerTotal) statBannerTotal.textContent = total;
    if (statBannerActive) statBannerActive.textContent = active;
    if (statBannerInactive) statBannerInactive.textContent = inactive;
    if (statBannerExpired) statBannerExpired.textContent = expired;
  }

  // Load Banners from Server
  async function loadBannersData() {
    if (!adminToken) return;
    try {
      const res = await fetch("/api/admin/banners", {
        headers: { Authorization: `Bearer ${adminToken}` },
      });
      if (!res.ok) return;
      const data = await res.json();
      const serverBanners = data.banners || [];
      const deletedIds = new Set(getDeletedBannerIds());

      // Filter out any banner explicitly deleted in this browser
      const validServerBanners = serverBanners.filter((b) => !deletedIds.has(b.id));

      const localBanners = getLocalBanners();
      if (Array.isArray(localBanners)) {
        const localMap = new Map(localBanners.map((b) => [b.id, b]));
        bannersList = localBanners.filter((b) => !deletedIds.has(b.id));
        validServerBanners.forEach((sb) => {
          if (!localMap.has(sb.id) && !deletedIds.has(sb.id)) {
            bannersList.push(sb);
          }
        });
      } else {
        bannersList = validServerBanners;
      }

      saveLocalBanners(bannersList);
      updateBannerStats();

      // Populate Governorates
      if (data.governorates && data.governorates.length) {
        populateGovernoratesCheckboxes(data.governorates);
      }

      renderBannersTable();
      syncBannersToServer(bannersList);
    } catch (err) {
      console.warn("Failed to load banners data:", err);
    }
  }

  // Populate Governorates Checkbox Grid
  function populateGovernoratesCheckboxes(govs) {
    governoratesList = govs || [];
    if (!governoratesCheckboxes) return;
    governoratesCheckboxes.innerHTML = "";
    governoratesList.forEach((gov) => {
      const label = document.createElement("label");
      label.className = "gov-checkbox-item";
      label.innerHTML = `
        <input type="checkbox" name="gov_item" value="${escapeHtml(gov)}" style="accent-color: #00897b;">
        <span>${escapeHtml(gov)}</span>
      `;
      governoratesCheckboxes.appendChild(label);
    });
  }

  // Render Banners Table
  function renderBannersTable() {
    if (!bannersTableBody) return;
    bannersTableBody.innerHTML = "";

    const filtered = bannersList.filter((b) => {
      if (currentBannerFilter === "active") return b.is_active && !b.is_expired && !b.is_pending;
      if (currentBannerFilter === "inactive") return !b.is_active;
      if (currentBannerFilter === "expired") return b.is_expired;
      return true;
    });

    if (filtered.length === 0) {
      if (bannersEmptyState) bannersEmptyState.classList.remove("hidden");
      bannersTableBody.parentElement.classList.add("hidden");
      return;
    }

    if (bannersEmptyState) bannersEmptyState.classList.add("hidden");
    bannersTableBody.parentElement.classList.remove("hidden");

    filtered.forEach((banner, idx) => {
      const tr = document.createElement("tr");

      // Action description tag
      let actionTagHtml = "";
      if (banner.action_type === "whatsapp") {
        actionTagHtml = `<span class="banner-action-tag">🟢 واتساب: ${escapeHtml(banner.action_target || "شات فوري")}</span>`;
      } else if (banner.action_type === "url") {
        actionTagHtml = `<span class="banner-action-tag" style="background:#eff6ff;color:#1d4ed8;border-color:#bfdbfe;">🌐 رابط: ${escapeHtml(banner.action_target || "موقع خارجي")}</span>`;
      } else if (banner.action_type === "phone") {
        actionTagHtml = `<span class="banner-action-tag" style="background:#fefce8;color:#a16207;border-color:#fef08a;">📞 اتصال: ${escapeHtml(banner.action_target || "خط ساخن")}</span>`;
      } else if (banner.action_type === "drug") {
        actionTagHtml = `<span class="banner-action-tag" style="background:#fdf2f8;color:#be185d;border-color:#fbcfe8;">💊 دواء: ${escapeHtml(banner.action_target || "دليل الأدوية")}</span>`;
      }

      // Targeting badge
      let targetHtml = "";
      if (banner.target_type === "all") {
        targetHtml = `<span class="banner-gov-badge all">جميع المحافظات 🇪🇬</span>`;
      } else {
        const govs = banner.target_governorates || [];
        const pills = govs.slice(0, 3).map((g) => `<span class="gov-pill-tag">${escapeHtml(g)}</span>`).join("");
        const moreCount = govs.length > 3 ? `<span class="gov-pill-tag">+${govs.length - 3}</span>` : "";
        targetHtml = `
          <div>
            <span class="banner-gov-badge specific">محافظات محددة (${govs.length})</span>
            <div style="margin-top: 0.25rem;">${pills}${moreCount}</div>
          </div>
        `;
      }

      // Duration & Expiry text
      let durationHtml = "";
      const startStr = banner.start_date || "-";
      const endStr = banner.end_date || null;
      if (endStr) {
        durationHtml = `
          <div style="font-size:0.8rem;">
            <div>من: <strong>${escapeHtml(startStr)}</strong></div>
            <div>إلى: <strong>${escapeHtml(endStr)}</strong></div>
            ${banner.is_expired ? '<span style="color:#ef4444;font-size:0.75rem;font-weight:700;">⚠️ منتهي الصلاحية</span>' : ''}
          </div>
        `;
      } else {
        durationHtml = `
          <div style="font-size:0.8rem;">
            <div>بدأ: <strong>${escapeHtml(startStr)}</strong></div>
            <span style="color:#00897b;font-size:0.75rem;font-weight:600;">∞ مستمر بدون تاريخ انتهاء</span>
          </div>
        `;
      }

      // Status Badge
      const statusBadge = `
        <button type="button" class="status-badge ${banner.status_color || 'gray'}" style="cursor: pointer; border: none;" onclick="window.toggleBannerActive('${banner.id}')" title="اضغط للتفعيل أو الإلغاء">
          <span style="display:inline-block;width:6px;height:6px;border-radius:50%;background:currentColor;"></span>
          ${escapeHtml(banner.status_label || (banner.is_active ? 'نشط' : 'معطل'))}
        </button>
      `;

      // Order buttons
      const isFirst = idx === 0;
      const isLast = idx === filtered.length - 1;
      const orderHtml = `
        <div class="order-controls">
          <button type="button" class="btn-order" onclick="window.moveBannerOrder('${banner.id}', 'up')" ${isFirst ? 'disabled' : ''} title="تحريك لأعلى">⬆️</button>
          <span class="order-number">${banner.display_order || (idx + 1)}</span>
          <button type="button" class="btn-order" onclick="window.moveBannerOrder('${banner.id}', 'down')" ${isLast ? 'disabled' : ''} title="تحريك لأسفل">⬇️</button>
        </div>
      `;

      tr.innerHTML = `
        <td style="text-align: center;">${orderHtml}</td>
        <td class="banner-thumb-cell">
          <img src="${escapeHtml(banner.image_url)}" alt="${escapeHtml(banner.title)}" class="banner-thumb-img" onerror="this.src='/static/logo.png'">
        </td>
        <td>
          <div class="banner-title-text">${escapeHtml(banner.title)}</div>
          ${banner.subtitle ? `<div class="banner-sub-text">${escapeHtml(banner.subtitle)}</div>` : ''}
          ${actionTagHtml}
        </td>
        <td>${targetHtml}</td>
        <td>${durationHtml}</td>
        <td style="text-align: center;">${statusBadge}</td>
        <td style="text-align: center;">
          <div style="display:flex;gap:0.35rem;justify-content:center;">
            <button type="button" class="btn btn-secondary btn-xs" onclick="window.openEditBannerModal('${banner.id}')" title="تعديل">✏️ تعديل</button>
            <button type="button" class="btn btn-xs" style="background:#fee2e2;color:#991b1b;border:1px solid #fecaca;" onclick="window.openDeleteBannerModal('${banner.id}')" title="حذف">🗑️</button>
          </div>
        </td>
      `;

      bannersTableBody.appendChild(tr);
    });
  }

  // Filter Pills Event Listeners
  bannerFilterPills.forEach((pill) => {
    pill.addEventListener("click", () => {
      bannerFilterPills.forEach((p) => p.classList.remove("active"));
      pill.classList.add("active");
      currentBannerFilter = pill.dataset.bannerFilter || "all";
      renderBannersTable();
    });
  });

  // Image Preview Logic
  function updateImagePreview(src) {
    if (src && src.trim()) {
      bannerImagePreview.src = src.trim();
      bannerImagePreview.style.display = "block";
      if (bannerImagePreviewPlaceholder) bannerImagePreviewPlaceholder.style.display = "none";
    } else {
      bannerImagePreview.src = "";
      bannerImagePreview.style.display = "none";
      if (bannerImagePreviewPlaceholder) bannerImagePreviewPlaceholder.style.display = "block";
    }
  }

  if (bannerImageUrlInput) {
    bannerImageUrlInput.addEventListener("input", () => {
      updateImagePreview(bannerImageUrlInput.value);
    });
  }

  if (bannerImageFileInput) {
    bannerImageFileInput.addEventListener("change", (e) => {
      const file = e.target.files && e.target.files[0];
      if (!file) return;
      const reader = new FileReader();
      reader.onload = (event) => {
        const dataUrl = event.target.result;
        bannerImageUrlInput.value = dataUrl;
        updateImagePreview(dataUrl);
      };
      reader.readAsDataURL(file);
    });
  }

  // Targeting Radio Switch
  if (targetTypeAll && targetTypeSpecific && specificGovsSection) {
    targetTypeAll.addEventListener("change", () => {
      if (targetTypeAll.checked) {
        specificGovsSection.classList.add("hidden");
      }
    });
    targetTypeSpecific.addEventListener("change", () => {
      if (targetTypeSpecific.checked) {
        specificGovsSection.classList.remove("hidden");
      }
    });
  }

  // Select / Clear all governorates
  if (selectAllGovsBtn) {
    selectAllGovsBtn.addEventListener("click", () => {
      document.querySelectorAll("#governoratesCheckboxes input[name='gov_item']").forEach((cb) => {
        cb.checked = true;
      });
    });
  }

  if (clearAllGovsBtn) {
    clearAllGovsBtn.addEventListener("click", () => {
      document.querySelectorAll("#governoratesCheckboxes input[name='gov_item']").forEach((cb) => {
        cb.checked = false;
      });
    });
  }

  // Duration Helper Presets
  durationPresetBtns.forEach((btn) => {
    btn.addEventListener("click", () => {
      const days = parseInt(btn.dataset.days, 10);
      const start = bannerStartDate.value || getTodayIso();
      if (!bannerStartDate.value) bannerStartDate.value = start;

      if (days === 0) {
        bannerEndDate.value = "";
      } else {
        bannerEndDate.value = addDaysToDate(start, days);
      }
    });
  });

  // Action Type Helper Labels
  if (bannerActionType) {
    bannerActionType.addEventListener("change", () => {
      const val = bannerActionType.value;
      if (val === "whatsapp") {
        actionTargetLabel.textContent = "رابط واتساب مع كود الخصم أو الرسالة:";
        bannerActionTarget.placeholder = "https://wa.me/201... أو رقم واتساب";
        bannerActionTarget.disabled = false;
      } else if (val === "url") {
        actionTargetLabel.textContent = "رابط الموقع الخارجي:";
        bannerActionTarget.placeholder = "https://example-pharmacy.com/offer";
        bannerActionTarget.disabled = false;
      } else if (val === "phone") {
        actionTargetLabel.textContent = "رقم الهاتف / الخط الساخن:";
        bannerActionTarget.placeholder = "19011 أو 010xxxxxxxx";
        bannerActionTarget.disabled = false;
      } else if (val === "drug") {
        actionTargetLabel.textContent = "معرف أو اسم الدواء لفتحه في الدليل:";
        bannerActionTarget.placeholder = "EDA-DB-00001 أو Panadol";
        bannerActionTarget.disabled = false;
      } else {
        actionTargetLabel.textContent = "الإجراء:";
        bannerActionTarget.placeholder = "غير مطلوب";
        bannerActionTarget.value = "";
        bannerActionTarget.disabled = true;
      }
    });
  }

  // Open Modal for Add
  function openAddBannerModal() {
    if (!bannerModal) return;
    bannerFormError.classList.add("hidden");
    bannerIdInput.value = "";
    bannerTitleInput.value = "";
    bannerSubtitleInput.value = "";
    bannerImageUrlInput.value = "";
    updateImagePreview("");
    targetTypeAll.checked = true;
    specificGovsSection.classList.add("hidden");
    document.querySelectorAll("#governoratesCheckboxes input[name='gov_item']").forEach((cb) => {
      cb.checked = false;
    });

    bannerStartDate.value = getTodayIso();
    // Default preset: 1 month duration (30 days)
    bannerEndDate.value = addDaysToDate(getTodayIso(), 30);

    bannerActionType.value = "whatsapp";
    bannerActionType.dispatchEvent(new Event("change"));
    bannerActionTarget.value = "";

    // Set order to next available
    const nextOrder = bannersList.length > 0 ? (Math.max(...bannersList.map((b) => b.display_order || 0)) + 1) : 1;
    bannerDisplayOrder.value = nextOrder;
    bannerIsActive.checked = true;

    bannerModalTitle.textContent = "➕ إضافة إعلان جديد للسلايدر";
    bannerModal.classList.remove("hidden");
  }

  // Open Modal for Edit
  window.openEditBannerModal = function(bannerId) {
    const banner = bannersList.find((b) => b.id === bannerId);
    if (!banner || !bannerModal) return;

    bannerFormError.classList.add("hidden");
    bannerIdInput.value = banner.id;
    bannerTitleInput.value = banner.title || "";
    bannerSubtitleInput.value = banner.subtitle || "";
    bannerImageUrlInput.value = banner.image_url || "";
    updateImagePreview(banner.image_url || "");

    if (banner.target_type === "specific") {
      targetTypeSpecific.checked = true;
      specificGovsSection.classList.remove("hidden");
      const selectedGovs = new Set(banner.target_governorates || []);
      document.querySelectorAll("#governoratesCheckboxes input[name='gov_item']").forEach((cb) => {
        cb.checked = selectedGovs.has(cb.value);
      });
    } else {
      targetTypeAll.checked = true;
      specificGovsSection.classList.add("hidden");
      document.querySelectorAll("#governoratesCheckboxes input[name='gov_item']").forEach((cb) => {
        cb.checked = false;
      });
    }

    bannerStartDate.value = banner.start_date || getTodayIso();
    bannerEndDate.value = banner.end_date || "";

    bannerActionType.value = banner.action_type || "none";
    bannerActionType.dispatchEvent(new Event("change"));
    bannerActionTarget.value = banner.action_target || "";

    bannerDisplayOrder.value = banner.display_order || 1;
    bannerIsActive.checked = Boolean(banner.is_active);

    bannerModalTitle.textContent = "✏️ تعديل بيانات الإعلان في السلايدر";
    bannerModal.classList.remove("hidden");
  };

  function closeBannerModal() {
    if (bannerModal) bannerModal.classList.add("hidden");
  }

  if (addNewBannerBtn) addNewBannerBtn.addEventListener("click", openAddBannerModal);
  if (emptyAddBannerBtn) emptyAddBannerBtn.addEventListener("click", openAddBannerModal);
  if (closeBannerModalBtn) closeBannerModalBtn.addEventListener("click", closeBannerModal);
  if (cancelBannerModalBtn) cancelBannerModalBtn.addEventListener("click", closeBannerModal);

  // Save Banner (Add or Edit) Form Submit
  if (bannerForm) {
    bannerForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      bannerFormError.classList.add("hidden");

      const title = bannerTitleInput.value.trim();
      const imageUrl = bannerImageUrlInput.value.trim();
      if (!title) {
        bannerFormError.textContent = "يرجى كتابة عنوان الإعلان";
        bannerFormError.classList.remove("hidden");
        return;
      }
      if (!imageUrl) {
        bannerFormError.textContent = "يرجى إدخال رابط صورة أو رفع صورة للإعلان";
        bannerFormError.classList.remove("hidden");
        return;
      }

      const targetType = targetTypeSpecific.checked ? "specific" : "all";
      let targetGovs = [];
      if (targetType === "specific") {
        document.querySelectorAll("#governoratesCheckboxes input[name='gov_item']:checked").forEach((cb) => {
          targetGovs.push(cb.value);
        });
        if (targetGovs.length === 0) {
          bannerFormError.textContent = "يرجى اختيار محافظة واحدة على الأقل للاستهداف المحدد";
          bannerFormError.classList.remove("hidden");
          return;
        }
      }

      const payload = {
        title: title,
        subtitle: bannerSubtitleInput.value.trim(),
        image_url: imageUrl,
        target_type: targetType,
        target_governorates: targetGovs,
        action_type: bannerActionType.value,
        action_target: bannerActionTarget.value.trim(),
        is_active: bannerIsActive.checked,
        start_date: bannerStartDate.value || getTodayIso(),
        end_date: bannerEndDate.value ? bannerEndDate.value : null,
        display_order: parseInt(bannerDisplayOrder.value, 10) || 1,
      };

      const bannerId = bannerIdInput.value;
      const isEdit = Boolean(bannerId);
      const url = isEdit ? `/api/admin/banners/${bannerId}` : "/api/admin/banners";
      const method = isEdit ? "PUT" : "POST";

      saveBannerSpinner.classList.remove("hidden");
      saveBannerBtnText.textContent = "جارِ الحفظ...";
      saveBannerSubmitBtn.disabled = true;

      try {
        const res = await fetch(url, {
          method: method,
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${adminToken}`,
          },
          body: JSON.stringify(payload),
        });

        if (!res.ok) {
          const err = await res.json().catch(() => ({}));
          throw new Error(err.detail || "فشل حفظ الإعلان");
        }

        const data = await res.json();
        const savedBanner = data.banner;

        if (isEdit) {
          const idx = bannersList.findIndex((b) => b.id === bannerId);
          if (idx >= 0) {
            bannersList[idx] = savedBanner || { ...bannersList[idx], ...payload, id: bannerId };
          }
        } else {
          if (savedBanner) {
            bannersList.push(savedBanner);
          } else {
            bannersList.push({ id: `banner-${Date.now()}`, ...payload });
          }
        }

        saveLocalBanners(bannersList);
        updateBannerStats();
        renderBannersTable();
        closeBannerModal();
        showToast(data.message || "تم حفظ الإعلان في السلايدر بنجاح ✓");

        // Sync with serverless backend
        await syncBannersToServer(bannersList);
      } catch (err) {
        bannerFormError.textContent = err.message || "حدث خطأ أثناء الحفظ";
        bannerFormError.classList.remove("hidden");
      } finally {
        saveBannerSpinner.classList.add("hidden");
        saveBannerBtnText.textContent = "حفظ الإعلان في السلايدر 💾";
        saveBannerSubmitBtn.disabled = false;
      }
    });
  }

  // Toggle Banner Active
  window.toggleBannerActive = async function(bannerId) {
    if (!adminToken) return;
    const banner = bannersList.find((b) => b.id === bannerId);
    if (!banner) return;

    // Toggle optimistically in local state
    banner.is_active = !banner.is_active;
    banner.status_label = banner.is_active ? "نشط" : "معطل";
    banner.status_color = banner.is_active ? "green" : "gray";

    saveLocalBanners(bannersList);
    updateBannerStats();
    renderBannersTable();
    showToast(banner.is_active ? "تم تفعيل الإعلان في السلايدر ✓" : "تم تعطيل الإعلان من السلايدر ✓");

    try {
      await fetch(`/api/admin/banners/${bannerId}/toggle`, {
        method: "PATCH",
        headers: { Authorization: `Bearer ${adminToken}` },
      });
      await syncBannersToServer(bannersList);
    } catch (err) {
      console.warn("Toggle sync warning:", err);
    }
  };

  // Move Banner Order Up / Down
  window.moveBannerOrder = async function(bannerId, direction) {
    const sorted = [...bannersList].sort((a, b) => (a.display_order || 999) - (b.display_order || 999));
    const idx = sorted.findIndex((b) => b.id === bannerId);
    if (idx < 0) return;
    const targetIdx = direction === "up" ? idx - 1 : idx + 1;
    if (targetIdx < 0 || targetIdx >= sorted.length) return;

    // Swap display_order
    const itemA = sorted[idx];
    const itemB = sorted[targetIdx];
    const tempOrder = itemA.display_order;
    itemA.display_order = itemB.display_order;
    itemB.display_order = tempOrder;

    sorted.sort((a, b) => (a.display_order || 999) - (b.display_order || 999));
    sorted.forEach((item, i) => { item.display_order = i + 1; });
    bannersList = sorted;

    saveLocalBanners(bannersList);
    renderBannersTable();
    showToast("تم تحديث ترتيب الإعلانات في السلايدر ✓");

    const orders = sorted.map((b, i) => ({ id: b.id, display_order: i + 1 }));
    try {
      await fetch("/api/admin/banners/reorder", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${adminToken}`,
        },
        body: JSON.stringify({ orders }),
      });
      await syncBannersToServer(bannersList);
    } catch (err) {
      console.warn("Reorder warning:", err);
    }
  };

  // Delete Banner Modal
  window.openDeleteBannerModal = function(bannerId) {
    const banner = bannersList.find((b) => b.id === bannerId);
    if (!banner || !deleteBannerModal) return;
    deletingBannerId = bannerId;
    deleteBannerTitlePreview.textContent = banner.title;
    deleteBannerModal.classList.remove("hidden");
  };

  function closeDeleteModal() {
    if (deleteBannerModal) deleteBannerModal.classList.add("hidden");
    deletingBannerId = null;
  }

  if (closeDeleteBannerModalBtn) closeDeleteBannerModalBtn.addEventListener("click", closeDeleteModal);
  if (cancelDeleteBannerBtn) cancelDeleteBannerBtn.addEventListener("click", closeDeleteModal);

  if (confirmDeleteBannerBtn) {
    confirmDeleteBannerBtn.addEventListener("click", async () => {
      if (!deletingBannerId) return;
      const targetId = deletingBannerId;

      // 1. Permanently record deletion in localStorage so it never reappears on reload
      addDeletedBannerId(targetId);

      // 2. Remove from bannersList in-memory
      bannersList = bannersList.filter((b) => b.id !== targetId);
      saveLocalBanners(bannersList);

      // 3. Update UI optimistically
      updateBannerStats();
      renderBannersTable();
      closeDeleteModal();
      showToast("تم حذف الإعلان بنجاح 🗑️");

      // 4. Send DELETE and SYNC to serverless backend
      try {
        await fetch(`/api/admin/banners/${targetId}`, {
          method: "DELETE",
          headers: { Authorization: `Bearer ${adminToken}` },
        });
        await syncBannersToServer(bannersList);
      } catch (err) {
        console.warn("Delete server warning:", err);
      }
    });
  }

  // =========================================================================
  // 7. Mobile App Users & Customers Management
  // =========================================================================
  let usersList = [];
  const statUsersCount = document.getElementById("statUsersCount");
  const statUsersSub = document.getElementById("statUsersSub");
  const statUsersTotal = document.getElementById("statUsersTotal");
  const statTopGov = document.getElementById("statTopGov");
  const statCloudRxsTotal = document.getElementById("statCloudRxsTotal");

  const usersTableBody = document.getElementById("usersTableBody");
  const usersEmptyState = document.getElementById("usersEmptyState");
  const userSearchInput = document.getElementById("userSearchInput");
  const refreshUsersBtn = document.getElementById("refreshUsersBtn");

  async function loadUsersData() {
    if (!adminToken) return;
    try {
      const res = await fetch("/api/admin/users", {
        headers: { Authorization: `Bearer ${adminToken}` },
      });
      if (!res.ok) return;
      const data = await res.json();
      usersList = data.users || [];
      const stats = data.stats || { total_users: 0, total_prescriptions: 0, top_governorate: "القاهرة" };

      // Update Ribbon Stats
      if (statUsersCount) statUsersCount.textContent = (stats.total_users || 0).toLocaleString();
      if (statUsersSub) statUsersSub.textContent = `${stats.total_users || 0} عملاء مسجلين`;

      // Update Mini Stats
      if (statUsersTotal) statUsersTotal.textContent = (stats.total_users || 0).toLocaleString();
      if (statTopGov) statTopGov.textContent = stats.top_governorate || "القاهرة";
      if (statCloudRxsTotal) statCloudRxsTotal.textContent = (stats.total_prescriptions || 0).toLocaleString();

      renderUsersTable(usersList);
    } catch (err) {
      console.warn("Failed to load users data:", err);
    }
  }

  function renderUsersTable(list) {
    if (!usersTableBody) return;
    usersTableBody.innerHTML = "";

    const query = (userSearchInput?.value || "").trim().toLowerCase();
    const filtered = list.filter((u) => {
      if (!query) return true;
      const matchName = (u.name || "").toLowerCase().includes(query);
      const matchPhone = (u.phone || "").toLowerCase().includes(query);
      const matchGov = (u.governorate || "").toLowerCase().includes(query);
      return matchName || matchPhone || matchGov;
    });

    if (filtered.length === 0) {
      if (usersEmptyState) usersEmptyState.classList.remove("hidden");
      usersTableBody.parentElement.classList.add("hidden");
      return;
    }

    if (usersEmptyState) usersEmptyState.classList.add("hidden");
    usersTableBody.parentElement.classList.remove("hidden");

    filtered.forEach((user, idx) => {
      const tr = document.createElement("tr");

      // Phone formatting and WhatsApp direct link
      const rawPhone = user.phone || "";
      const waNumber = rawPhone.startsWith("0") ? `20${rawPhone.slice(1)}` : rawPhone;
      const waLink = `https://wa.me/${waNumber}?text=${encodeURIComponent(`مرحباً أستاذ ${user.name || ''}، معك فريق دعم تطبيق جرعة Gor3a 🩺`)}`;

      // Format date
      let regDate = "-";
      if (user.created_at) {
        try {
          regDate = new Date(user.created_at).toLocaleDateString("ar-EG", { year: "numeric", month: "short", day: "numeric" });
        } catch (_) {
          regDate = user.created_at.split("T")[0];
        }
      }

      let lastLoginDate = "-";
      if (user.last_login_at) {
        try {
          lastLoginDate = new Date(user.last_login_at).toLocaleDateString("ar-EG", { year: "numeric", month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });
        } catch (_) {
          lastLoginDate = user.last_login_at.split("T")[0];
        }
      }

      const avatarLetter = (user.name || "م").trim().charAt(0);
      const rxsCount = user.prescriptions_count || 0;

      tr.innerHTML = `
        <td style="text-align: center; color: var(--text-muted); font-weight: 600;">${idx + 1}</td>
        <td>
          <div style="display: flex; align-items: center; gap: 0.65rem;">
            <div style="width: 36px; height: 36px; border-radius: 50%; background: #e0f2fe; color: #0369a1; display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 0.95rem;">
              ${escapeHtml(avatarLetter)}
            </div>
            <div>
              <div style="font-weight: 700; color: var(--text);">${escapeHtml(user.name || "مستخدم بدون اسم")}</div>
              ${user.age ? `<span style="font-size: 0.75rem; color: var(--text-muted);">${user.age} سنة</span>` : ''}
              ${user.gender === 'male' ? '<span style="display: inline-block; padding: 2px 6px; border-radius: 6px; background: #eff6ff; color: #1d4ed8; font-size: 0.75rem; font-weight: 600; margin-right: 4px;">ذكر 👨</span>' : (user.gender === 'female' ? '<span style="display: inline-block; padding: 2px 6px; border-radius: 6px; background: #fdf2f8; color: #be185d; font-size: 0.75rem; font-weight: 600; margin-right: 4px;">أنثى 👩</span>' : '')}
            </div>
          </div>
        </td>
        <td dir="ltr" style="text-align: right;">
          <a href="${waLink}" target="_blank" rel="noopener" class="btn btn-secondary btn-xs" style="text-decoration: none; display: inline-flex; align-items: center; gap: 0.35rem; color: #15803d; font-weight: 600; border-color: #bbf7d0; background: #f0fdf4;">
            <span style="font-size: 1rem;">🟢</span>
            <span>${escapeHtml(rawPhone)}</span>
          </a>
        </td>
        <td>
          <span class="gov-pill-tag" style="background: #f1f5f9; color: #334155; font-weight: 600;">
            📍 ${escapeHtml(user.governorate || "القاهرة")}
          </span>
        </td>
        <td style="text-align: center;">
          <span style="display: inline-flex; align-items: center; gap: 0.25rem; padding: 0.25rem 0.65rem; border-radius: 999px; background: ${rxsCount > 0 ? '#ecfdf5' : '#f8fafc'}; color: ${rxsCount > 0 ? '#047857' : '#94a3b8'}; font-weight: 700; font-size: 0.85rem; border: 1px solid ${rxsCount > 0 ? '#a7f3d0' : '#e2e8f0'};">
            📋 ${rxsCount} ${rxsCount === 1 ? 'روشتة' : 'روشتات'}
          </span>
        </td>
        <td style="font-size: 0.825rem; color: var(--text-muted);">${escapeHtml(regDate)}</td>
        <td style="font-size: 0.825rem; color: var(--text-muted);">${escapeHtml(lastLoginDate)}</td>
        <td style="text-align: center;">
          <button type="button" class="btn btn-xs" style="background: #fee2e2; color: #991b1b; border: 1px solid #fecaca;" onclick="window.deleteUserAccount('${user.id}')" title="حذف حساب العميل">
            🗑️
          </button>
        </td>
      `;

      usersTableBody.appendChild(tr);
    });
  }

  // Live Search Users
  if (userSearchInput) {
    userSearchInput.addEventListener("input", () => {
      renderUsersTable(usersList);
    });
  }

  if (refreshUsersBtn) {
    refreshUsersBtn.addEventListener("click", () => {
      loadUsersData();
      showToast("تم تحديث قائمة العملاء ✓");
    });
  }

  window.deleteUserAccount = async function(userId) {
    if (!adminToken) return;
    const user = usersList.find((u) => u.id === userId);
    const userName = user ? user.name : "هذا المستخدم";
    if (!confirm(`هل أنت متأكد من رغبتك في حذف حساب ${userName} وروشتاته السحابية نهائياً؟`)) {
      return;
    }

    try {
      const res = await fetch(`/api/admin/users/${userId}`, {
        method: "DELETE",
        headers: { Authorization: `Bearer ${adminToken}` },
      });
      if (res.ok) {
        usersList = usersList.filter((u) => u.id !== userId);
        renderUsersTable(usersList);
        if (statUsersCount) statUsersCount.textContent = usersList.length;
        if (statUsersTotal) statUsersTotal.textContent = usersList.length;
        showToast("تم حذف الحساب بنجاح 🗑️");
      } else {
        alert("فشل حذف الحساب");
      }
    } catch (_) {
      alert("تعذر الاتصال بالسيرفر");
    }
  };

  function showToast(msg) {
    adminToast.textContent = msg;
    adminToast.classList.remove("hidden");
    setTimeout(() => {
      adminToast.classList.add("hidden");
    }, 3500);
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
