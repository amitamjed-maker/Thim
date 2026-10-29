# Glass Launcher

iPhone-স্টাইল (Liquid Glass) লঞ্চার: হোম স্ক্রিন + অ্যাপ লাইব্রেরি + লক স্ক্রিন + Dynamic Island।

## APK বানানো (Termux)
```
cd GlassLauncher
git init && git add . && git commit -m "init"
git branch -M main
git remote add origin https://github.com/USERNAME/REPO.git
git push -u origin main
```
GitHub → Actions → "Build APK" → শেষে Artifacts থেকে `GlassLauncher-APK` নামাও।
