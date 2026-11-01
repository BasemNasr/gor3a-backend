# دليل حل مشكلة رفض التطبيق على Google Play Console
## تطبيق: جرعة - قراءة الروشتات والأدوية (`com.bn.gor3a`)

---

### 🔍 تحليل سبب الرفض بالتفصيل (Root Cause Analysis)

جاء في رسالة الرفض من Google Play:
> **Issue details:** We found an issue in the following area(s): **Full description**  
> **Issue found:** Violation of Misleading Claims policy  
> **Missing Source Link for Government Information:** Your app provides government information but lacks one or more clear and accessible URL/link(s) to the original source(s) (for example, .gov domains).

#### لماذا حدث ذلك؟
في **الوصف الكامل (Full Description)** للتطبيق على Google Play، ذُكرت عبارات تشير إلى:
- "هيئة الدواء المصرية" (Egyptian Drug Authority)
- "قاعدة بيانات رسمية" أو "تسعيرة رسمية / جبرية" للأدوية.

وفقاً لسياسة Google Play الصارمة الخاصة بـ **"المعلومات والخدمات الحكومية (Government Information and Services)"** التابعة لسياسة **"الادعاءات المضللة (Misleading Claims)"**:
إذا كان أي تطبيق يذكر بيانات أو تسعيرة أو جهات حكومية، فيجب **إلزامياً** توفير أمرين واضحين في الوصف:
1. **رابط رسمي وفعال لمصدر البيانات الحكومي** (نطاق حكومي مثل `https://www.edaegypt.gov.eg/`).
2. **إخلاء مسؤولية صريح وبارز (Disclaimer)** يوضح أن التطبيق مستقل ولا يمثل الجهة الحكومية وليس تابعاً لها بأي شكل.

---

### 🚀 خطوات الحل السريع (خلال 3 دقائق)

#### الخطوة 1: تعديل الوصف في Google Play Console
1. افتح حسابك في [Google Play Console](https://play.google.com/console/).
2. اختر التطبيق: **جرعة - قراءة الروشتات والأدوية** (`com.bn.gor3a`).
3. من القائمة الجانبية، اذهب إلى قسم **النمو (Grow)** ثم اضغط على **التواجد على المتجر (Store presence)** ➔ **بطاقة بيانات المتجر الرئيسية (Main store listing)**.
4. انتقل إلى حقل **الوصف الكامل (Full description)**:
   - احذف النص الحالي والصق النص المعتمد المكتوب في الملف:  
     [`google_play_submission/FULL_DESCRIPTION_AR.txt`](./FULL_DESCRIPTION_AR.txt)
   - إذا كنت قد أضفت لغة إنجليزية للتطبيق (English Translation)، افتح لغة الإنجليزية وضع النص الموجود في:  
     [`google_play_submission/FULL_DESCRIPTION_EN.txt`](./FULL_DESCRIPTION_EN.txt)
5. تأكد من **الوصف القصير (Short description)** (أقل من 80 حرفاً):
   - استخدم النص الموجود في [`google_play_submission/SHORT_DESCRIPTION_AR.txt`](./SHORT_DESCRIPTION_AR.txt):  
     `قراءة الروشتات بالذكاء الاصطناعي، دليل الأدوية والبدائل، وتنظيم مواعيد الجرعات`
6. اضغط على زر **حفظ (Save)** أسفل الصفحة.

---

#### الخطوة 2: التحقق من إقرار "التطبيقات الحكومية" (Government Apps)
1. من القائمة الجانبية، انزل إلى قسم **السياسة والبرامج (Policy and programs)** ➔ **محتوى التطبيق (App content)**.
2. ابحث عن قسم **تطبيقات الجهات الحكومية (Government apps)**:
3. تأكد من أنك اخترت:  
   **"لا، هذا التطبيق لا يمثل جهة حكومية أو يوفر خدمات حكومية"**  
   *(No, this app does not represent a government entity or facilitate government services)*.
4. اضغط **حفظ (Save)**.

---

#### الخطوة 3: إرسال التحديث للمراجعة (Send for Review)
1. من القائمة الجانبية في الأعلى، اضغط على **نظرة عامة على النشر (Publishing overview)**.
2. ستجد التغييرات معلقة (مثل تحديث بيانات المتجر الرئيسية).
3. اضغط على زر **إرسال التغييرات للمراجعة (Send changes for review)**.

---

### ✉️ هل تحتاج لتقديم استئناف (Appeal)؟
في معظم الحالات، **بمجرد تعديل الوصف الكامل في المتجر وإرسال التغييرات للمراجعة، لا تحتاج لتقديم استئناف** لأن المشكلة كانت محصورة في "Full description" فقط.  
ولكن، إذا كان زر الإرسال مقفلاً أو رغبت في الرد على فريق المراجعة فوراً:
1. اذهب إلى رابط الاستئناف المذكور في رسالتهم:  
   [Submit an appeal](https://play.google.com/console/appealsForm?ts=SR&aid=com.bn.gor3a)
2. انسخ نص الاستئناف الجاهز في ملف:  
   [`google_play_submission/APPEAL_LETTER.txt`](./APPEAL_LETTER.txt)
3. أرسل النموذج، وسيقوم فريق المراجعة بإعادة تفعيل وموافقة التطبيق خلال 24–48 ساعة.

---

### 🛡️ ملخص النصوص الجاهزة في المجلد:
- [`FULL_DESCRIPTION_AR.txt`](./FULL_DESCRIPTION_AR.txt): الوصف الكامل بالعربية متضمن إخلاء المسؤولية الحكومي والطبي ورابط هيئة الدواء الرسمي.
- [`FULL_DESCRIPTION_EN.txt`](./FULL_DESCRIPTION_EN.txt): الوصف الكامل باللغة الإنجليزية المتوافق رسمياً.
- [`SHORT_DESCRIPTION_AR.txt`](./SHORT_DESCRIPTION_AR.txt): الوصف القصير بالعربية (77 حرفاً).
- [`SHORT_DESCRIPTION_EN.txt`](./SHORT_DESCRIPTION_EN.txt): الوصف القصير بالإنجليزية (76 حرفاً).
- [`APPEAL_LETTER.txt`](./APPEAL_LETTER.txt): رسالة استئناف جاهزة واحترافية لفريق جوجل بلاي.
