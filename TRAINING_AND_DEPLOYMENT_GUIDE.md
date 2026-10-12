# 🏥 دليل تدريب ونشر موديل الذكاء الاصطناعي للروشتات المصرية (Gor3a Rx-Vision) 🇪🇬

هذا الدليل يوضح لك بالخطوات العملية والمباشرة كيفية تدريب موديل رؤية ولغة متخصص (**Qwen2.5-VL**) على خطوط الدكاترة والروشتات المصرية (عربي وإنجليزي)، ثم نشره للعامة على **Hugging Face** وتشغيله محلياً عبر **Ollama**.

---

## 1. الداتاسيت الموحدة (572 روشتة طبية حقيقية)
تم دمج جميع الروشتات الموجودة في المشروع في حزمة تدريبية واحدة موثقة:
1. **داتاسيت الدكتورة نور الزغبي الموثقة (561 روشتة):** تحتوي على خطوط يد حقيقية مع أسماء الأدوية والجرعات باللغة العربية (أقراص، شراب، فوار، نقط، إلخ).
2. **مجلد `new Prescriptions images` (14 روشتة):** روشتات أطفال، نزلات معوية، مضادات حيوية، وأدوية حديثة.
3. **مجلد `new Prescriptions images/my` (11 روشتة):** صور واتساب حقيقية لعيادات الأطفال والباطنة المصرية.

📦 **ملف التدريب الجاهز والمضغوط:**
`dataset/training_exports/gor3a_rx_dataset_572.zip` (حجمه **86.4 ميجابايت** فقط، يحتوي على جميع الصور وملفي `train.json` و `val.json`).

---

## 2. التدريب في خطوة واحدة على Google Colab (مجاناً)
نظراً لأن جهاز الماك يعمل بمعمارية Apple Silicon ولا يدعم تدريب 4-bit QLoRA عبر كروت نفيديا، فإن أسرع وأفضل وسيلة مجانية هي **Google Colab** (يستغرق التدريب حوالي 25 إلى 35 دقيقة فقط):

1. افتح [Google Colab](https://colab.research.google.com/).
2. اضغط على **Upload Notebook** وارفع الملف التالي من مشروعك:
   `notebooks/train_egyptian_prescription_qwen2_5_vl.ipynb`
3. من القائمة العلوية في Colab: اختر **Runtime** ➡️ **Change runtime type** ➡️ اختر **T4 GPU** (مجاني تماماً).
4. اضغط على أيقونة المجلد (الملفات) في القائمة اليسرى في Colab، ثم اسحب وارفع الملف:
   `dataset/training_exports/gor3a_rx_dataset_572.zip`
5. اضغط **Runtime** ➡️ **Run all** (تشغيل جميع الخلايا).
6. سيقوم الدفتر بفك الضغط، تحميل الموديل الأساسي `Qwen/Qwen2.5-VL-3B-Instruct`، وتدريبه على الـ 572 روشتة، ثم حفظ الـ LoRA Adapter في ملف `gor3a_egyptian_rx_lora.zip`.

---

## 3. نشر الموديل للعامة على Hugging Face 🌐
ليتمكن أي شخص في العالم من استخدام الموديل، يمكنك رفعه إلى Hugging Face Model Hub:

1. أنشئ حساباً مجانياً على [Hugging Face](https://huggingface.co/).
2. ادخل إلى **Settings ➡️ Access Tokens** وأنشئ توكن جديد بصلاحية **Write**.
3. في خلية النشر داخل النوت بوك:
```python
from huggingface_hub import login

login("YOUR_HF_WRITE_TOKEN")

# اسم الحساب والريبو الخاص بك:
HF_REPO = "YourUsername/Gor3a-Qwen2.5-VL-Egyptian-Rx"

model.push_to_hub(HF_REPO)
processor.push_to_hub(HF_REPO)
print(f"تم النشر بنجاح على: https://huggingface.co/{HF_REPO}")
```

---

## 4. تشغيل الموديل محلياً على جهازك في Ollama
بعد اكتمال التدريب وتنزيل ملف `gor3a_egyptian_rx_lora.zip`:

1. فك ضغط الملف داخل مجلد `models/` في مشروع Gor3a:
```bash
unzip gor3a_egyptian_rx_lora.zip -d models/
```

2. أنشئ ملف `Modelfile` بالأمر:
```dockerfile
FROM qwen2.5vl:3b
ADAPTER ./models/gor3a_egyptian_rx_lora

SYSTEM """You are Gor3a Rx-Vision AI, an expert clinical AI specialized in transcribing and verifying handwritten Egyptian medical prescriptions.
Extract medications, dosage, frequency, and duration with strict zero-hallucination fidelity."""

PARAMETER temperature 0.1
PARAMETER top_p 0.8
PARAMETER num_ctx 4096
```

3. أنشئ الموديل في Ollama:
```bash
ollama create gor3a-rx -f Modelfile
```

4. شغله فوراً في واجهة Gor3a Lab أو في التيرمينال:
```bash
ollama run gor3a-rx
```

---

## 5. اختبار الروشتة في واجهة Prescription Lab
عند فتح واجهة Gor3a (`http://localhost:3000` أو صفحة `Prescription AI Lab`):
- اختر **Provider:** `Local Open-Source VLM (Ollama / vLLM / Qwen2.5-VL)`
- اختر **Model Name:** `gor3a-rx`
- اختر **Pipeline:** `Pipeline V2: Vision + Egyptian Drug Database`
- ارفع أي روشتة مكتوبة بخط يد الطبيب ليقوم الموديل بقراءتها ومطابقتها فوراً مع قاعدة بيانات هيئة الدواء المصرية (EDA).
