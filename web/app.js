/**
 * Prescription AI Lab — Frontend Application
 * Phase 1: Local Research & Model Evaluation Lab
 */

document.addEventListener("DOMContentLoaded", () => {
  // DOM Elements - Inputs
  const fileInput = document.getElementById("fileInput");
  const dropzone = document.getElementById("dropzone");
  const loadSampleBtn = document.getElementById("loadSampleBtn");
  const previewWrapper = document.getElementById("previewWrapper");
  const prescriptionPreview = document.getElementById("prescriptionPreview");
  const previewFilename = document.getElementById("previewFilename");
  const previewMeta = document.getElementById("previewMeta");

  // DOM Elements - Config
  const providerSelect = document.getElementById("providerSelect");
  const modelSelect = document.getElementById("modelSelect");
  const providerStatusHint = document.getElementById("providerStatusHint");
  const analyzeBtn = document.getElementById("analyzeBtn");
  const btnSpinner = document.getElementById("btnSpinner");
  const btnText = document.getElementById("btnText");
  const errorAlert = document.getElementById("errorAlert");

  // DOM Elements - Result States
  const emptyState = document.getElementById("emptyState");
  const loadingState = document.getElementById("loadingState");
  const resultsContent = document.getElementById("resultsContent");
  const elapsedSeconds = document.getElementById("elapsedSeconds");

  // DOM Elements - Result Data
  const statConfidence = document.getElementById("statConfidence");
  const statConfidenceBar = document.getElementById("statConfidenceBar");
  const statMedCount = document.getElementById("statMedCount");
  const statMedBreakdown = document.getElementById("statMedBreakdown");
  const statLatency = document.getElementById("statLatency");
  const statModelUsed = document.getElementById("statModelUsed");
  const statCost = document.getElementById("statCost");
  const statTokens = document.getElementById("statTokens");

  const uncertainBanner = document.getElementById("uncertainBanner");
  const uncertainItemsList = document.getElementById("uncertainItemsList");
  const patientAge = document.getElementById("patientAge");
  const patientWeight = document.getElementById("patientWeight");
  const medListCount = document.getElementById("medListCount");
  const medicationsList = document.getElementById("medicationsList");
  const rawJsonCode = document.getElementById("rawJsonCode");
  const copyJsonBtn = document.getElementById("copyJsonBtn");

  // Application State
  let currentFile = null;
  let availableProviders = [];
  let timerInterval = null;
  let startTime = null;
  let lastAnalysisJson = null;

  // Initialize
  fetchProviders();
  setupEventListeners();

  // 1. Fetch Providers and Models from Backend
  async function fetchProviders() {
    try {
      const res = await fetch("/api/providers");
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      availableProviders = data.providers || [];
      populateProviders();
    } catch (err) {
      console.warn("Could not fetch /api/providers from relative path, trying localhost:8000...", err);
      try {
        const res = await fetch("http://localhost:8000/api/providers");
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const data = await res.json();
        availableProviders = data.providers || [];
        populateProviders();
      } catch (e) {
        showError("Failed to connect to backend API. Is the server running on port 8000?");
      }
    }
  }

  function populateProviders() {
    providerSelect.innerHTML = "";
    if (availableProviders.length === 0) {
      providerSelect.innerHTML = `<option value="">No providers available</option>`;
      return;
    }

    availableProviders.forEach((prov) => {
      const opt = document.createElement("option");
      opt.value = prov.id;
      const statusSuffix = prov.is_configured ? "✓ (Configured)" : "⚠️ (Missing API Key)";
      opt.textContent = `${prov.display_name} ${statusSuffix}`;
      providerSelect.appendChild(opt);
    });

    // Select first configured provider or default to gemini
    const defaultProv = availableProviders.find(p => p.is_configured) || availableProviders[0];
    if (defaultProv) {
      providerSelect.value = defaultProv.id;
    }
    updateModelsForSelectedProvider();
  }

  function updateModelsForSelectedProvider() {
    const provId = providerSelect.value;
    const prov = availableProviders.find((p) => p.id === provId);
    modelSelect.innerHTML = "";

    if (!prov) return;

    // Update status hint
    if (prov.is_configured) {
      providerStatusHint.textContent = `● API key detected for ${prov.display_name}`;
      providerStatusHint.className = "status-hint configured";
    } else {
      providerStatusHint.textContent = `● Missing API key in .env for ${prov.display_name}. Requests will fail unless configured.`;
      providerStatusHint.className = "status-hint unconfigured";
    }

    prov.supported_models.forEach((m) => {
      const opt = document.createElement("option");
      opt.value = m;
      opt.textContent = m === prov.default_model ? `${m} (Default)` : m;
      if (m === prov.default_model) opt.selected = true;
      modelSelect.appendChild(opt);
    });

    validateFormState();
  }

  // 2. Event Listeners
  function setupEventListeners() {
    providerSelect.addEventListener("change", updateModelsForSelectedProvider);
    fileInput.addEventListener("change", (e) => {
      if (e.target.files && e.target.files[0]) {
        handleFileSelection(e.target.files[0]);
      }
    });

    // Drag and drop
    ["dragenter", "dragover"].forEach((eventName) => {
      dropzone.addEventListener(eventName, (e) => {
        e.preventDefault();
        dropzone.classList.add("dragover");
      });
    });

    ["dragleave", "drop"].forEach((eventName) => {
      dropzone.addEventListener(eventName, (e) => {
        e.preventDefault();
        dropzone.classList.remove("dragover");
      });
    });

    dropzone.addEventListener("drop", (e) => {
      if (e.dataTransfer.files && e.dataTransfer.files[0]) {
        handleFileSelection(e.dataTransfer.files[0]);
      }
    });

    // Sample prescription generator
    loadSampleBtn.addEventListener("click", generateAndLoadSamplePrescription);

    // Analyze button
    analyzeBtn.addEventListener("click", analyzePrescription);

    // Copy JSON button
    copyJsonBtn.addEventListener("click", () => {
      if (!lastAnalysisJson) return;
      navigator.clipboard.writeText(JSON.stringify(lastAnalysisJson, null, 2)).then(() => {
        const orig = copyJsonBtn.textContent;
        copyJsonBtn.textContent = "Copied! ✓";
        setTimeout(() => { copyJsonBtn.textContent = orig; }, 2000);
      });
    });
  }

  function handleFileSelection(file) {
    if (!file.type.startsWith("image/")) {
      showError("Please upload a valid image file (JPEG, PNG, or WEBP).");
      return;
    }

    hideError();
    currentFile = file;
    previewFilename.textContent = file.name;
    previewMeta.textContent = `${(file.size / 1024).toFixed(1)} KB • ${file.type}`;

    const reader = new FileReader();
    reader.onload = (e) => {
      prescriptionPreview.src = e.target.result;
      previewWrapper.classList.remove("hidden");
      validateFormState();
    };
    reader.readAsDataURL(file);
  }

  function validateFormState() {
    analyzeBtn.disabled = !currentFile || !providerSelect.value;
  }

  // 3. Realistic Egyptian Prescription Canvas Generator (for instant 1-click testing)
  function generateAndLoadSamplePrescription() {
    const canvas = document.createElement("canvas");
    canvas.width = 1000;
    canvas.height = 1350;
    const ctx = canvas.getContext("2d");

    // Paper background with warm medical clinic tint
    ctx.fillStyle = "#faf8f2";
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    // Subtle paper texture lines
    ctx.strokeStyle = "#e8e5dc";
    ctx.lineWidth = 1;
    for (let y = 180; y < canvas.height - 100; y += 45) {
      ctx.beginPath();
      ctx.moveTo(60, y);
      ctx.lineTo(canvas.width - 60, y);
      ctx.stroke();
    }

    // Clinic Letterhead (Arabic + English)
    ctx.fillStyle = "#1e3a8a";
    ctx.font = "bold 28px Cairo, sans-serif";
    ctx.textAlign = "right";
    ctx.fillText("عيادة د. أحمد السيد", 940, 70);
    ctx.font = "18px Cairo, sans-serif";
    ctx.fillText("استشاري طب الأطفال وحديثي الولادة", 940, 100);

    ctx.textAlign = "left";
    ctx.font = "bold 22px Inter, sans-serif";
    ctx.fillText("Dr. Ahmed El-Sayed, M.D.", 60, 70);
    ctx.font = "16px Inter, sans-serif";
    ctx.fillText("Consultant Pediatrician", 60, 95);

    // Header divider line
    ctx.strokeStyle = "#1e3a8a";
    ctx.lineWidth = 2.5;
    ctx.beginPath();
    ctx.moveTo(60, 120);
    ctx.lineTo(940, 120);
    ctx.stroke();

    // Patient info row
    ctx.fillStyle = "#334155";
    ctx.font = "18px Inter, sans-serif";
    ctx.fillText("Pt: Youssef M.              Age: 8 months              Wt: 8.14 kg", 60, 150);

    // R/ Symbol
    ctx.fillStyle = "#0f172a";
    ctx.font = "bold 64px 'Times New Roman', serif";
    ctx.fillText("℞", 70, 240);

    // Handwritten medications in blue ink with natural variation
    ctx.fillStyle = "#1d3557";
    ctx.font = "italic 32px 'Brush Script MT', 'Comic Sans MS', cursive";

    // Item 1: Flagyl susp
    ctx.fillText("1. Flagyl 125mg/5ml susp", 130, 320);
    ctx.font = "24px Cairo, cursive";
    ctx.fillText("٣.٥ سم ٣ مرات يومياً بعد الرضاعة لمدة ٥ أيام", 170, 365);

    // Item 2: Cetal drops
    ctx.font = "italic 32px 'Brush Script MT', 'Comic Sans MS', cursive";
    ctx.fillText("2. Cetal drops 100mg/ml", 130, 480);
    ctx.font = "24px Cairo, cursive";
    ctx.fillText("١٠ نقط عند اللزوم (الحرارة > ٣٨.٥)", 170, 525);

    // Item 3: Antinal susp with slight blur/degradation
    ctx.font = "italic 30px 'Brush Script MT', 'Comic Sans MS', cursive";
    ctx.fillText("3. Antinal susp (1 spoon ...)", 130, 640);
    ctx.font = "22px Cairo, cursive";
    ctx.fillText("ملعقة كل ٨ ساعات ... (خط غير واضح)", 170, 685);

    // Clinic Footer stamp
    ctx.strokeStyle = "#991b1b";
    ctx.lineWidth = 2;
    ctx.strokeRect(650, 1150, 280, 110);
    ctx.fillStyle = "#991b1b";
    ctx.font = "bold 16px Cairo, sans-serif";
    ctx.textAlign = "center";
    ctx.fillText("د. أحمد السيد", 790, 1190);
    ctx.font = "14px Cairo, sans-serif";
    ctx.fillText("نقابة أطباء مصر: ٤٨٢٩١", 790, 1220);

    canvas.toBlob((blob) => {
      const file = new File([blob], "egyptian_pediatric_rx_sample.jpg", { type: "image/jpeg" });
      handleFileSelection(file);
    }, "image/jpeg", 0.95);
  }

  // 4. Send Analysis Request
  async function analyzePrescription() {
    if (!currentFile) return;

    hideError();
    setLoadingState(true);

    const formData = new FormData();
    formData.append("file", currentFile);
    formData.append("provider", providerSelect.value);
    formData.append("model", modelSelect.value);

    const pipelineSelect = document.getElementById("pipelineSelect");
    if (pipelineSelect) {
      formData.append("pipeline_version", pipelineSelect.value);
    }

    try {
      const res = await fetch("/api/analyze", {
        method: "POST",
        body: formData,
      });

      if (!res.ok) {
        let errMessage = `Error ${res.status}`;
        try {
          const errData = await res.json();
          errMessage = errData.detail || errMessage;
        } catch (_) {}
        throw new Error(errMessage);
      }

      const responseData = await res.json();
      lastAnalysisJson = responseData;
      renderAnalysisResults(responseData);
    } catch (err) {
      console.error("Analysis failed:", err);
      showError(err.message || "Failed to analyze prescription. Please check console or server logs.");
      setLoadingState(false, true);
    } finally {
      setLoadingState(false);
    }
  }

  // 5. Loading State & Timer
  function setLoadingState(isLoading, isError = false) {
    if (isLoading) {
      btnSpinner.classList.remove("hidden");
      btnText.textContent = "Analyzing...";
      analyzeBtn.disabled = true;

      emptyState.classList.add("hidden");
      resultsContent.classList.add("hidden");
      loadingState.classList.remove("hidden");

      startTime = Date.now();
      elapsedSeconds.textContent = "0.0";
      timerInterval = setInterval(() => {
        const diff = ((Date.now() - startTime) / 1000).toFixed(1);
        elapsedSeconds.textContent = diff;
      }, 100);
    } else {
      btnSpinner.classList.add("hidden");
      btnText.textContent = "Analyze Prescription";
      analyzeBtn.disabled = false;

      if (timerInterval) {
        clearInterval(timerInterval);
        timerInterval = null;
      }
      loadingState.classList.add("hidden");

      if (isError) {
        emptyState.classList.remove("hidden");
      }
    }
  }

  // 6. Render Extracted Results
  function renderAnalysisResults(data) {
    const analysis = data.analysis;
    const metadata = data.metadata;

    emptyState.classList.add("hidden");
    loadingState.classList.add("hidden");
    resultsContent.classList.remove("hidden");

    // Ribbon Stats
    const confScore = Math.round((analysis.overall_confidence || 0) * 100);
    statConfidence.textContent = `${confScore}%`;
    statConfidenceBar.style.width = `${confScore}%`;
    if (confScore >= 80) {
      statConfidenceBar.style.backgroundColor = "var(--success-solid)";
    } else if (confScore >= 50) {
      statConfidenceBar.style.backgroundColor = "var(--warning-solid)";
    } else {
      statConfidenceBar.style.backgroundColor = "var(--danger-solid)";
    }

    const meds = analysis.medications || [];
    statMedCount.textContent = meds.length;
    const idCount = meds.filter((m) => m.status === "identified").length;
    const uncCount = meds.filter((m) => m.status === "uncertain").length;
    const unreadCount = meds.filter((m) => m.status === "unreadable").length;
    statMedBreakdown.textContent = `${idCount} identified • ${uncCount} uncertain • ${unreadCount} unreadable`;

    statLatency.textContent = `${metadata.processing_time_ms.toLocaleString()} ms`;
    statModelUsed.textContent = `${metadata.provider} (${metadata.model})`;

    if (metadata.cost_estimate) {
      statCost.textContent = `$${metadata.cost_estimate.total_cost_usd.toFixed(4)}`;
    } else {
      statCost.textContent = "N/A";
    }

    const usage = metadata.usage;
    statTokens.textContent = usage ? `${usage.total_tokens.toLocaleString()} tokens` : "-- tokens";

    // Uncertain Items Callout
    const uncertains = analysis.uncertain_items || [];
    if (uncertains.length > 0) {
      uncertainBanner.classList.remove("hidden");
      uncertainItemsList.innerHTML = "";
      uncertains.forEach((item) => {
        const li = document.createElement("li");
        li.textContent = item;
        uncertainItemsList.appendChild(li);
      });
    } else {
      uncertainBanner.classList.add("hidden");
    }

    // Patient Information
    const pt = analysis.prescription || {};
    patientAge.textContent = pt.patient_age || "Not legible / Absent";
    patientWeight.textContent = pt.patient_weight_kg ? `${pt.patient_weight_kg} kg` : "Not legible / Absent";

    // Medications List
    medListCount.textContent = meds.length;
    medicationsList.innerHTML = "";

    if (meds.length === 0) {
      medicationsList.innerHTML = `<div class="empty-meds">No medication lines were detected on this prescription.</div>`;
    } else {
      meds.forEach((med, idx) => {
        const card = document.createElement("div");
        card.className = `med-card status-${med.status}`;

        const confPct = Math.round(med.confidence * 100);

        card.innerHTML = `
          <div class="med-header">
            <div class="med-title-wrap">
              <span class="med-num">${idx + 1}</span>
              <span class="med-name ${med.name ? '' : 'unreadable'}">
                ${escapeHtml(med.name || "Unreadable Drug Name")}
              </span>
              ${med.active_ingredient ? `<span class="active-ingredient-badge">${escapeHtml(med.active_ingredient)}</span>` : ""}
              ${med.eda_id ? `<span class="eda-badge" title="Registered with Egyptian Drug Authority">EDA: ${escapeHtml(med.eda_id)}</span>` : ""}
            </div>
            <div class="med-badges">
              <span class="status-pill ${med.status}">${med.status}</span>
              <span class="confidence-pill">${confPct}% Conf.</span>
            </div>
          </div>

          ${med.lasa_warning ? `
            <div class="lasa-warning-box">
              <span>⚠️</span>
              <div><strong>Clinical Safety Alert:</strong> ${escapeHtml(med.lasa_warning)}</div>
            </div>
          ` : ""}

          <div class="raw-transcription-box">
            <span class="raw-label">Doctor's Raw Handwriting:</span>
            <span class="raw-text-value">"${escapeHtml(med.raw_text)}"</span>
          </div>

          <div class="med-details-grid">
            <div class="detail-cell">
              <span class="detail-lbl">Strength / Concentration</span>
              <span class="detail-val ${med.strength ? '' : 'is-null'}">${med.strength ? escapeHtml(med.strength) : "Unknown / Null"}</span>
            </div>
            <div class="detail-cell">
              <span class="detail-lbl">Prescribed Dose</span>
              <span class="detail-val ${med.dose ? '' : 'is-null'}">${med.dose ? escapeHtml(med.dose) : "Unknown / Null"}</span>
            </div>
            <div class="detail-cell">
              <span class="detail-lbl">Frequency</span>
              <span class="detail-val ${med.frequency ? '' : 'is-null'}">${med.frequency ? escapeHtml(med.frequency) : "Unknown / Null"}</span>
            </div>
            <div class="detail-cell">
              <span class="detail-lbl">Duration</span>
              <span class="detail-val ${med.duration ? '' : 'is-null'}">${med.duration ? escapeHtml(med.duration) : "Unknown / Null"}</span>
            </div>
            ${med.instructions ? `
              <div class="detail-cell instructions-cell">
                <span class="detail-lbl">Instructions / Route</span>
                <span class="detail-val">${escapeHtml(med.instructions)}</span>
              </div>
            ` : ""}
          </div>

          ${med.candidates && med.candidates.length > 0 ? `
            <div class="candidates-container">
              <details>
                <summary class="candidates-toggle">
                  🔍 Egyptian Drug Authority Matches (${med.candidates.length} candidates)
                </summary>
                <div class="candidates-list">
                  ${med.candidates.map(c => `
                    <div class="candidate-item">
                      <div class="candidate-name-wrap">
                        <strong>${escapeHtml(c.name)}</strong>
                        ${c.commercial_name_ar ? `<span style="direction: rtl; color: var(--text-muted); font-size: 0.8rem;">(${escapeHtml(c.commercial_name_ar)})</span>` : ""}
                        <span style="color: var(--text-muted); font-size: 0.725rem;">• ${escapeHtml(c.dosage_form || "")} ${escapeHtml(c.strength || "")}</span>
                      </div>
                      <span class="candidate-score ${c.score >= 0.85 ? 'high' : (c.score >= 0.65 ? 'mid' : '')}">
                        ${Math.round(c.score * 100)}% Match (${escapeHtml(c.match_type)})
                      </span>
                    </div>
                  `).join('')}
                </div>
              </details>
            </div>
          ` : ""}
        `;
        medicationsList.appendChild(card);
      });
    }

    // Raw JSON Viewer
    rawJsonCode.textContent = JSON.stringify(data, null, 2);
  }

  function showError(msg) {
    errorAlert.textContent = msg;
    errorAlert.classList.remove("hidden");
  }

  function hideError() {
    errorAlert.classList.add("hidden");
    errorAlert.textContent = "";
  }

  function escapeHtml(str) {
    if (!str) return "";
    return str
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#039;");
  }

  // =========================================================================
  // Phase 2: Multi-Model Benchmark Tab & Suite
  // =========================================================================
  const tabLabBtn = document.getElementById("tabLabBtn");
  const tabBenchmarkBtn = document.getElementById("tabBenchmarkBtn");
  const labView = document.getElementById("labView");
  const benchmarkView = document.getElementById("benchmarkView");
  const phaseBadge = document.getElementById("phaseBadge");

  const runBenchmarkBtn = document.getElementById("runBenchmarkBtn");
  const benchSpinner = document.getElementById("benchSpinner");
  const benchBtnText = document.getElementById("benchBtnText");
  const benchmarkTableBody = document.getElementById("benchmarkTableBody");
  const benchmarkCasesGrid = document.getElementById("benchmarkCasesGrid");
  const benchCaseCount = document.getElementById("benchCaseCount");

  tabLabBtn.addEventListener("click", () => switchTab("lab"));
  tabBenchmarkBtn.addEventListener("click", () => switchTab("benchmark"));

  function switchTab(tab) {
    if (tab === "lab") {
      tabLabBtn.classList.add("active");
      tabBenchmarkBtn.classList.remove("active");
      labView.classList.remove("hidden");
      benchmarkView.classList.add("hidden");
      phaseBadge.textContent = "PHASE 1 — LOCAL RESEARCH LAB";
    } else {
      tabBenchmarkBtn.classList.add("active");
      tabLabBtn.classList.remove("active");
      benchmarkView.classList.remove("hidden");
      labView.classList.add("hidden");
      phaseBadge.textContent = "PHASE 2 — MULTI-MODEL BENCHMARK";
      loadBenchmarkCases();
      loadBenchmarkResults();
    }
  }

  async function loadBenchmarkCases() {
    try {
      const res = await fetch("/api/benchmark/cases");
      if (!res.ok) return;
      const data = await res.json();
      const cases = data.cases || [];
      benchCaseCount.textContent = cases.length;
      benchmarkCasesGrid.innerHTML = "";

      cases.forEach((c) => {
        const card = document.createElement("div");
        card.className = "case-card";
        const pt = c.patient || {};
        const ptStr = `Age: ${pt.patient_age || "N/A"} • Wt: ${pt.patient_weight_kg ? pt.patient_weight_kg + " kg" : "N/A"}`;

        card.innerHTML = `
          <div class="case-img-box">
            <img src="/api/benchmark/case/${c.case_id}/image" alt="${c.case_id}">
          </div>
          <div class="case-content">
            <div class="case-header-row">
              <span class="case-tag">${c.case_id}</span>
              <span class="case-med-count">${c.medication_count} meds</span>
            </div>
            <div class="case-pt-info">${escapeHtml(ptStr)}</div>
            <div class="case-meds-summary" title="${c.medications_summary.join(', ')}">
              ℞ ${escapeHtml(c.medications_summary.join(", "))}
            </div>
          </div>
        `;

        // Click case card to test directly in lab
        card.addEventListener("click", () => {
          fetch(`/api/benchmark/case/${c.case_id}/image`)
            .then(res => res.blob())
            .then(blob => {
              const file = new File([blob], `${c.case_id}_image.jpg`, { type: "image/jpeg" });
              switchTab("lab");
              handleFileSelection(file);
            });
        });

        benchmarkCasesGrid.appendChild(card);
      });
    } catch (err) {
      console.warn("Failed to load benchmark cases:", err);
    }
  }

  async function loadBenchmarkResults() {
    try {
      const res = await fetch("/api/benchmark/results");
      if (!res.ok) return;
      const data = await res.json();
      const summaries = (data.results && data.results.model_summaries) || {};
      renderBenchmarkTable(summaries);
    } catch (err) {
      console.warn("Failed to load benchmark results:", err);
    }
  }

  runBenchmarkBtn.addEventListener("click", async () => {
    const checked = Array.from(document.querySelectorAll('input[name="benchModel"]:checked'))
      .map(cb => {
        const parts = cb.value.split(":");
        return {
          provider: parts[0],
          model: parts[1],
          pipeline: parts[2] || "v2",
        };
      });

    if (checked.length === 0) {
      alert("Please select at least one model to benchmark.");
      return;
    }

    benchSpinner.classList.remove("hidden");
    benchBtnText.textContent = "Evaluating Models...";
    runBenchmarkBtn.disabled = true;

    try {
      const res = await fetch("/api/benchmark/run", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ models: checked, use_cache: false }),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      const summaries = (data.results && data.results.model_summaries) || {};
      renderBenchmarkTable(summaries);
    } catch (err) {
      alert(`Benchmark failed: ${err.message}`);
    } finally {
      benchSpinner.classList.add("hidden");
      benchBtnText.textContent = "▶ Run Multi-Model Benchmark";
      runBenchmarkBtn.disabled = false;
    }
  });

  function renderBenchmarkTable(summaries) {
    benchmarkTableBody.innerHTML = "";
    const keys = Object.keys(summaries);

    if (keys.length === 0) {
      benchmarkTableBody.innerHTML = `<tr><td colspan="9" class="text-center">No benchmark runs recorded. Click "Run Multi-Model Benchmark".</td></tr>`;
      return;
    }

    // Sort by critical error rate ascending (safest first), then by name accuracy descending
    const sorted = Object.entries(summaries).sort((a, b) => {
      const critDiff = a[1].critical_medication_error_rate - b[1].critical_medication_error_rate;
      if (critDiff !== 0) return critDiff;
      return b[1].medication_name_accuracy - a[1].medication_name_accuracy;
    });

    sorted.forEach(([modelKey, s]) => {
      const row = document.createElement("tr");

      const nameAcc = `${Math.round(s.medication_name_accuracy * 100)}%`;
      const critErr = Math.round(s.critical_medication_error_rate * 100);
      const critBadge = critErr === 0 
        ? `<span class="critical-safe">0.0% (Safe)</span>` 
        : `<span class="critical-danger">⚠️ ${critErr}% (Critical)</span>`;

      const uncAcc = `${Math.round(s.uncertainty_detection_accuracy * 100)}%`;
      const strAcc = `${Math.round(s.strength_accuracy * 100)}%`;
      const doseAcc = `${Math.round(s.dose_accuracy * 100)}%`;
      const freqAcc = `${Math.round(s.frequency_accuracy * 100)}%`;
      const lat = `${Math.round(s.mean_latency_ms)} ms`;
      const cost = `$${s.mean_cost_per_rx_usd.toFixed(4)}`;

      row.innerHTML = `
        <td><strong>${escapeHtml(modelKey)}</strong></td>
        <td>${nameAcc}</td>
        <td>${critBadge}</td>
        <td>${uncAcc}</td>
        <td>${strAcc}</td>
        <td>${doseAcc}</td>
        <td>${freqAcc}</td>
        <td>${lat}</td>
        <td>${cost}</td>
      `;
      benchmarkTableBody.appendChild(row);
    });
  }
});
