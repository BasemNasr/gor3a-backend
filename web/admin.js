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
