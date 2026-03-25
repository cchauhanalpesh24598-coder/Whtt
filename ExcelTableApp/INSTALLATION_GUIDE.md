# AIDE Me Sahi Tarike Se Install Karne Ka Guide

## Problem: "App not installed as package appears to be invalid"

Ye error AIDE me apk signing ki wajah se aata hai. Niche diye gaye steps follow karo:

## Solution 1: AIDE Me Directly Run Karo (Recommended ✅)

### Step 1: Project Setup
1. Mobile me **ExcelTableApp** folder ko extract karo
2. AIDE app kholo
3. **"Open Project"** ya **folder icon** click karo
4. ExcelTableApp folder select karo

### Step 2: Files Check Karo
AIDE me ye files dikhni chahiye:
```
ExcelTableApp/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/exceltable/MainActivity.java
│   │   ├── res/layout/activity_main.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
└── settings.gradle
```

### Step 3: Build Settings
1. AIDE me **Menu (3 dots)** → **More** → **Build settings**
2. Check karo:
   - ✅ "Use Gradle" selected ho
   - ✅ "Java version: 1.7" selected ho

### Step 4: Build & Run
1. **Build** button (hammer icon) dabao
2. Build complete hone tak wait karo (2-3 minutes lag sakte hain first time)
3. Build successful hone par **Run** button (play icon) dabao
4. App directly device me run hoga (install nahi, direct run!)

---

## Solution 2: APK Manually Sign Karo (Advanced)

Agar aapko APK file chahiye install karne ke liye:

### AIDE Me APK Build Steps:
1. Project open karo
2. Menu → **More** → **Project**
3. **"Build APK"** select karo
4. APK build hoga: `ExcelTableApp/app/build/outputs/apk/debug/app-debug.apk`

### APK Sign Karo:
AIDE me built-in signing honi chahiye, but agar nahi hai to:

1. **ZipSigner** app download karo (Play Store se - free hai)
2. ZipSigner kholo
3. Input file: `app-debug.apk` select karo
4. Output file name: `app-signed.apk` rakho
5. Key mode: **"Auto-testkey"** select karo
6. **"Sign the file"** button dabao
7. Ab `app-signed.apk` install karo

---

## Solution 3: Sabse Aasan Tarika (Best for AIDE!)

AIDE me apko APK install karne ki zaroorat nahi hai!

### Direct Run Process:
1. AIDE me project open karo
2. Build → Run
3. App directly test mode me run hoga
4. Har baar app use karne ke liye AIDE se run karo

**Note:** Development ke liye ye best approach hai!

---

## Common AIDE Issues & Fixes

### Issue 1: "Gradle build failed"
**Fix:**
- AIDE ko close karo aur phir se kholo
- Menu → More → "Refresh" select karo
- Phir se build karo

### Issue 2: "SDK not found"
**Fix:**
- AIDE automatically SDK download karega
- Internet on rakho
- First time build me 5-10 minutes lag sakte hain

### Issue 3: "Out of memory"
**Fix:**
- Mobile me kam se kam 2GB free space hona chahiye
- Dusre apps close kar do
- Mobile restart karo

### Issue 4: Build bahut slow hai
**Fix:**
- First time build slow hota hai (SDK download ki wajah se)
- Next time se fast build hoga
- WiFi use karo (mobile data slow hai)

---

## AIDE Me Testing

### App Test Karne Ke Liye:
1. Build & Run karo
2. App open hoga
3. Koi bhi row select karo
4. Text boxes me data paste karo:

**Text Box 1:**
```
B.v(Fedbank)
Applicant:Ramesh Kumar Sharma
```

**Text Box 2:**
```
22.1704N71.6677E
```

5. "Parse & Fill" button dabao
6. Row me data auto-fill ho jayega!
7. Row click karo to clipboard me copy hoga

---

## Best Practice for AIDE Development:

✅ **Direct Run** karo (install mat karo)
✅ **Gradle build** use karo
✅ **Test mode** me app use karo
✅ **Release APK** baad me banao jab sab kaam test ho jaye

---

## Final Release APK Banane Ke Liye:

Jab app completely ready ho jaye tab:

1. AIDE me project open karo
2. Menu → More → Build → **"Build Release APK"**
3. Release APK sign karo (ZipSigner se)
4. Wo APK share kar sakte ho

---

## Quick Troubleshoot Checklist:

- [ ] AIDE latest version hai?
- [ ] Mobile me 2GB+ free space hai?
- [ ] Internet connection stable hai?
- [ ] Project folder sahi jagah extract hui hai?
- [ ] AIDE ko storage permission diya hai?
- [ ] Gradle build selected hai settings me?

Agar ab bhi problem ho to specific error message batao! 😊
