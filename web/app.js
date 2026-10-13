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

  // DOM Elements - Overall Diagnosis Card
  const overallDiagnosisCard = document.getElementById("overallDiagnosisCard");
  const overallDiagnosisBody = document.getElementById("overallDiagnosisBody");

  // DOM Elements - Prescription Medication Detail Modal
  const medicationDetailModal = document.getElementById("medicationDetailModal");
  const closeMedModalBtn = document.getElementById("closeMedModalBtn");
  const closeMedModalBottomBtn = document.getElementById("closeMedModalBottomBtn");
  const copyMedModalInfoBtn = document.getElementById("copyMedModalInfoBtn");
  const medModalExploreAlternativesBtn = document.getElementById("medModalExploreAlternativesBtn");
  const medModalCommercialEn = document.getElementById("medModalCommercialEn");
  const medModalCommercialAr = document.getElementById("medModalCommercialAr");
  const medModalEdaId = document.getElementById("medModalEdaId");
  const medModalStatusBadge = document.getElementById("medModalStatusBadge");
  const medModalDosageBadge = document.getElementById("medModalDosageBadge");
  const medModalImage = document.getElementById("medModalImage");
  const medModalVisualSkeleton = document.getElementById("medModalVisualSkeleton");
  const medModalActive = document.getElementById("medModalActive");
  const medModalStrengthForm = document.getElementById("medModalStrengthForm");
  const medModalCategory = document.getElementById("medModalCategory");
  const medModalPrice = document.getElementById("medModalPrice");
  const medModalWhatItTreats = document.getElementById("medModalWhatItTreats");
  const medModalLikelyDiagnosis = document.getElementById("medModalLikelyDiagnosis");
  const medModalMechanism = document.getElementById("medModalMechanism");
  const medModalPrecautions = document.getElementById("medModalPrecautions");
  const medModalDose = document.getElementById("medModalDose");
  const medModalFrequency = document.getElementById("medModalFrequency");
  const medModalDuration = document.getElementById("medModalDuration");
  const medModalInstructions = document.getElementById("medModalInstructions");
  const medModalRawText = document.getElementById("medModalRawText");
  const medModalAltibbiLink = document.getElementById("medModalAltibbiLink");

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
      providerSelect.innerHTML = `<option value="gemini">Gor3a Clinical AI</option>`;
      return;
    }

    availableProviders.forEach((prov) => {
      const opt = document.createElement("option");
      opt.value = prov.id;
      opt.textContent = prov.display_name;
      providerSelect.appendChild(opt);
    });

    // Prefer gemini when configured, otherwise first configured provider
    const geminiProv = availableProviders.find(p => p.id === "gemini" && p.is_configured);
    const defaultProv = geminiProv || availableProviders.find(p => p.is_configured) || availableProviders[0];
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

    if (providerStatusHint) {
      providerStatusHint.textContent = "● Gor3a Clinical AI Engine Ready";
      providerStatusHint.className = "status-hint configured";
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

    // Prescription Medication Detail Modal Event Listeners
    if (closeMedModalBtn) {
      closeMedModalBtn.addEventListener("click", closeMedModal);
    }
    if (closeMedModalBottomBtn) {
      closeMedModalBottomBtn.addEventListener("click", closeMedModal);
    }
    if (medicationDetailModal) {
      medicationDetailModal.addEventListener("click", (e) => {
        if (e.target === medicationDetailModal) {
          closeMedModal();
        }
      });
    }
    document.addEventListener("keydown", (e) => {
      if (e.key === "Escape" && medicationDetailModal && !medicationDetailModal.classList.contains("hidden")) {
        closeMedModal();
      }
    });

    if (copyMedModalInfoBtn) {
      copyMedModalInfoBtn.addEventListener("click", () => {
        const nameEn = medModalCommercialEn.textContent || "";
        const nameAr = medModalCommercialAr.textContent || "";
        const textToCopy = `📋 تفاصيل الدواء والتشخيص: ${nameEn} ${nameAr ? '(' + nameAr + ')' : ''}\n` +
          `• المادة الفعالة: ${medModalActive.textContent}\n` +
          `• ماذا يعالج الدواء: ${medModalWhatItTreats.textContent}\n` +
          `• تشخيص الطبيب المتوقع: ${medModalLikelyDiagnosis.textContent}\n` +
          `• طريقة عمل الدواء: ${medModalMechanism.textContent}\n` +
          `• الجرعة بالروشتة: ${medModalDose.textContent} (${medModalFrequency.textContent})\n` +
          `• تعليمات الاستخدام: ${medModalInstructions.textContent}\n` +
          `• تنبيهات هامة: ${medModalPrecautions.textContent}`;

        navigator.clipboard.writeText(textToCopy).then(() => {
          const orig = copyMedModalInfoBtn.textContent;
          copyMedModalInfoBtn.textContent = "تم النسخ بنجاح! ✓";
          setTimeout(() => { copyMedModalInfoBtn.textContent = orig; }, 2000);
        });
      });
    }
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
      btnText.textContent = "Analyzing Prescription...";
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
      btnText.textContent = "🔍 Analyze Prescription | تحليل الروشتة";
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
    statModelUsed.textContent = "Gor3a Clinical AI + EDA V2";

    if (statCost) {
      statCost.textContent = metadata.cost_estimate ? `$${metadata.cost_estimate.total_cost_usd.toFixed(4)}` : "N/A";
    }
    if (statTokens) {
      const usage = metadata.usage;
      statTokens.textContent = usage ? `${usage.total_tokens.toLocaleString()} tokens` : "-- tokens";
    }

    function cleanVal(v) {
      if (!v) return null;
      const s = String(v).trim();
      if (!s || s.toLowerCase() === 'null' || s.toLowerCase() === 'none' || s.toLowerCase() === 'unknown' || s.toLowerCase() === 'n/a' || s.toLowerCase() === 'null null for null') {
        return null;
      }
      return s;
    }

    // Uncertain Items Callout
    const uncertains = (analysis.uncertain_items || []).filter(u => cleanVal(u));
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

    // Overall Prescription Clinical Diagnosis Insight
    if (overallDiagnosisCard && overallDiagnosisBody) {
      const overallDiagText = cleanVal(analysis.overall_likely_diagnosis);
      if (overallDiagText) {
        overallDiagnosisBody.innerHTML = `
          <div class="overall-diag-highlight">
            <span class="diag-bullet">🎯</span>
            <div class="diag-content">
              <strong>التشخيص المرجح بناءً على توليفة الأدوية:</strong>
              <p class="diag-text">${escapeHtml(overallDiagText)}</p>
            </div>
          </div>
        `;
        overallDiagnosisCard.classList.remove("hidden");
      } else {
        overallDiagnosisCard.classList.add("hidden");
        overallDiagnosisBody.innerHTML = "";
      }
    }

    // Patient Information
    const pt = analysis.prescription || {};
    const ageVal = cleanVal(pt.patient_age);
    const weightVal = (pt.patient_weight_kg && pt.patient_weight_kg > 0) ? `${pt.patient_weight_kg} kg` : null;
    patientAge.textContent = ageVal || "Not legible / Absent";
    patientWeight.textContent = weightVal || "Not legible / Absent";

    // Medications List & State Storage
    window.currentPrescriptionMeds = meds;
    medListCount.textContent = meds.length;
    medicationsList.innerHTML = "";

    if (meds.length === 0) {
      medicationsList.innerHTML = `<div class="empty-meds">No medication lines were detected on this prescription.</div>`;
    } else {
      meds.forEach((med, idx) => {
        const card = document.createElement("div");
        card.className = `med-card status-${med.status}`;

        const confPct = Math.round(med.confidence * 100);

        const cleanName = cleanVal(med.name);
        const topCandidate = (med.candidates && med.candidates.length > 0) ? cleanVal(med.candidates[0].name) : null;
        const rawLine = cleanVal(med.raw_text);
        const displayName = cleanName || topCandidate || rawLine || "Unreadable Drug Name";
        const cleanActive = cleanVal(med.active_ingredient);
        const cleanEdaId = cleanVal(med.eda_id);
        const cleanLasa = cleanVal(med.lasa_warning);
        const cleanStrength = cleanVal(med.strength);
        const cleanDose = cleanVal(med.dose);
        const cleanFreq = cleanVal(med.frequency);
        const cleanDur = cleanVal(med.duration);
        const cleanInstructions = cleanVal(med.instructions);

        // Clinical Indication, Diagnosis, and Image URL
        const cleanIndication = cleanVal(med.clinical_indication);
        const cleanDiag = cleanVal(med.likely_diagnosis);
        const medImgUrl = med.image_url || (cleanEdaId ? `/api/drugs/${encodeURIComponent(cleanEdaId)}/image` : `/api/drugs/image-by-name?name=${encodeURIComponent(displayName)}`);
        const arCandidateName = (med.candidates && med.candidates.length > 0 && med.candidates[0].commercial_name_ar) ? med.candidates[0].commercial_name_ar : null;

        card.innerHTML = `
          <div class="med-header">
            <div class="med-title-wrap">
              <span class="med-num">${idx + 1}</span>

              <!-- Small Drug Packaging Visual Thumbnail (صورة صغيرة للدواء) -->
              <div class="med-thumb-preview" onclick="openMedicationModal(${idx})" title="انقر لتكبير صورة العلاج والتفاصيل الكاملة">
                <img src="${medImgUrl}" class="med-thumb-img" alt="${escapeHtml(displayName)}" loading="lazy">
                <span class="thumb-zoom-badge">🔍</span>
              </div>

              <div class="med-name-block">
                <div class="med-name-row">
                  <span class="med-name ${cleanName ? '' : (topCandidate ? 'uncertain' : 'unreadable')}" onclick="openMedicationModal(${idx})" title="انقر لعرض تفاصيل الدواء والتشخيص">
                    ${escapeHtml(displayName)}
                  </span>
                  ${arCandidateName ? `<span class="med-ar-badge">(${escapeHtml(arCandidateName)})</span>` : ""}
                </div>
                <div class="med-tags-row">
                  ${cleanActive ? `<span class="active-ingredient-badge">${escapeHtml(cleanActive)}</span>` : ""}
                  ${cleanEdaId ? `<span class="eda-badge" title="Registered with Egyptian Drug Authority">EDA: ${escapeHtml(cleanEdaId)}</span>` : ""}
                </div>
              </div>
            </div>

            <div class="med-badges">
              <span class="status-pill ${med.status}">${med.status}</span>
              <span class="confidence-pill">${confPct}% Conf.</span>
            </div>
          </div>

          <!-- Concise Clinical Indication & Diagnosis Box (تحت الدواء باختصار) -->
          ${cleanIndication ? `
            <div class="clinical-indication-summary-card" onclick="openMedicationModal(${idx})" title="انقر لعرض تفاصيل الدواء بالكامل وصورة العلاج">
              <div class="indication-summary-header">
                <div class="indication-tag-badge">
                  <span class="indication-pulse-dot"></span>
                  <strong>🩺 ماذا يعالج هذا الدواء ومؤشر التشخيص:</strong>
                </div>
                <span class="click-details-tag">عرض التفاصيل وصورة العلاج 🔍</span>
              </div>
              <p class="indication-summary-text">${escapeHtml(cleanIndication)}</p>
              ${cleanDiag ? `
                <div class="likely-diagnosis-callout">
                  <span class="diag-icon-small">🎯</span>
                  <span><strong>مؤشر تشخيص الطبيب:</strong> ${escapeHtml(cleanDiag)}</span>
                </div>
              ` : ""}
            </div>
          ` : ""}

          ${cleanLasa ? `
            <div class="lasa-warning-box">
              <span>⚠️</span>
              <div><strong>Clinical Safety Alert:</strong> ${escapeHtml(cleanLasa)}</div>
            </div>
          ` : ""}

          <div class="raw-transcription-box">
            <span class="raw-label">Doctor's Raw Handwriting:</span>
            <span class="raw-text-value">"${escapeHtml(rawLine || "Illegible / Not deciphered")}"</span>
          </div>

          <div class="med-details-grid">
            <div class="detail-cell">
              <span class="detail-lbl">⚖️ Strength / Concentration</span>
              <span class="detail-val ${cleanStrength ? '' : 'is-null'}">${cleanStrength ? escapeHtml(cleanStrength) : "Not specified"}</span>
            </div>
            <div class="detail-cell">
              <span class="detail-lbl">💊 Prescribed Dose</span>
              <span class="detail-val ${cleanDose ? '' : 'is-null'}">${cleanDose ? escapeHtml(cleanDose) : "Not specified"}</span>
            </div>
            <div class="detail-cell">
              <span class="detail-lbl">⏰ Frequency</span>
              <span class="detail-val ${cleanFreq ? '' : 'is-null'}">${cleanFreq ? escapeHtml(cleanFreq) : "Not specified"}</span>
            </div>
            <div class="detail-cell">
              <span class="detail-lbl">📅 Duration</span>
              <span class="detail-val ${cleanDur ? '' : 'is-null'}">${cleanDur ? escapeHtml(cleanDur) : "Not specified"}</span>
            </div>
            ${cleanInstructions ? `
              <div class="detail-cell instructions-cell">
                <div class="instructions-header">
                  <span class="detail-lbl">📋 Full Clinical Administration / Instructions:</span>
                  ${/[\u0600-\u06FF]/.test(cleanInstructions) ? `<span class="badge badge-neutral ar-badge">تعليمات باللغة العربية 🇪🇬</span>` : ""}
                </div>
                <div class="instructions-text">${escapeHtml(cleanInstructions)}</div>
              </div>
            ` : ""}
          </div>

          <!-- Bottom Action Button for Full Drug Details & Packaging Photo -->
          <div class="med-card-action-row">
            <button type="button" class="btn btn-outline-primary btn-sm view-drug-details-btn" onclick="openMedicationModal(${idx})">
              🔍 تفاصيل الدواء وصورة العلاج وتشخيص الطبيب بالكامل
            </button>
          </div>

          ${med.candidates && med.candidates.length > 0 ? `
            <div class="candidates-container">
              <details>
                <summary class="candidates-toggle">
                  🔍 Egyptian Drug Authority Matches (${med.candidates.length} candidates)
                </summary>
                <div class="candidates-list">
                  ${med.candidates.map(c => `
                    <div class="candidate-item" onclick="openCandidateModal('${escapeHtml(c.drug_id)}', ${idx})" title="انقر لعرض تفاصيل وصورة وسعر هذا الصنف بالكامل">
                      <div class="candidate-name-wrap">
                        <strong>${escapeHtml(c.name)}</strong>
                        ${c.commercial_name_ar ? `<span style="direction: rtl; color: var(--text-muted); font-size: 0.8rem;">(${escapeHtml(c.commercial_name_ar)})</span>` : ""}
                        <span style="color: var(--text-muted); font-size: 0.725rem;">• ${escapeHtml(c.dosage_form || "")} ${escapeHtml(c.strength || "")}</span>
                      </div>
                      <div style="display: flex; align-items: center; gap: 6px;">
                        <span class="candidate-score ${c.score >= 0.85 ? 'high' : (c.score >= 0.65 ? 'mid' : '')}">
                          ${Math.round(c.score * 100)}% Match (${escapeHtml(c.match_type)})
                        </span>
                        <span class="candidate-inspect-badge" style="font-size: 0.72rem; color: #00897B; background: #E8F1F2; padding: 2px 7px; border-radius: 4px; font-weight: 700; display: inline-flex; align-items: center; gap: 3px;">
                          🔍 تفاصيل الصنف
                        </span>
                      </div>
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

    // Dietary & Herbal Advice Callout
    const dietaryAdviceCard = document.getElementById("dietaryAdviceCard");
    const dietaryAdviceContent = document.getElementById("dietaryAdviceContent");
    const cleanDietary = cleanVal(analysis.dietary_and_herbal_advice);

    if (dietaryAdviceCard && dietaryAdviceContent) {
      if (cleanDietary) {
        dietaryAdviceContent.textContent = cleanDietary;
        dietaryAdviceCard.classList.remove("hidden");
      } else {
        dietaryAdviceCard.classList.add("hidden");
        dietaryAdviceContent.textContent = "";
      }
    }

    // Raw JSON Viewer
    rawJsonCode.textContent = JSON.stringify(data, null, 2);
  }

  // 6.1 Open Medication Clinical Detail Modal (نافذة تفاصيل الدواء بالكامل مع صورة العلاج)
  function openMedicationModal(idx) {
    if (!window.currentPrescriptionMeds || !window.currentPrescriptionMeds[idx]) return;
    const med = window.currentPrescriptionMeds[idx];
    const details = med.indication_details || {};

    const cleanName = (med.name && med.name.trim()) || null;
    const topCandidate = (med.candidates && med.candidates.length > 0) ? med.candidates[0] : null;
    const displayName = cleanName || (topCandidate ? topCandidate.name : null) || (med.raw_text ? med.raw_text.trim() : null) || "مستحضر صيدلاني";
    const arName = (topCandidate && topCandidate.commercial_name_ar) ? topCandidate.commercial_name_ar : (med.commercial_name_ar || "");

    medModalCommercialEn.textContent = displayName;
    medModalCommercialAr.textContent = arName ? `(${arName})` : "";
    medModalEdaId.textContent = med.eda_id || (topCandidate ? topCandidate.drug_id : "EDA-REG");
    medModalStatusBadge.textContent = med.status === "identified" ? "تم التعرف بنجاح ✓" : (med.status === "uncertain" ? "قيد المراجعة ⚠️" : "غير مقروء");
    // Packaging Visual Image (with shimmer loading & SVG fallback)
    if (medModalImage) {
      medModalImage.src = "";
      medModalImage.classList.add("hidden");
      medModalImage.style.display = "none";
      medModalImage.style.opacity = "0";
    }
    if (medModalVisualSkeleton) {
      medModalVisualSkeleton.style.display = "flex";
      medModalVisualSkeleton.style.opacity = "1";
    }

    const fallbackUrl = med.eda_id ? `/api/drugs/${encodeURIComponent(med.eda_id)}/image` : `/api/drugs/image-by-name?name=${encodeURIComponent(displayName)}`;
    const targetSrc = med.image_url || fallbackUrl;

    if (medModalImage) {
      medModalImage.onload = function() {
        if (medModalVisualSkeleton) medModalVisualSkeleton.style.display = "none";
        medModalImage.classList.remove("hidden");
        medModalImage.style.display = "block";
        medModalImage.style.opacity = "1";
      };
      medModalImage.onerror = function() {
        this.onerror = null;
        this.src = fallbackUrl;
      };
      medModalImage.src = targetSrc;
    }

    // Active ingredient
    const activeSub = (med.active_ingredient && med.active_ingredient.trim()) || (topCandidate ? topCandidate.generic_name : null) || "مادة فعالة صيدلانية مسجلة";
    medModalActive.textContent = activeSub;

    // Strength & Form
    const strVal = (med.strength && med.strength.trim()) || (topCandidate ? topCandidate.strength : null) || "تركيز قياسي";
    const formVal = (topCandidate && topCandidate.dosage_form) ? topCandidate.dosage_form : "مستحضر علاجي";
    medModalStrengthForm.textContent = `${strVal} • ${formVal}`;

    // Category
    medModalCategory.textContent = details.category_ar || details.category_en || "مستحضر صيدلاني مصري";

    // Pricing
    medModalPrice.textContent = (details.price_egp != null)
      ? `${details.price_egp.toFixed(2)} EGP (تسعيرة رسمية)`
      : "خاضع للتسعيرة الجبرية الرسمية بهيئة الدواء";

    // Clinical Details: What It Treats & Physician Diagnosis
    const detailedUses = details.what_it_treats_detailed || details.what_it_treats || med.clinical_indication;
    medModalWhatItTreats.textContent = detailedUses || "علاج ومكافحة الحالة المرضية المقررة بالروشتة الطبية.";
    medModalLikelyDiagnosis.textContent = details.likely_diagnosis || med.likely_diagnosis || "حالة مرضية مشخصة ومحددة من قبل الطبيب المعالج.";
    medModalMechanism.textContent = details.mechanism || "يعمل وفقاً للخصائص الحيوية للمادة الفعالة المسجلة بهيئة الدواء المصرية.";
    medModalPrecautions.textContent = details.precautions || "يؤخذ بالجرعة المقررة في الروشتة وتحت إشراف الطبيب أو الصيدلي.";

    // Altibbi Medical Reference Link
    if (medModalAltibbiLink) {
      if (details.source_url) {
        medModalAltibbiLink.href = details.source_url;
        medModalAltibbiLink.style.display = "inline-flex";
        medModalAltibbiLink.textContent = `🌐 عرض صفحة ${displayName} على موسوعة الطبي ↗`;
      } else {
        medModalAltibbiLink.href = `https://altibbi.com/search?q=${encodeURIComponent(displayName)}`;
        medModalAltibbiLink.style.display = "inline-flex";
        medModalAltibbiLink.textContent = "🌐 البحث عن المستحضر في موسوعة الطبي ↗";
      }
    }

    // Prescription Specific Regimen
    medModalDose.textContent = (med.dose && med.dose.trim()) || "حسب إرشادات الطبيب";
    medModalFrequency.textContent = (med.frequency && med.frequency.trim()) || "حسب المواعيد المقررة";
    medModalDuration.textContent = (med.duration && med.duration.trim()) || "طوال فترة الكورس العلاجي";
    medModalInstructions.textContent = (med.instructions && med.instructions.trim()) || "لا توجد تعليمات كتابية إضافية.";
    medModalRawText.textContent = (med.raw_text && med.raw_text.trim()) || "غير محدد";

    // Alternatives Button
    medModalExploreAlternativesBtn.href = `/drugs?q=${encodeURIComponent(displayName)}`;

    // If eda_id exists, asynchronously fetch live market pricing
    if (med.eda_id) {
      fetch(`/api/drugs/${encodeURIComponent(med.eda_id)}`)
        .then(r => r.ok ? r.json() : null)
        .then(drugData => {
          if (drugData && drugData.pricing) {
            const p = drugData.pricing;
            if (p.expected_price_current_egp != null) {
              medModalPrice.textContent = `${p.expected_price_current_egp.toFixed(2)} EGP (سعر الصيدليات حالياً)`;
              if (p.expected_strip_price_egp != null) {
                medModalPrice.textContent += ` • سعر الشريط: ${p.expected_strip_price_egp.toFixed(2)} EGP`;
              }
            }
          }
        })
        .catch(() => {});
    }

    // Display modal
    medicationDetailModal.classList.remove("hidden");
    document.body.style.overflow = "hidden";
  }

  // 6.2 Open Candidate Drug Detail Modal (عند النقر على أي صنف من أصناف هيئة الدواء المطابقة)
  async function openCandidateModal(drugId, prescriptionIdx) {
    if (!drugId) return;

    // Retrieve parent prescription regimen context if available
    const med = (window.currentPrescriptionMeds && prescriptionIdx != null) ? window.currentPrescriptionMeds[prescriptionIdx] : null;

    // Display modal in loading state
    medicationDetailModal.classList.remove("hidden");
    document.body.style.overflow = "hidden";

    medModalCommercialEn.textContent = "Loading candidate medicine...";
    medModalCommercialAr.textContent = "جاري جلب تفاصيل الصنف وصورة العلاج...";
    medModalEdaId.textContent = drugId;
    medModalStatusBadge.textContent = "صنف مطابق بهيئة الدواء";
    medModalDosageBadge.textContent = "جاري التحميل...";

    // 1. Immediately wipe previous image & activate shimmer skeleton
    if (medModalImage) {
      medModalImage.src = "";
      medModalImage.classList.add("hidden");
      medModalImage.style.display = "none";
      medModalImage.style.opacity = "0";
    }
    if (medModalVisualSkeleton) {
      medModalVisualSkeleton.style.display = "flex";
      medModalVisualSkeleton.style.opacity = "1";
    }

    // 2. Add shimmer loading to clinical detail cards
    if (medModalWhatItTreats) {
      medModalWhatItTreats.textContent = "جاري فحص دواعي الاستعمال من موسوعة الأدوية...";
      medModalWhatItTreats.classList.add("shimmer-active");
    }
    if (medModalLikelyDiagnosis) {
      medModalLikelyDiagnosis.textContent = "جاري تحليل التشخيص الطبي واستنتاج مشكلة المريض...";
      medModalLikelyDiagnosis.classList.add("shimmer-active");
    }
    if (medModalMechanism) {
      medModalMechanism.textContent = "جاري التحميل...";
      medModalMechanism.classList.add("shimmer-active");
    }
    if (medModalPrecautions) {
      medModalPrecautions.textContent = "جاري التحميل...";
      medModalPrecautions.classList.add("shimmer-active");
    }

    medModalActive.textContent = "--";
    medModalStrengthForm.textContent = "--";
    medModalCategory.textContent = "--";
    medModalPrice.textContent = "جاري استرجاع السعر الرسمي والنهائي...";

    // 3. Initiate image fetch with onload/onerror handlers
    if (medModalImage) {
      const fallbackUrl = `/api/drugs/${encodeURIComponent(drugId)}/image?type=svg`;
      medModalImage.onload = function() {
        if (medModalVisualSkeleton) medModalVisualSkeleton.style.display = "none";
        medModalImage.classList.remove("hidden");
        medModalImage.style.display = "block";
        medModalImage.style.opacity = "1";
      };
      medModalImage.onerror = function() {
        this.onerror = null;
        this.src = fallbackUrl;
      };
      medModalImage.src = `/api/drugs/${encodeURIComponent(drugId)}/image`;
    }

    if (med) {
      medModalDose.textContent = (med.dose && med.dose.trim()) || "حسب إرشادات الطبيب";
      medModalFrequency.textContent = (med.frequency && med.frequency.trim()) || "حسب المواعيد المقررة";
      medModalDuration.textContent = (med.duration && med.duration.trim()) || "طوال فترة الكورس العلاجي";
      medModalInstructions.textContent = (med.instructions && med.instructions.trim()) || "لا توجد تعليمات كتابية إضافية.";
      medModalRawText.textContent = (med.raw_text && med.raw_text.trim()) || "غير محدد";
    }

    try {
      const res = await fetch(`/api/drugs/${encodeURIComponent(drugId)}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      const drug = data.drug || {};
      const pricing = data.pricing || {};
      const details = drug.indication_details || {};

      medModalCommercialEn.textContent = drug.full_commercial_name_en || drug.commercial_name_en;
      medModalCommercialAr.textContent = drug.commercial_name_ar ? `(${drug.commercial_name_ar})` : "";
      medModalEdaId.textContent = drug.id;
      medModalStatusBadge.textContent = "صنف معتمد بهيئة الدواء ✓";
      medModalDosageBadge.textContent = drug.dosage_form || "مستحضر صيدلاني";

      // Active ingredient & specs
      medModalActive.textContent = drug.generic_name || "مادة فعالة صيدلانية مسجلة";
      medModalStrengthForm.textContent = `${drug.strength || "تركيز قياسي"} • ${drug.dosage_form || "مستحضر"}`;
      medModalCategory.textContent = drug.drug_class || "مستحضر صيدلاني مصري";

      // Pricing
      if (pricing.expected_price_current_egp != null) {
        medModalPrice.textContent = `${pricing.expected_price_current_egp.toFixed(2)} EGP (سعر الصيدليات حالياً)`;
        if (pricing.expected_strip_price_egp != null) {
          medModalPrice.textContent += ` • سعر الشريط: ${pricing.expected_strip_price_egp.toFixed(2)} EGP`;
        }
      } else if (drug.price_egp != null) {
        medModalPrice.textContent = `${drug.price_egp.toFixed(2)} EGP (تسعيرة رسمية)`;
      } else {
        medModalPrice.textContent = "خاضع للتسعيرة الرسمية بهيئة الدواء المصرية";
      }

      // Clinical details (remove shimmer)
      if (medModalWhatItTreats) {
        medModalWhatItTreats.classList.remove("shimmer-active");
        const richUses = details.what_it_treats_detailed || details.what_it_treats || drug.clinical_indication;
        medModalWhatItTreats.textContent = richUses || "علاج ومكافحة الحالة المرضية المقررة بالروشتة الطبية.";
      }
      if (medModalLikelyDiagnosis) {
        medModalLikelyDiagnosis.classList.remove("shimmer-active");
        medModalLikelyDiagnosis.textContent = details.likely_diagnosis || drug.likely_diagnosis || "حالة مرضية مشخصة ومحددة من قبل الطبيب المعالج.";
      }
      if (medModalMechanism) {
        medModalMechanism.classList.remove("shimmer-active");
        medModalMechanism.textContent = details.mechanism || "يعمل وفقاً للخصائص الحيوية للمادة الفعالة المسجلة بهيئة الدواء المصرية.";
      }
      if (medModalPrecautions) {
        medModalPrecautions.classList.remove("shimmer-active");
        medModalPrecautions.textContent = details.precautions || "يؤخذ بالجرعة المقررة في الروشتة وتحت إشراف الطبيب أو الصيدلي.";
      }

      // Altibbi Medical Reference Link
      if (medModalAltibbiLink) {
        if (details.source_url) {
          medModalAltibbiLink.href = details.source_url;
          medModalAltibbiLink.style.display = "inline-flex";
          medModalAltibbiLink.textContent = `🌐 عرض صفحة ${drug.commercial_name_ar || drug.commercial_name_en} على موسوعة الطبي ↗`;
        } else {
          medModalAltibbiLink.href = `https://altibbi.com/search?q=${encodeURIComponent(drug.commercial_name_ar || drug.commercial_name_en)}`;
          medModalAltibbiLink.style.display = "inline-flex";
          medModalAltibbiLink.textContent = "🌐 البحث عن المستحضر في موسوعة الطبي ↗";
        }
      }

      // Alternatives explore link
      medModalExploreAlternativesBtn.href = `/drugs?q=${encodeURIComponent(drug.commercial_name_en)}&id=${encodeURIComponent(drug.id)}`;
    } catch (err) {
      console.error("Failed to load candidate drug details:", err);
      medModalCommercialEn.textContent = "تعذر تحميل تفاصيل الصنف";
      medModalCommercialAr.textContent = "";
      if (medModalWhatItTreats) {
        medModalWhatItTreats.classList.remove("shimmer-active");
        medModalWhatItTreats.textContent = "يرجى التحقق من الاتصال بالخادم والمحاولة مرة أخرى.";
      }
      if (medModalLikelyDiagnosis) medModalLikelyDiagnosis.classList.remove("shimmer-active");
      if (medModalMechanism) medModalMechanism.classList.remove("shimmer-active");
      if (medModalPrecautions) medModalPrecautions.classList.remove("shimmer-active");
    }
  }

  function closeMedModal() {
    if (medicationDetailModal) {
      medicationDetailModal.classList.add("hidden");
      document.body.style.overflow = "";
      if (medModalImage) {
        medModalImage.src = "";
        medModalImage.classList.add("hidden");
        medModalImage.style.display = "none";
      }
      if (medModalVisualSkeleton) {
        medModalVisualSkeleton.style.display = "flex";
      }
    }
  }

  window.openMedicationModal = openMedicationModal;
  window.openCandidateModal = openCandidateModal;
  window.closeMedModal = closeMedModal;

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
  const tabBilingualBtn = document.getElementById("tabBilingualBtn");
  const labView = document.getElementById("labView");
  const benchmarkView = document.getElementById("benchmarkView");
  const bilingualView = document.getElementById("bilingualView");
  const phaseBadge = document.getElementById("phaseBadge");

  const runBenchmarkBtn = document.getElementById("runBenchmarkBtn");
  const benchSpinner = document.getElementById("benchSpinner");
  const benchBtnText = document.getElementById("benchBtnText");
  const benchmarkTableBody = document.getElementById("benchmarkTableBody");
  const benchmarkCasesGrid = document.getElementById("benchmarkCasesGrid");
  const benchCaseCount = document.getElementById("benchCaseCount");

  tabLabBtn.addEventListener("click", () => switchTab("lab"));
  tabBenchmarkBtn.addEventListener("click", () => switchTab("benchmark"));
  if (tabBilingualBtn) tabBilingualBtn.addEventListener("click", () => switchTab("bilingual"));

  function switchTab(tab) {
    tabLabBtn.classList.remove("active");
    tabBenchmarkBtn.classList.remove("active");
    if (tabBilingualBtn) tabBilingualBtn.classList.remove("active");
    labView.classList.add("hidden");
    benchmarkView.classList.add("hidden");
    if (bilingualView) bilingualView.classList.add("hidden");

    if (tab === "lab") {
      tabLabBtn.classList.add("active");
      labView.classList.remove("hidden");
      phaseBadge.textContent = "PHASE 1 & 5 — RESEARCH LAB & EDA DATABASE";
    } else if (tab === "benchmark") {
      tabBenchmarkBtn.classList.add("active");
      benchmarkView.classList.remove("hidden");
      phaseBadge.textContent = "PHASE 2 — MULTI-MODEL BENCHMARK";
      loadBenchmarkCases();
      loadBenchmarkResults();
    } else if (tab === "bilingual") {
      if (tabBilingualBtn) tabBilingualBtn.classList.add("active");
      if (bilingualView) bilingualView.classList.remove("hidden");
      phaseBadge.textContent = "PHASE 3 & 5 — BILINGUAL ARABIC DATASET & TRAINING";
      loadBilingualStats();
      loadBilingualCases();
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
      benchmarkTableBody.innerHTML = `<tr><td colspan="10" class="text-center">No benchmark runs recorded. Click "Run Multi-Model Benchmark".</td></tr>`;
      return;
    }

    // Sort by critical error rate ascending (safest first), then by F1 score descending
    const sorted = Object.entries(summaries).sort((a, b) => {
      const critDiff = a[1].critical_medication_error_rate - b[1].critical_medication_error_rate;
      if (critDiff !== 0) return critDiff;
      const f1Diff = (b[1].f1_score || 0) - (a[1].f1_score || 0);
      if (f1Diff !== 0) return f1Diff;
      return (b[1].accuracy || b[1].medication_name_accuracy) - (a[1].accuracy || a[1].medication_name_accuracy);
    });

    sorted.forEach(([modelKey, s]) => {
      const row = document.createElement("tr");

      const accVal = Math.round((s.accuracy !== undefined ? s.accuracy : s.medication_name_accuracy) * 100);
      const precVal = Math.round((s.precision || s.medication_name_accuracy || 0) * 100);
      const recVal = Math.round((s.recall || s.medication_name_accuracy || 0) * 100);
      const f1Val = Math.round((s.f1_score || s.medication_name_accuracy || 0) * 100);
      const cerVal = Math.round((s.cer || 0) * 100);
      const werVal = Math.round((s.wer || 0) * 100);
      const exactVal = Math.round((s.exact_match_rate || 0) * 100);

      const critErr = Math.round(s.critical_medication_error_rate * 100);
      const critBadge = critErr === 0 
        ? `<span class="critical-safe">0.0% (Safe)</span>` 
        : `<span class="critical-danger">⚠️ ${critErr}% (Critical)</span>`;

      const lat = `${Math.round(s.mean_latency_ms)} ms`;

      row.innerHTML = `
        <td><strong>${escapeHtml(modelKey)}</strong></td>
        <td><span class="badge ${accVal >= 75 ? 'badge-success' : 'badge-neutral'}">${accVal}%</span></td>
        <td>${precVal}%</td>
        <td>${recVal}%</td>
        <td><strong><span style="color: var(--primary); font-weight: 700;">${f1Val}%</span></strong></td>
        <td><span style="color: ${cerVal > 25 ? 'var(--danger-solid, #ef4444)' : 'inherit'};">${cerVal}%</span></td>
        <td><span style="color: ${werVal > 30 ? 'var(--danger-solid, #ef4444)' : 'inherit'};">${werVal}%</span></td>
        <td>${exactVal}%</td>
        <td>${critBadge}</td>
        <td><span style="color: var(--text-muted); font-size: 0.85rem;">${lat}</span></td>
      `;
      benchmarkTableBody.appendChild(row);
    });
  }

  // =========================================================================
  // Phase 3 & 5: Bilingual Arabic Dataset & Model Training Studio
  // =========================================================================
  const bilingualStatCases = document.getElementById("bilingualStatCases");
  const bilingualStatMeds = document.getElementById("bilingualStatMeds");
  const bilingualStatArabic = document.getElementById("bilingualStatArabic");
  const bilingualStatEda = document.getElementById("bilingualStatEda");

  const exportTrainingBtn = document.getElementById("exportTrainingBtn");
  const exportSpinner = document.getElementById("exportSpinner");
  const exportBtnText = document.getElementById("exportBtnText");
  const exportStatusAlert = document.getElementById("exportStatusAlert");

  const bilingualEvalProvider = document.getElementById("bilingualEvalProvider");
  const bilingualEvalPipeline = document.getElementById("bilingualEvalPipeline");
  const runBilingualEvalBtn = document.getElementById("runBilingualEvalBtn");
  const bilingualEvalSpinner = document.getElementById("bilingualEvalSpinner");
  const bilingualEvalBtnText = document.getElementById("bilingualEvalBtnText");
  const bilingualEvalResults = document.getElementById("bilingualEvalResults");

  const bilingualDisplayCount = document.getElementById("bilingualDisplayCount");
  const bilingualSearchInput = document.getElementById("bilingualSearchInput");
  const bilingualCasesGrid = document.getElementById("bilingualCasesGrid");

  let currentBilingualSplit = "all";
  let bilingualSearchQuery = "";
  let bilingualSearchTimer = null;

  async function loadBilingualStats() {
    try {
      const res = await fetch("/api/bilingual/stats");
      if (!res.ok) return;
      const data = await res.json();
      if (bilingualStatCases) bilingualStatCases.textContent = (data.total_verified_cases || 0).toLocaleString();
      if (bilingualStatMeds) bilingualStatMeds.textContent = (data.total_medications || 0).toLocaleString();
      if (bilingualStatArabic) bilingualStatArabic.textContent = (data.medications_with_arabic_instructions || 0).toLocaleString();
      if (bilingualStatEda) bilingualStatEda.textContent = (data.total_registered_drugs_available || 0).toLocaleString();
    } catch (err) {
      console.warn("Failed to load bilingual stats:", err);
    }
  }

  async function loadBilingualCases() {
    if (!bilingualCasesGrid) return;
    try {
      bilingualCasesGrid.innerHTML = `
        <div style="grid-column: 1 / -1; padding: 2rem; text-align: center;">
          <div class="spinner" style="margin: 0 auto;"></div>
          <p style="margin-top:0.5rem; font-size:0.85rem; color:var(--text-muted);">Loading prescription cases...</p>
        </div>
      `;

      let url = `/api/bilingual/cases?limit=36`;
      if (currentBilingualSplit && currentBilingualSplit !== "all") {
        url += `&split=${encodeURIComponent(currentBilingualSplit)}`;
      }
      if (bilingualSearchQuery) {
        url += `&search=${encodeURIComponent(bilingualSearchQuery)}`;
      }

      const res = await fetch(url);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      const cases = data.cases || [];

      if (bilingualDisplayCount) {
        bilingualDisplayCount.textContent = data.total !== undefined ? data.total : cases.length;
      }
      bilingualCasesGrid.innerHTML = "";

      if (cases.length === 0) {
        bilingualCasesGrid.innerHTML = `
          <div style="grid-column: 1 / -1; padding: 3rem; text-align: center; color: var(--text-muted);">
            <p>No prescriptions match the selected filter or search term.</p>
          </div>
        `;
        return;
      }

      cases.forEach((c) => {
        const card = document.createElement("div");
        card.className = "case-card";

        const meds = c.medications || [];
        const medTagsHtml = meds.slice(0, 4).map((m) => {
          const name = escapeHtml(m.Medication_name || m.Medication_name_raw || "Prescription item");
          const dose = m.Dosage ? `<span style="color:var(--text-muted); font-size:0.7rem;">${escapeHtml(m.Dosage)}</span>` : "";
          const ar = m.Instructions ? `<span class="ar-instr">(${escapeHtml(m.Instructions)})</span>` : "";
          return `<span class="bilingual-med-tag"><strong>${name}</strong> ${dose} ${ar}</span>`;
        }).join("");

        const moreMedsCount = meds.length > 4 ? meds.length - 4 : 0;
        const moreMedsHtml = moreMedsCount > 0 ? `<span class="bilingual-med-tag" style="background:var(--border);">+${moreMedsCount} more</span>` : "";

        card.innerHTML = `
          <div class="case-img-box">
            <img src="/api/bilingual/case/${c.case_id}/image" alt="${escapeHtml(c.filename)}" loading="lazy">
          </div>
          <div class="case-content">
            <div class="case-header-row">
              <span class="case-tag">${escapeHtml(c.case_id)}</span>
              <span class="case-med-count">${c.medication_count} meds</span>
            </div>
            <div style="font-size:0.75rem; color:var(--text-muted); margin-bottom: 0.35rem; font-family: monospace;">${escapeHtml(c.filename)}</div>
            <div style="margin-top: 0.25rem;">
              ${medTagsHtml}
              ${moreMedsHtml}
            </div>
            <div class="case-btn-row">
              <button type="button" class="btn btn-secondary btn-sm test-in-lab-btn" style="width: 100%; font-size: 0.775rem; padding: 0.35rem 0.5rem;">
                🧪 Test in Lab
              </button>
            </div>
          </div>
        `;

        const loadCaseIntoLab = () => {
          fetch(`/api/bilingual/case/${c.case_id}/image`)
            .then((res) => {
              if (!res.ok) throw new Error("Image not found");
              return res.blob();
            })
            .then((blob) => {
              const file = new File([blob], c.filename || `${c.case_id}.jpg`, { type: "image/jpeg" });
              switchTab("lab");
              handleFileSelection(file);
            })
            .catch((err) => {
              alert(`Could not load prescription image: ${err.message}`);
            });
        };

        const labBtn = card.querySelector(".test-in-lab-btn");
        if (labBtn) {
          labBtn.addEventListener("click", (e) => {
            e.stopPropagation();
            loadCaseIntoLab();
          });
        }

        const imgBox = card.querySelector(".case-img-box");
        if (imgBox) {
          imgBox.addEventListener("click", loadCaseIntoLab);
        }

        bilingualCasesGrid.appendChild(card);
      });
    } catch (err) {
      console.warn("Failed to load bilingual cases:", err);
      if (bilingualCasesGrid) {
        bilingualCasesGrid.innerHTML = `
          <div style="grid-column: 1 / -1; padding: 2rem; text-align: center; color: var(--danger);">
            Failed to load dataset cases: ${escapeHtml(err.message)}
          </div>
        `;
      }
    }
  }

  // Split Filter Pills
  document.querySelectorAll(".split-pill").forEach((pill) => {
    pill.addEventListener("click", () => {
      document.querySelectorAll(".split-pill").forEach((p) => p.classList.remove("active"));
      pill.classList.add("active");
      currentBilingualSplit = pill.dataset.split || "all";
      loadBilingualCases();
    });
  });

  // Search input debounce
  if (bilingualSearchInput) {
    bilingualSearchInput.addEventListener("input", (e) => {
      clearTimeout(bilingualSearchTimer);
      bilingualSearchTimer = setTimeout(() => {
        bilingualSearchQuery = e.target.value.trim();
        loadBilingualCases();
      }, 300);
    });
  }

  // Export Fine-Tuning Training Datasets
  if (exportTrainingBtn) {
    exportTrainingBtn.addEventListener("click", async () => {
      exportSpinner.classList.remove("hidden");
      exportBtnText.textContent = "Exporting Datasets...";
      exportTrainingBtn.disabled = true;
      if (exportStatusAlert) exportStatusAlert.classList.add("hidden");

      try {
        const res = await fetch("/api/bilingual/export-training", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ max_cases: null }),
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const data = await res.json();
        const m = data.manifest || {};

        if (exportStatusAlert) {
          exportStatusAlert.className = "alert alert-success";
          exportStatusAlert.innerHTML = `
            <strong>✅ Training Export Ready!</strong><br>
            • Train Split: <strong>${m.train_cases_count || 0}</strong> cases<br>
            • Val Split: <strong>${m.val_cases_count || 0}</strong> cases<br>
            • Formats: <code>openai_vision_train.jsonl</code> &amp; <code>vlm_lora_train.json</code><br>
            Saved to <code>dataset/training_exports/</code>
          `;
          exportStatusAlert.classList.remove("hidden");
        }
      } catch (err) {
        if (exportStatusAlert) {
          exportStatusAlert.className = "alert alert-danger";
          exportStatusAlert.textContent = `Export failed: ${err.message}`;
          exportStatusAlert.classList.remove("hidden");
        }
      } finally {
        exportSpinner.classList.add("hidden");
        exportBtnText.textContent = "📦 Export JSONL Training Sets";
        exportTrainingBtn.disabled = false;
      }
    });
  }

  // Evaluate on Test Split
  if (runBilingualEvalBtn) {
    runBilingualEvalBtn.addEventListener("click", async () => {
      const provider = bilingualEvalProvider ? bilingualEvalProvider.value : "mock";
      const pipeline = bilingualEvalPipeline ? bilingualEvalPipeline.value : "v2";

      bilingualEvalSpinner.classList.remove("hidden");
      bilingualEvalBtnText.textContent = "Evaluating...";
      runBilingualEvalBtn.disabled = true;

      try {
        const res = await fetch("/api/bilingual/benchmark/run", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            provider: provider,
            pipeline_version: pipeline,
            max_cases: 15,
          }),
        });
        if (!res.ok) {
          const errData = await res.json().catch(() => ({}));
          throw new Error(errData.detail || `HTTP ${res.status}`);
        }
        const data = await res.json();
        const results = data.results || {};
        const m = results.metrics || {};

        if (bilingualEvalResults) {
          bilingualEvalResults.innerHTML = `
            <div style="font-weight: 700; color: var(--text-main); margin-bottom: 0.5rem; display: flex; justify-content: space-between; align-items: center;">
              <span>🎯 Test Split Evaluation: ${escapeHtml(results.model_key || provider)} (${results.evaluated_cases || 0} cases)</span>
              <span class="badge badge-success">Evaluated</span>
            </div>
            <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 0.5rem; margin-bottom: 0.5rem; background: var(--bg-card); padding: 0.75rem; border-radius: var(--radius-sm); border: 1px solid var(--border);">
              <div><span style="font-size: 0.75rem; color: var(--text-muted);">Accuracy:</span><br><strong>${((m.accuracy !== undefined ? m.accuracy : m.medication_name_accuracy) * 100).toFixed(1)}%</strong></div>
              <div><span style="font-size: 0.75rem; color: var(--text-muted);">Precision:</span><br><strong>${((m.precision || 0) * 100).toFixed(1)}%</strong></div>
              <div><span style="font-size: 0.75rem; color: var(--text-muted);">Recall:</span><br><strong>${((m.recall || 0) * 100).toFixed(1)}%</strong></div>
              <div><span style="font-size: 0.75rem; color: var(--text-muted);">F1 Score:</span><br><strong style="color: var(--primary);">${((m.f1_score || 0) * 100).toFixed(1)}%</strong></div>
              <div><span style="font-size: 0.75rem; color: var(--text-muted);">CER (OCR):</span><br><strong style="color: ${(m.cer || 0) > 0.25 ? 'var(--danger-solid, #ef4444)' : 'inherit'};">${((m.cer || 0) * 100).toFixed(1)}%</strong></div>
              <div><span style="font-size: 0.75rem; color: var(--text-muted);">WER:</span><br><strong style="color: ${(m.wer || 0) > 0.30 ? 'var(--danger-solid, #ef4444)' : 'inherit'};">${((m.wer || 0) * 100).toFixed(1)}%</strong></div>
              <div><span style="font-size: 0.75rem; color: var(--text-muted);">Exact Match:</span><br><strong>${((m.exact_match_rate || 0) * 100).toFixed(1)}%</strong></div>
              <div><span style="font-size: 0.75rem; color: var(--text-muted);">Arabic Instructions:</span><br><strong>${((m.arabic_instructions_accuracy || 0) * 100).toFixed(1)}%</strong></div>
              <div><span style="font-size: 0.75rem; color: var(--text-muted);">EDA Grounding:</span><br><strong>${((m.eda_database_grounding_rate || 0) * 100).toFixed(1)}%</strong></div>
              <div><span style="font-size: 0.75rem; color: var(--text-muted);">Avg Latency:</span><br><strong>${Math.round(m.mean_latency_ms || 0)} ms</strong></div>
            </div>
            <div style="color: var(--text-muted); font-size: 0.75rem;">
              Cost: $${(m.mean_cost_per_rx_usd || 0).toFixed(4)} / prescription • Evaluated strictly on the unseen test partition.
            </div>
          `;
          bilingualEvalResults.classList.remove("hidden");
        }
      } catch (err) {
        alert(`Bilingual test evaluation failed: ${err.message}`);
      } finally {
        bilingualEvalSpinner.classList.add("hidden");
        bilingualEvalBtnText.textContent = "▶ Run Test Split Eval";
        runBilingualEvalBtn.disabled = false;
      }
    });
  }
});
