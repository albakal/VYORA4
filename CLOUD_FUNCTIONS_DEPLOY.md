# دليل تفعيل قاعدة بيانات Supabase المحاسبية ونشر Cloud Functions وتصدير APK

تم ربط تطبيق **VYORA** بقاعدة بيانات **Supabase (PostgreSQL)** لإدارة السجل المحاسبي للمحافظ والنقاط والقيود المالية وسجل الفيديوهات، مع إبقاء المصادقة عبر **Google Sign-In / Firebase Auth** والتفاعل اللحظي عبر **Firestore**.

---

## 1. إعداد جداول Supabase (SQL & RLS)

1. افتح لوحة تحكم مشروعك في **Supabase** -> **SQL Editor** -> **New Query**.
2. انسخ محتوى الملف `/supabase/schema.sql` بالكامل وقم بتشغيله (**Run**).
   - يقوم السكربت بإنشاء الجداول المحاسبية: `vyora_users_sync`, `vyora_videos_ledger`, `vyora_wallets`, `vyora_wallet_transactions`, `vyora_withdrawal_requests`.
   - يفعل سياسات الحماية (**Row-Level Security**) والدوال الذرية `record_vyora_view_reward` و `grant_vyora_points_ledger` و `review_vyora_withdrawal`.

---

## 2. إعداد المفاتيح بأمان (بدون وضع الأسرار داخل APK)

### أ) في لوحة Secrets داخل AI Studio (للعميل / التطبيق):
أضف المتغيرات التالية في **Secrets Panel** بـ AI Studio:
- `SUPABASE_URL`: رابط مشروعك (مثال: `https://<project-ref>.supabase.co`).
- `SUPABASE_PUBLISHABLE_KEY`: المفتاح العام لـ Supabase (المخصص للقراءة وفق سياسات RLS).

> ⚠️ **تنبيه أمني هام**: لا تضع أبداً المفتاح السري (`SUPABASE_SECRET_KEY`) داخل `.env` أو `.env.example` أو داخل كود تطبيق الأندرويد.

### ب) في Firebase Secret Manager (للخادم فقط):
نفّذ الأوامر التالية عبر **Firebase CLI** (`npm install -g firebase-tools`) لحفظ مفاتيح Agora و Supabase في الخادم فقط:

```bash
firebase login
firebase use com-aistudio-tokshort-ap-f5c5c

# مفاتيح البث المباشر Agora
firebase functions:secrets:set AGORA_APP_ID
firebase functions:secrets:set AGORA_APP_CERTIFICATE

# مفاتيح السجل المحاسبي Supabase (تعمل على الخادم فقط بصلاحيات كاملة آمنة)
firebase functions:secrets:set SUPABASE_URL
firebase functions:secrets:set SUPABASE_SECRET_KEY
```

---

## 3. بناء ونشر Cloud Functions

```bash
npm --prefix functions install
npm --prefix functions run build
firebase deploy --only functions
```

### الدوال السحابية المنشورة:
- `getAgoraLiveToken`: إصدار رمز بث Agora مؤقت وموقع من الخادم.
- `syncUserAndVideoToSupabase`: مزامنة بيانات المنشئ ومحفظته والفيديوهات الجديدة مع Supabase.
- `recordVideoViewReward`: احتساب المشاهدة وإضافة المكافآت في Firestore و Supabase ذرياً.
- `requestCreatorWithdrawal`: التحقق من الرصيد والحد الأدنى للسحب وتسجيل الطلب في القاعدتين.
- `adminGrantCreatorPoints` / `adminReviewWithdrawal`: تنفيذ قرارات المدير المحاسبية في Firestore و Supabase معاً.
- `getWalletAccountingSummary`: جلب ملخص الرصيد وعدد القيود من Supabase لشاشة المحفظة.
- `sendActivityPushNotification`: إرسال إشعارات الدفع (FCM).

---

## 4. تنزيل وتثبيت حزمة VYORA Debug APK

تم تجميع حزمة التطبيق محلياً داخل مخرجات المشروع في المسار:
```
app/build/outputs/apk/debug/app-debug.apk
```

يمكنك تصدير وتنزيل ملف الـ APK مباشرة من قائمة **Settings / Export / Generate APK** داخل واجهة **Google AI Studio**، أو تثبيته عبر جهاز متصل بـ USB:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 5. الرفع على GitHub والحصول على رابط ويب مباشر لتحميل الـ APK

تم تجهيز ملف البناء التلقائي وملفات الإعدادات والتوقيع (`app/google-services.json` و`debug.keystore.base64`) داخل المشروع:
```
.github/workflows/build-apk.yml
```
1. ارفع المشروع إلى مستودعك `https://github.com/albakal/VYORA4` عبر زر **Push to GitHub** في قائمة الإعدادات بـ Google AI Studio.
2. سيعمل **GitHub Actions** تلقائياً على قبول تراخيص Android SDK وفك تشفير شهادة التوقيع وبناء `VYORA.apk` ثم نشره في **GitHub Releases** و**Artifacts**.
3. رابط التحميل المباشر على الويب بعد اكتمال البناء سيكون:
   `https://github.com/albakal/VYORA4/releases/latest/download/VYORA.apk`

> **ملاحظة**: يدعم مسار البناء أيضاً إضافة `GOOGLE_SERVICES_JSON_BASE64` و`DEBUG_KEYSTORE_BASE64` (أو `KEYSTORE_BASE64` و`STORE_PASSWORD` و`KEY_PASSWORD` لتوقيع Release) في **GitHub Secrets** كخيار احتياطي.

